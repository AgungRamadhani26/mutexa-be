package com.example.mutexa_be.service;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.repository.BankTransactionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service untuk mengekstrak nama pengirim/penerima (counterparty) dari deskripsi transaksi
 * menggunakan AI lokal Ollama (model llama3.2).
 *
 * STRATEGI KECEPATAN:
 * 1. Kumpulkan semua deskripsi UNIK dari batch transaksi (deduplikasi)
 * 2. Kirim dalam 1 prompt batch ke Ollama (bukan per-transaksi)
 * 3. Parse respons AI dan update counterparty_name secara bulk
 *
 * FALLBACK:
 * Jika Ollama tidak berjalan atau gagal, counterparty_name tetap menggunakan
 * hasil regex dari TransactionRefinementService.extractCounterpartyName()
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CounterpartyExtractionService {

   private final BankTransactionRepository bankTransactionRepository;
   private final ObjectMapper objectMapper;

   // URL Ollama lokal dan model yang digunakan
   private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
   private static final String MODEL_NAME = "llama3.2";

   /**
    * Mengekstrak counterparty name menggunakan AI untuk seluruh batch transaksi.
    * Dipanggil SETELAH transaksi sudah tersimpan di database.
    *
    * @param transactions List transaksi yang baru saja disimpan
    */
   public void extractAndUpdate(List<BankTransaction> transactions) {
      if (transactions == null || transactions.isEmpty()) {
         return;
      }

      try {
         // 1. Kumpulkan deskripsi UNIK saja (hemat token AI)
         // Key: normalized description, Value: list of transaction IDs yang memiliki deskripsi tersebut
         Map<String, List<Long>> descToIds = new LinkedHashMap<>();
         for (BankTransaction tx : transactions) {
            String desc = tx.getNormalizedDescription() != null
                  ? tx.getNormalizedDescription()
                  : tx.getRawDescription();
            if (desc == null || desc.trim().isEmpty()) continue;

            descToIds.computeIfAbsent(desc.trim(), k -> new ArrayList<>()).add(tx.getId());
         }

         if (descToIds.isEmpty()) return;

         log.info("Mengirim {} deskripsi unik ke Ollama ({}) untuk ekstraksi counterparty...",
               descToIds.size(), MODEL_NAME);

         // 2. Buat prompt batch: kirim semua deskripsi dalam 1 request
         List<String> uniqueDescs = new ArrayList<>(descToIds.keySet());
         String batchPrompt = buildBatchPrompt(uniqueDescs);

         // 3. Panggil Ollama
         String aiResponse = callOllama(batchPrompt);

         if (aiResponse == null || aiResponse.trim().isEmpty()) {
            log.warn("Ollama tidak memberikan respons. Counterparty tetap menggunakan hasil regex.");
            return;
         }

         // 4. Parse respons AI menjadi Map<nomor, nama>
         Map<Integer, String> extractedNames = parseAiResponse(aiResponse, uniqueDescs.size());

         // 5. Update counterparty_name di database secara bulk
         int updateCount = 0;
         for (int i = 0; i < uniqueDescs.size(); i++) {
            String extractedName = extractedNames.get(i + 1); // AI menjawab mulai dari nomor 1
            if (extractedName != null && !extractedName.isBlank() && !extractedName.equals("-")) {
               List<Long> txIds = descToIds.get(uniqueDescs.get(i));
               for (Long txId : txIds) {
                  bankTransactionRepository.findById(txId).ifPresent(tx -> {
                     tx.setCounterpartyName(extractedName.trim().toUpperCase());
                     bankTransactionRepository.save(tx);
                  });
               }
               updateCount += txIds.size();
            }
         }

         log.info("Berhasil meng-update counterparty_name untuk {} transaksi via AI Ollama.", updateCount);

      } catch (Exception e) {
         // TIDAK THROW EXCEPTION — Jika AI gagal, data transaksi tetap disimpan dengan counterparty regex
         log.warn("Gagal mengekstrak counterparty via Ollama (fallback ke regex): {}", e.getMessage());
      }
   }

   /**
    * Membangun prompt batch yang berisi daftar deskripsi bernomor.
    * Format respons yang diminta: daftar bernomor dengan hanya nama pengirim/penerima.
    */
   private String buildBatchPrompt(List<String> descriptions) {
      StringBuilder sb = new StringBuilder();
      sb.append("Kamu adalah alat ekstraksi data perbankan Indonesia. ");
      sb.append("Dari setiap keterangan transaksi bank di bawah, ekstrak HANYA nama pengirim atau penerima uang. ");
      sb.append("Hapus semua kode referensi, nomor rekening, tanggal, tipe transfer (SETORAN, PINBUK, MCM, CENAIDJA, dll). ");
      sb.append("Jawab HANYA dengan format daftar bernomor, satu nama per baris. ");
      sb.append("Jika tidak ada nama yang bisa diekstrak, tulis tanda strip (-).\n\n");

      for (int i = 0; i < descriptions.size(); i++) {
         sb.append((i + 1)).append(". ").append(descriptions.get(i)).append("\n");
      }

      sb.append("\nJawaban (HANYA nama, tanpa penjelasan):\n");
      return sb.toString();
   }

   /**
    * Memanggil Ollama REST API secara sinkron.
    */
   private String callOllama(String prompt) {
      try {
         Map<String, Object> requestBody = new HashMap<>();
         requestBody.put("model", MODEL_NAME);
         requestBody.put("prompt", prompt);
         requestBody.put("stream", false);

         String bodyJson = objectMapper.writeValueAsString(requestBody);

         HttpClient client = HttpClient.newHttpClient();
         HttpRequest request = HttpRequest.newBuilder()
               .uri(URI.create(OLLAMA_URL))
               .header("Content-Type", "application/json")
               .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
               .build();

         HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

         if (response.statusCode() == 200) {
            Map<String, Object> responseMap = objectMapper.readValue(
                  response.body(), new TypeReference<Map<String, Object>>() {});
            String aiText = (String) responseMap.get("response");
            log.info("Ollama berhasil merespons untuk batch counterparty extraction.");
            return aiText;
         } else {
            log.error("Ollama merespons dengan status {}: {}", response.statusCode(), response.body());
         }
      } catch (java.net.ConnectException e) {
         log.warn("Ollama tidak berjalan di localhost:11434. Counterparty fallback ke regex.");
      } catch (Exception e) {
         log.error("Gagal berkomunikasi dengan Ollama: {}", e.getMessage());
      }
      return null;
   }

   /**
    * Mem-parse respons AI yang berformat daftar bernomor.
    * Contoh input AI:
    *   1. AHSA JAYA METALINDO
    *   2. BUDI SANTOSO
    *   3. -
    */
   private Map<Integer, String> parseAiResponse(String aiResponse, int expectedCount) {
      Map<Integer, String> result = new HashMap<>();

      String[] lines = aiResponse.split("\\r?\\n");
      for (String line : lines) {
         line = line.trim();
         if (line.isEmpty()) continue;

         // Coba deteksi format "1. NAMA" atau "1) NAMA" atau "1: NAMA"
         java.util.regex.Matcher m = java.util.regex.Pattern
               .compile("^(\\d+)[.):\\-]\\s*(.+)$")
               .matcher(line);

         if (m.find()) {
            try {
               int number = Integer.parseInt(m.group(1));
               String name = m.group(2).trim();

               // Bersihkan tanda kutip atau tanda baca ekstra
               name = name.replaceAll("[\"'`]", "").trim();

               if (number >= 1 && number <= expectedCount) {
                  result.put(number, name);
               }
            } catch (NumberFormatException ignored) {}
         }
      }

      log.info("Berhasil mem-parse {} dari {} jawaban AI.", result.size(), expectedCount);
      return result;
   }
}
