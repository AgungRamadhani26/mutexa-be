package com.example.mutexa_be.service.parser;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.entity.enums.MutationType;
import com.example.mutexa_be.entity.enums.TransactionCategory;
import com.example.mutexa_be.repository.BankTransactionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaOcrService {

    private final ObjectMapper objectMapper;
    private final BankTransactionRepository bankTransactionRepository;

    // URL Server Ollama lokal yang berjalan di PC Anda
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    // Nama model yang barusan Anda download
    private static final String MODEL_NAME = "llama3.2-vision";

    /**
     * Memproses PDF berbasis gambar/scan (seperti BCA) dengan memotong halamannya menjadi gambar (PNG),
     * lalu mengirimkannya satu per satu ke Ollama untuk diekstrak.
     * 
     * @param document Entity file dokumen
     * @param filePath Lokasi file PDF di harddisk
     */
    public List<BankTransaction> processImagePdf(MutationDocument document, String filePath) throws Exception {
        log.info("Memulai ekstraksi gambar dari PDF (OCR via Ollama): {}", filePath);
        List<BankTransaction> allTransactions = new ArrayList<>();

        // 1. Buka file PDF menggunakan PDFBox
        try (PDDocument pdDocument = Loader.loadPDF(new File(filePath))) {
            // PDFRenderer digunakan untuk mengubah halaman PDF (teks/vektor) menjadi format pixel/gambar
            PDFRenderer renderer = new PDFRenderer(pdDocument);
            
            // Loop setiap halaman yang ada di dalam PDF tersebut
            for (int page = 0; page < pdDocument.getNumberOfPages(); page++) {
                log.info("Mengekstrak halaman {} dari {}", (page + 1), pdDocument.getNumberOfPages());
                
                // Ubah halaman PDF ke format BufferedImage (resolusi 300 DPI agar cukup jernih dibaca AI)
                BufferedImage bim = renderer.renderImageWithDPI(page, 300);
                
                // Ubah gambar menjadi format string Base64 (Syarat wajib untuk dikirim lewat JSON/API ke Ollama)
                String base64Image = convertImageToBase64(bim);
                
                // 2. Minta Ollama untuk membaca gambar tersebut dan mengembalikannya dlm bentuk JSON
                String ocrJsonResponse = askOllamaToExtractData(base64Image);
                
                if (ocrJsonResponse != null && !ocrJsonResponse.isEmpty()) {
                    // 3. Ubah (parse) JSON teks dari Ollama kembali menjadi List objek Map Java
                    List<Map<String, String>> extractedDataList = parseJsonToMap(ocrJsonResponse);
                    
                    // 4. Konversi Data Mentah (Map) menjadi Entitas Database (BankTransaction)
                    LocalDate defaultDate = LocalDate.now();
                    for (Map<String, String> data : extractedDataList) {
                        try {
                            BankTransaction tx = buildTransaction(document, data, defaultDate);
                            
                            // Anti Duplikasi: Jangan simpan jika sudah ada transaksi yang benar-benar persis di DB
                            if (!bankTransactionRepository.existsByDuplicateHash(tx.getDuplicateHash())) {
                                allTransactions.add(tx);
                            }
                        } catch (Exception e) {
                            log.error("Gagal melakukan konversi satu baris transaksi dari JSON OCR (halaman {}): {}", page + 1, e.getMessage());
                            // Lanjut iterasi baris berikutnya meskipun 1 baris gagal (best effort)
                        }
                    }
                }
            }
        }
        
        // Simpan semua hasil OCR ke SQL Server menggunakan metode saveAll (lebih efisien daripada save satu per satu)
        if (!allTransactions.isEmpty()) {
            bankTransactionRepository.saveAll(allTransactions);
            log.info("Total transaksi OCR tersimpan dari PDF BCA: {}", allTransactions.size());
        } else {
            log.warn("Tidak ada satupun transaksi utuh yang bisa diekstrak dari dokumen BCA ini.");
        }

        return allTransactions;
    }

    /**
     * Konversi Image (Bawaan Java) ke Teks Base64 (Supaya bisa disisipkan ke JSON Post Body)
     */
    private String convertImageToBase64(BufferedImage image) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream); // Tulis format PNG
        byte[] imageBytes = outputStream.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    /**
     * Berkomunikasi HTTP (REST) ke API Ollama lokal.
     * Mengirimkan instruksi rahasia (Prompting) yang mengatur output data haruslah array JSON yang rapi!
     */
    private String askOllamaToExtractData(String base64Image) {
        log.info("Mengirim gambar ke Ollama ({})...", MODEL_NAME);

        try {
            // Prompt inilah otak utamanya. Semakin jelas kita menginstruksikan AI, semakin rapi JSON yang kita peroleh.
            // Kita secara eksplisit membatasi dia dari menceracau atau memberikan penjelasan yang gak diminta.
            String prompt = "Anda adalah ekstrator data perbankan yang sangat akurat. " +
                    "Saya berikan sebuah gambar hasil scan halaman mutasi rekening (BCA atau bank lain). " +
                    "Temukan semua baris tabel mutasi rekening. " +
                    "Kembalikan murni hanya dalam bentuk list JSON tanpa teks embel-embel apapun sebelum atau sesudahnya. \n" +
                    "Format array JSON yang DIBUTUHKAN:\n" +
                    "[\n" +
                    "  {\n" +
                    "    \"date\": \"DD/MM/YYYY atau DD/MM\",\n" +
                    "    \"description\": \"Keterangan lengkap transaksi beserta nomor atau nama pengirim/penerima\",\n" +
                    "    \"type\": \"DB atau CR (Tebak dari posisi kolom atau keterangan, DB jika uang keluar, CR jika uang masuk)\",\n" +
                    "    \"amount\": \"1500000.00 (Hanya angka, hilangkan tanda RP, buang titik ribuan pemisah, pertahankan desimal)\",\n" +
                    "    \"balance\": \"10000000.00 (Saldo akhir jika tersedia di baris tersebut)\"\n" +
                    "  }\n" +
                    "]";

            // Membuat struktur Body JSON yang akan dikirim (format bawaan Ollama)
            // stream: false artinya kita tunggu sampai mikirnya selesai baru merespon semuanya (tdk dieja huruf per huruf)
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", MODEL_NAME);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", false);
            // Menempelkan foto ke JSON
            requestBody.put("images", Collections.singletonList(base64Image));

            String bodyJsonText = objectMapper.writeValueAsString(requestBody);

            // Bikin klien HTTP Bawaan Java 11+
            HttpClient client = HttpClient.newHttpClient();
            
            // Siapkan amplop HTTP Request dengan tujuannya (POST localhost:11434)
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJsonText))
                    .build();

            // Eksekusi / Kirim ke Ollama dan Tunggu balasannya
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                // Konversi balasan teks dari Ollama ke Map untuk mengambil properti "response"
                Map<String, Object> responseMap = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
                String aiText = (String) responseMap.get("response");
                
                // Kadang LLM bandel memberikan "```json ... ```" sebagai markdown, jadi kita buang itu
                if (aiText != null) {
                    aiText = aiText.replace("```json", "").replace("```", "").trim();
                    log.info("Ekstraksi OCR dari Ollama sukses didapatkan");
                }
                return aiText;
            } else {
                log.error("Ollama mengembalikan status kesalahan: {} - {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Gagal terhubung atau mem-parsing balasan Ollama: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Mengubah teks balasan AI yang sudah bersih berupa array menjadi struktur Data di Java
     */
    private List<Map<String, String>> parseJsonToMap(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            log.warn("Output dari Ollama ternyata bukan JSON Valid. Abaikan halaman ini. Pesan AI: \n{}", jsonString);
            return Collections.emptyList();
        }
    }

    /**
     * Jembatan perantara: Membentuk Entitas (Tabel) dari baris-baris Data Map hasil OCR Ollama tadi.
     */
    private BankTransaction buildTransaction(MutationDocument document, Map<String, String> data, LocalDate defaultDate) {
        // 1. Parsing Tanggal (BCA biasanya pakai DD/MM, misal 05/11)
        LocalDate txDate = defaultDate;
        if (data.containsKey("date") && !data.get("date").isBlank()) {
            String dateStr = data.get("date").trim();
            try {
                // Di sini kita kasih skema tebak sederhana, jika dia hanya DD/MM berarti tahun ini (BCA style)
                if (dateStr.length() == 5 && dateStr.contains("/")) {
                    int day = Integer.parseInt(dateStr.substring(0, 2));
                    int month = Integer.parseInt(dateStr.substring(3, 5));
                    txDate = LocalDate.of(defaultDate.getYear(), month, day);
                } else if (dateStr.length() > 8) { // mungkin full DD/MM/YYYY
                    txDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                }
            } catch (Exception ignored) {
                // Kalau format tanggalnya aneh, biarkan pakai tanggal default/sekarang, 
                // ini OCR, kemungkinan baca salah selalu ada.
            }
        }

        // 2. Parsing Angka Jumlah (Amount) dan Saldo (Balance)
        BigDecimal amount = BigDecimal.ZERO;
        if (data.containsKey("amount") && !data.get("amount").isBlank()) {
            try {
                amount = new BigDecimal(data.get("amount").replaceAll("[^\\d.]", ""));
            } catch (Exception ignored) {}
        }

        BigDecimal balance = BigDecimal.ZERO;
        if (data.containsKey("balance") && !data.get("balance").isBlank()) {
            try {
                balance = new BigDecimal(data.get("balance").replaceAll("[^\\d.]", ""));
            } catch (Exception ignored) {}
        }
        
        // 3. Tentukan DB (Debet/Keluar) / CR (Kredit/Masuk)
        MutationType type = MutationType.DB; 
        if (data.containsKey("type") && data.get("type").toUpperCase().contains("CR")) {
            type = MutationType.CR;
        }
        
        String desc = data.getOrDefault("description", "OCR: Keterangan Tidak Terdeteksi").trim();

        String cpName = desc;
        java.util.regex.Matcher bcaM = java.util.regex.Pattern.compile("(PT\\.?|CV\\.?)\\s+([A-Z0-9 ]{3,30})").matcher(desc.toUpperCase());
        if (bcaM.find()) cpName = bcaM.group(0).trim();
        else {
            java.util.regex.Matcher bM = java.util.regex.Pattern.compile("(?:DARI|KE)\\s+([A-Z0-9\\.\\- ]+?)(?:\\s+[0-9]{10,}|$)").matcher(desc.toUpperCase());
            if (bM.find() && bM.group(1).trim().length()>3) cpName = bM.group(1).trim();
            else {
                cpName = desc.toUpperCase().replaceAll("TRANSFER DANA|MCM|PINBUK|WSID.*|\\b[A-Z0-9]{12,}\\b", "").replaceAll("[^A-Z0-9 ]", " ").trim();
            }
        }
        if (cpName.length() > 30) cpName = cpName.substring(0, 30);

        // 4. Membangun objek yang siap disimpan di Database
        BankTransaction tx = BankTransaction.builder()
                .mutationDocument(document)
                .bankAccount(document.getBankAccount())
                .transactionDate(txDate)
                .rawDescription(desc) // Catat keterangan asli
                .normalizedDescription(desc) // Untuk fase 6 (Normalization), smentara kita samakan dgn RAW
                .counterpartyName(cpName)
                .mutationType(type)
                .amount(amount)
                .balance(balance)
                .category(TransactionCategory.UNCLASSIFIED) // Semua di-set UNCATED. Kategori dipisah di fase 7
                .build();
                
        // 5. Generate MD5 keunikan (Idempotensi: Gabungan FileId + Tgl + Jumlah + DB/CR + Tipe)
        String uniqueRawString = document.getId() + "_" + txDate.toString() + "_" + amount.toString() + "_" + type.name() + "_" + desc;
        tx.setDuplicateHash(generateMd5Hash(uniqueRawString));
        
        return tx;
    }

    /**
     * Membentuk Hash Code Enkripsi 1 Arah untuk identifikasi duplikat.
     */
    private String generateMd5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm missing", e);
        }
    }
}
