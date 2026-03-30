package com.example.mutexa_be.service;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.enums.TransactionCategory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCategorizationService {

   @Value("${ai.gemini.api-key:}")
   private String geminiApiKey;

   @Value("${ai.ollama.url:http://localhost:11434/api/generate}")
   private String ollamaUrl;

   @Value("${ai.ollama.model:llama3.2}")
   private String ollamaModel;

   @Value("${ai.provider:gemini}")
   private String aiProvider; // gemini atau ollama

   private final ObjectMapper objectMapper;

   public void enrichUnclassified(List<BankTransaction> transactions) {
      // 1. Kumpulkan semua transaksi yang UNCLASSIFIED
      List<BankTransaction> unclassifiedTxs = transactions.stream()
            .filter(tx -> tx.getCategory() == TransactionCategory.UNCLASSIFIED)
            .collect(Collectors.toList());

      if (unclassifiedTxs.isEmpty()) {
         return;
      }

      log.info("Meminta AI ({}) mengklasifikasikan {} transaksi UNCLASSIFIED secara bulk...", aiProvider,
            unclassifiedTxs.size());

      try {
         // Dipecah jadi potongan (batch) berisi 15 transaksi per request agar tidak kena
         // limit 429
         int batchSize = 15;
         for (int i = 0; i < unclassifiedTxs.size(); i += batchSize) {
            int end = Math.min(unclassifiedTxs.size(), i + batchSize);
            List<BankTransaction> batchTxs = unclassifiedTxs.subList(i, end);

            List<Map<String, Object>> promptDataList = new ArrayList<>();
            for (int j = 0; j < batchTxs.size(); j++) {
               BankTransaction tx = batchTxs.get(j);
               Map<String, Object> data = new HashMap<>();
               data.put("id", i + j); // Gunakan index global array sebagai ID sementara
               data.put("desc", tx.getNormalizedDescription());
               data.put("type", tx.getMutationType() != null ? tx.getMutationType().name() : "");
               promptDataList.add(data);
            }

            String promptJsonStr = objectMapper.writeValueAsString(promptDataList);

            String prompt = "Anda adalah AI Sistem Analisis Keuangan Perbankan Indonesia. "
                  + "Tugas Anda membaca daftar transaksi bank pengguna dan mengklasifikasikannya HANYA ke salah satu kategori valid ini: "
                  + "INCOME, TAX, INTEREST, ADMIN, TRANSFER, ANOMALY, UNCLASSIFIED. "
                  + "Petunjuk: Jika itu gaji / bayaran / THR (type: CR) masukkan INCOME. Jika pajak masuk TAX. Jika biaya admin bulanan/harian (type: DB) masukkan ADMIN. Jika bunga bank masukkan INTEREST. "
                  + "Transaksi: " + promptJsonStr + " "
                  + "Anda HARUS mengembalikan string JSON Array persis seperti format ini TANPA block markdown (```json): "
                  + "[{\"id\": 0, \"category\": \"INCOME\"}, ...]";

            String aiJsonArrayStr = "";

            boolean success = false;
            int retryCount = 0;
            int maxRetries = 3;

            while (!success && retryCount < maxRetries) {
               try {
                  if ("gemini".equalsIgnoreCase(aiProvider)) {
                     if (geminiApiKey == null || geminiApiKey.trim().isEmpty()) {
                        log.warn("Gemini API Key tidak diset. Melewati proses AI Categorization.");
                        return;
                     }
                     aiJsonArrayStr = fetchFromGemini(prompt);
                  } else {
                     aiJsonArrayStr = fetchFromOllama(prompt);
                  }
                  success = true;
               } catch (RuntimeException e) {
                  if (e.getMessage().contains("429")) {
                     retryCount++;
                     log.warn("Terkena Limit 429 dari API (Retry {}/{}), menunggu 15 detik...", retryCount, maxRetries);
                     Thread.sleep(15000); // Tunggu 15 detik jika kena limit frekuensi
                  } else {
                     throw e; // Lempar ulang kalau error lain
                  }
               }
            }

            if (!success) {
               throw new RuntimeException(
                     "Gagal menghubungi AI setelah " + maxRetries + " kali percobaan karena limit API.");
            }

            if (aiJsonArrayStr != null && !aiJsonArrayStr.isEmpty()) {
               // Bersihkan dari markdown block text jika AI ngeyel
               int startIndex = aiJsonArrayStr.indexOf('[');
               int endIndex = aiJsonArrayStr.lastIndexOf(']');
               if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                  aiJsonArrayStr = aiJsonArrayStr.substring(startIndex, endIndex + 1);
               } else {
                  // Fallback
                  aiJsonArrayStr = aiJsonArrayStr.replace("```json", "").replace("```", "").trim();
               }

               // Parse Hasil JSON AI (Bisa berupa Array atau Object)
               log.debug("Raw AI Response: {}", aiJsonArrayStr);
               JsonNode rootResultNode = objectMapper.readTree(aiJsonArrayStr);
               List<Map<String, Object>> aiResults = new ArrayList<>();

               if (rootResultNode.isArray()) {
                  aiResults = objectMapper.convertValue(rootResultNode, new TypeReference<List<Map<String, Object>>>() {
                  });
               } else if (rootResultNode.isObject()) {
                  // Jika AI membungkusnya dalam satu parent object (misal: {"data": [...]})
                  for (JsonNode child : rootResultNode) {
                     if (child.isArray()) {
                        aiResults = objectMapper.convertValue(child, new TypeReference<List<Map<String, Object>>>() {
                        });
                        break;
                     }
                  }
                  // Jika tidak ada array, periksa barangkali ini cuma 1 object tunggal
                  if (aiResults.isEmpty() && rootResultNode.has("category")) {
                     aiResults.add(objectMapper.convertValue(rootResultNode, new TypeReference<Map<String, Object>>() {
                     }));
                  }
               }

               // Update Transaksi Asli
               for (Map<String, Object> result : aiResults) {
                  Integer idNode = (Integer) result.get("id");
                  String catNode = (String) result.get("category");

                  if (idNode != null && catNode != null && idNode < unclassifiedTxs.size()) {
                     try {
                        TransactionCategory newCat = TransactionCategory.valueOf(catNode.toUpperCase());
                        unclassifiedTxs.get(idNode).setCategory(newCat);
                        log.debug("AI mengklasifikasikan iterasi-{} sebagai {}", idNode, newCat);
                     } catch (IllegalArgumentException e) {
                        log.warn("AI mengembalikan kategori yang tidak dikenal: {}", catNode);
                     }
                  }
               }
            }

            // Jeda selama 3 detik sebelum melakukan batch berikutnya (Hindari HTTP 429
            // Google)
            if (end < unclassifiedTxs.size()) {
               Thread.sleep(3000);
            }
         }

         log.info("Batch AI Categorization berhasil diselesaikan.");

      } catch (Exception e) {
         log.error("Terjadi masalah saat AI Categorization: {}", e.getMessage(), e);
         throw new RuntimeException("Gagal melakukan klasifikasi via AI: " + e.getMessage(), e);
      }
   }

   private String fetchFromGemini(String prompt) throws Exception {
      Map<String, Object> part = new HashMap<>();
      part.put("text", prompt);

      Map<String, Object> content = new HashMap<>();
      content.put("parts", List.of(part));

      Map<String, Object> requestBody = new HashMap<>();
      requestBody.put("contents", List.of(content));

      Map<String, Object> genConfig = new HashMap<>();
      genConfig.put("response_mime_type", "application/json");
      genConfig.put("temperature", 0.0);
      requestBody.put("generationConfig", genConfig);

      String body = objectMapper.writeValueAsString(requestBody);
      String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key="
            + geminiApiKey;

      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();

      HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
         JsonNode rootNode = objectMapper.readTree(response.body());
         return rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
      } else {
         log.error("Gemini API Error: HTTP {} - {}", response.statusCode(), response.body());
         throw new RuntimeException("Gemini API Error: " + response.statusCode() + " - " + response.body());
      }
   }

   private String fetchFromOllama(String prompt) throws Exception {
      Map<String, Object> requestBody = new HashMap<>();
      requestBody.put("model", ollamaModel);
      requestBody.put("prompt", prompt);
      requestBody.put("stream", false);
      requestBody.put("format", "json");

      Map<String, Object> options = new HashMap<>();
      options.put("temperature", 0.0);
      requestBody.put("options", options);

      String body = objectMapper.writeValueAsString(requestBody);
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(120)).build();

      HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ollamaUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
         JsonNode rootNode = objectMapper.readTree(response.body());
         return rootNode.path("response").asText();
      } else {
         log.error("Ollama API Error: HTTP {} - {}", response.statusCode(), response.body());
         throw new RuntimeException("Ollama API Error: " + response.statusCode() + " - " + response.body());
      }
   }
}
