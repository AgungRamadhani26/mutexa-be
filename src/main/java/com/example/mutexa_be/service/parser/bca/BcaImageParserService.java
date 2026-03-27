package com.example.mutexa_be.service.parser.bca;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.entity.enums.MutationType;
import com.example.mutexa_be.entity.enums.TransactionCategory;
import com.example.mutexa_be.repository.BankTransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class BcaImageParserService {

   private final BankTransactionRepository bankTransactionRepository;
   private final RestTemplate restTemplate = new RestTemplate();
   private final ObjectMapper objectMapper = new ObjectMapper();

   private static final String OCR_MICROSERVICE_URL = "http://localhost:8000/api/v1/extract-image";

   public List<BankTransaction> parseAndSave(MutationDocument document, String filePath) {
      log.info("Memulai ekstraksi PaddleOCR via API untuk file BCA Scanner: {}", filePath);
      List<BankTransaction> transactions = new ArrayList<>();
      Set<String> seenHashes = new HashSet<>();

      int documentYear = document.getPeriodStart() != null ? document.getPeriodStart().getYear()
            : LocalDate.now().getYear();

      try (PDDocument pdDocument = Loader.loadPDF(new File(filePath))) {
         PDFRenderer renderer = new PDFRenderer(pdDocument);

         // Pass 1: Cari TAHUN dari beberapa halaman pertama
         for (int page = 0; page < Math.min(3, pdDocument.getNumberOfPages()); page++) {
            BufferedImage originalImage = renderer.renderImageWithDPI(page, 300);
            BufferedImage processedImage = preProcessImageForOcr(originalImage);
            String initialOcr = callPaddleOcrMicroservice(processedImage);
            // Cari pola "202x" (misal: NOVEMBER 2025)
            Matcher yearMatcher = Pattern.compile(".*?\\b(20[2-3][0-9])\\b").matcher(initialOcr);
            if (yearMatcher.find()) {
               documentYear = Integer.parseInt(yearMatcher.group(1));
               log.info("Berhasil menemukan Tahun Dokumen (PaddleOCR): {}", documentYear);
               break; // Ketemu di halaman ini, gak perlu cari di halaman lain
            }
         }

         // Array state agar nilai bisa terus di-update menembus batasan halaman
         int[] yearState = { documentYear };
         LocalDate[] dateState = { null };

         // Pass 2: Ekstraksi Data
         for (int page = 0; page < pdDocument.getNumberOfPages(); page++) {
            log.info("Memproses halaman {} dengan PaddleOCR... Pastikan API Python jalan!", (page + 1));

            BufferedImage originalImage = renderer.renderImageWithDPI(page, 300);
            BufferedImage processedImage = preProcessImageForOcr(originalImage);

            String ocrResult = callPaddleOcrMicroservice(processedImage);
            log.info("Berhasil membaca teks halaman {}. OCR Result:\n{}", page + 1, ocrResult);

            List<BankTransaction> pageTransactions = extractBankTransactions(ocrResult, document, seenHashes, yearState,
                  dateState);

            transactions.addAll(pageTransactions);
         }

         if (!transactions.isEmpty()) {
            bankTransactionRepository.saveAll(transactions);
            log.info("Sukses menyimpan {} total transaksi dari PDF BCA.", transactions.size());
         } else {
            log.warn("Tidak dapat menemukan baris transaksi BCA berformat pada hasil OCR.");
         }

      } catch (Exception e) {
         log.error("Terjadi kegagalan fatal saat (PaddleOCR) berjalan: {}", e.getMessage(), e);
      }

      return transactions;
   }

   /**
    * Memanggil Microservice PaddleOCR Python melalui HTTP POST
    */
   private String callPaddleOcrMicroservice(BufferedImage image) {
      try {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         // Konversi dari Buffer Java ke bytes .png
         ImageIO.write(image, "png", baos);
         byte[] imageBytes = baos.toByteArray();

         HttpHeaders headers = new HttpHeaders();
         headers.setContentType(MediaType.MULTIPART_FORM_DATA);

         MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
         body.add("file", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
               return "page.png";
            }
         });

         HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
         ResponseEntity<String> response = restTemplate.postForEntity(OCR_MICROSERVICE_URL, requestEntity,
               String.class);

         JsonNode root = objectMapper.readTree(response.getBody());
         if (root.has("status") && "success".equals(root.get("status").asText())) {
            return root.get("raw_text").asText();
         }
      } catch (Exception e) {
         log.error(
               "Gagal memanggil Python PaddleOCR Microservice di port 8000. Pastikan main.py sedang berjalan! | Error: {}",
               e.getMessage());
         throw new RuntimeException("Service OCR mati.");
      }
      return "";
   }

   /**
    * Memperjelas kualitas gambar (Hitam & Putih) sebelum dilumat AI.
    * Gambar scan biasanya memiliki noise abu-abu di latar belakang.
    */
   private BufferedImage preProcessImageForOcr(BufferedImage original) {
      BufferedImage bWImage = new BufferedImage(original.getWidth(), original.getHeight(),
            BufferedImage.TYPE_BYTE_BINARY);
      Graphics2D graphics = bWImage.createGraphics();
      // Meningkatkan kontras hitam putih dengan memindahkannya ke tipe BINARY image
      graphics.drawImage(original, 0, 0, Color.WHITE, null);
      graphics.dispose();
      return bWImage;
   }

   /**
    * Ekstraktor Logika/Regex untuk memecah teks Tesseract (spasi terjaga) ke tabel
    * Java (BCA Format).
    */
   private List<BankTransaction> extractBankTransactions(String ocrText, MutationDocument document,
         Set<String> seenHashes, int[] yearState, LocalDate[] dateState) {
      List<BankTransaction> list = new ArrayList<>();
      String[] lines = ocrText.replace("\\n", "\n").split("\n");

      // Pattern untuk deteksi Date di BCA (Toleransi karakter \ / l I 1 dll). Date
      // bisa di mana saja.
      Pattern patternDate = Pattern.compile("(?i)\\b([Oo0-3]?[0-9]\\s*[/|lI1!,\\\\]\\s*[Oo0-1]?[0-9])\\b");

      // RUBAH KE STRICT PATTERN: Harus ada 2 digit desimal (,00 atau .00) di
      // belakang!
      // Ini mencegah angka-angka referensi di Keterangan seperti "INV 15.000"
      // terdeteksi sebagai Saldo/Mutasi!
      Pattern amountPattern = Pattern.compile("(\\b\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})\\b)");

      for (int i = 0; i < lines.length; i++) {
         String line = lines[i].trim();
         if (line.isEmpty() || line.toLowerCase().contains("saldo awal")
               || line.toLowerCase().contains("halaman berikut") || line.toLowerCase().contains("saldo akhir"))
            continue;

         Matcher m = patternDate.matcher(line);
         boolean hasDate = m.find();
         boolean isJustTanggal = line.toUpperCase().startsWith("TANGGAL");

         // PERBAIKAN: Tanggal MUTASI harus ada di kisaran AWAL baris (index < 30) agar
         // tidak menangkap
         // teks "TANGGAL LAHIR 12/03" di tengah-tengah deskripsi. Ini menyelesaikan bug
         // "125 transaksi" menjadi benar 116.
         boolean isValidDatePosition = hasDate && m.start() <= 30;

         if (isValidDatePosition && !isJustTanggal) {
            String rawDateGroup = m.group(1);

            // Kita gabungkan baris saat ini dengan 2-3 baris berikutnya untuk memastikan
            // deskripsi dan angka (amount) yang sering terpotong ke bawah bisa terambil
            // semuanya
            StringBuilder stitched = new StringBuilder(line);
            int jump = i;
            for (int k = 1; k <= 3; k++) {
               if (i + k < lines.length) {
                  String nextLine = lines[i + k].trim();

                  // Deteksi batas halaman atau akhir tabel
                  if (nextLine.toLowerCase().contains("saldo awal") ||
                        nextLine.toLowerCase().contains("halaman berikut") ||
                        nextLine.toLowerCase().contains("saldo akhir"))
                     break;

                  Matcher nm = patternDate.matcher(nextLine);
                  boolean nextHasDate = nm.find();
                  boolean nextIsJustTanggal = nextLine.toUpperCase().startsWith("TANGGAL");

                  // Jika line berikutnya juga punya format tanggal (dan bukan sekedar string
                  // "Tanggal :")
                  // dan mengandung setidaknya satu amount... berarti itu adalah baris mutasi
                  // baru. Stop gabung.
                  if (nextHasDate && !nextIsJustTanggal && nextLine.length() > 10) {
                     Matcher nam = amountPattern.matcher(nextLine);
                     if (nam.find()) {
                        break;
                     }
                  }

                  stitched.append(" ").append(nextLine);
                  jump = i + k;
               }
            }

            String fullText = stitched.toString();
            String rawDesc = fullText;

            // Ekstrak Nilai Uang (Amount) & Balance
            Matcher am = amountPattern.matcher(rawDesc);
            List<String> currencyMatches = new ArrayList<>();
            while (am.find()) {
               currencyMatches.add(am.group(1));
               // Kita hapus angka dari calon description text agar deskripsinya bersih
               rawDesc = rawDesc.replace(am.group(1), "");
            }

            // Hapus string Date regex yang trigger pertama kali
            rawDesc = rawDesc.replaceFirst("(?i)\\b" + Pattern.quote(rawDateGroup) + "\\b", "");

            // Bersihkan sisa keyword yang nyempil
            rawDesc = rawDesc.replaceAll("(?i)\\b(DB|CR|TGL:|TANGGAL :)\\b", "").trim();
            // Bersihkan kelebihan spasi
            rawDesc = rawDesc.replaceAll("\\s+", " ").trim();

            // Lanjut ke identifikasi Date
            String cleanStr = rawDateGroup.replaceAll("[Oo]", "0").replaceAll("[Iil|]", "1");
            String dateStr = cleanStr.replaceAll("[^0-9]", ""); // "01f11" -> "0111", "131" -> "131"

            LocalDate txDate = null;
            try {
               if (dateStr.length() >= 3 && dateStr.length() <= 4) {
                  int day, month;
                  if (dateStr.length() == 4) {
                     day = Integer.parseInt(dateStr.substring(0, 2));
                     month = Integer.parseInt(dateStr.substring(2, 4));
                  } else {
                     // Format rusak 3 digit (misal: "131" -> 13 bulan 1)
                     day = Integer.parseInt(dateStr.substring(0, 2));
                     month = Integer.parseInt(dateStr.substring(2, 3));
                  }

                  if (day >= 1 && day <= 31) {
                     if (month < 1 || month > 12 || dateStr.length() == 3) {
                        if (dateState[0] != null)
                           month = dateState[0].getMonthValue();
                     }

                     if (dateState[0] != null) {
                        int prevMonth = dateState[0].getMonthValue();
                        if (prevMonth == 12 && month == 1) {
                           yearState[0]++; // Tahun berganti!
                           log.info(
                                 "Mendeteksi pergantian tahun dari Desember ke Januari. Tahun diperbarui menjadi: {}",
                                 yearState[0]);
                        } else if (prevMonth == 1 && month == 12) {
                           yearState[0]--;
                        }
                     }

                     txDate = LocalDate.of(yearState[0], month, day);
                  }
               }
            } catch (Exception ignored) {
            }

            // Fallback ke tanggal yang di atasnya jika format tanggal baris ini sangat
            // rusak ("O11" tidak valid day/month)
            if (txDate == null && dateState[0] != null) {
               txDate = dateState[0];
            } else if (txDate == null) {
               continue; // Kalau ini baris paling awal dan gagal dibaca tanggalnya, loncat saja
            }
            dateState[0] = txDate; // Simpan untuk baris berikutnya di bawah

            BigDecimal amount = BigDecimal.ZERO;
            BigDecimal balance = BigDecimal.ZERO;
            MutationType type = MutationType.DB;

            if (fullText.toUpperCase().contains(" CR ") || fullText.toUpperCase().endsWith(" CR")) {
               type = MutationType.CR;
            }

            if (currencyMatches.size() >= 2) {
               String balStr = currencyMatches.get(currencyMatches.size() - 1);
               String amtStr = currencyMatches.get(currencyMatches.size() - 2);

               if (!balStr.replaceAll("[^\\d]", "").isEmpty())
                  balance = new BigDecimal(balStr.replaceAll("[^\\d]", "")).divide(new BigDecimal(100));

               if (!amtStr.replaceAll("[^\\d]", "").isEmpty())
                  amount = new BigDecimal(amtStr.replaceAll("[^\\d]", "")).divide(new BigDecimal(100));

            } else if (currencyMatches.size() == 1) {
               String amtStr = currencyMatches.get(0);
               if (!amtStr.replaceAll("[^\\d]", "").isEmpty())
                  amount = new BigDecimal(amtStr.replaceAll("[^\\d]", "")).divide(new BigDecimal(100));
            }

            if (rawDesc.length() < 3 && amount.compareTo(BigDecimal.ZERO) == 0)
               continue;

            BankTransaction tx = BankTransaction.builder()
                  .mutationDocument(document)
                  .bankAccount(document.getBankAccount())
                  .transactionDate(txDate)
                  .rawDescription(rawDesc)
                  .normalizedDescription(rawDesc)
                  .mutationType(type)
                  .amount(amount)
                  .balance(balance)
                  .category(TransactionCategory.UNCLASSIFIED)
                  .isExcluded(false)
                  .build();

            // Generate MD5 Anti-Duplicate
            String uniqueRawString = document.getId() + "_" + txDate.toString() + "_" + amount.toString() + "_"
                  + type.name() + "_" + rawDesc;
            tx.setDuplicateHash(generateMd5Hash(uniqueRawString));

            // Hanya save jika transaksi ini belum pernah di-insert dan tidak duplikat di
            // file yang sama
            if (!seenHashes.contains(tx.getDuplicateHash())
                  && !bankTransactionRepository.existsByDuplicateHash(tx.getDuplicateHash())) {
               seenHashes.add(tx.getDuplicateHash());
               list.add(tx);
            }

            i = jump; // Majukan pointer iterasi
         }
      }
      return list;
   }

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
         throw new RuntimeException("MD5 alg missing", e);
      }
   }
}