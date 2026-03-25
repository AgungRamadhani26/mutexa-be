package com.example.mutexa_be.service.parser.bca;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.entity.enums.MutationType;
import com.example.mutexa_be.entity.enums.TransactionCategory;
import com.example.mutexa_be.repository.BankTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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

   public List<BankTransaction> parseAndSave(MutationDocument document, String filePath) {
      log.info("Memulai ekstraksi Tesseract OCR untuk file BCA Scanner: {}", filePath);
      List<BankTransaction> transactions = new ArrayList<>();
      Set<String> seenHashes = new HashSet<>();

      // 1. Inisialisasi Tesseract dengan Konfigurasi Presisi Tinggi untuk Tabel
      ITesseract tesseract = new Tesseract();

      // PENTING: Arahkan ke tempat program Tesseract diinstall.
      // Jika Anda install di "C:\Program Files\Tesseract-OCR", maka arahkan datapath
      // ke "\tessdata" di dalamnya
      tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
      tesseract.setLanguage("eng");

      // --- KONFIGURASI TINGKAT DEWA UNTUK MEMBACA TABEL (SPASI TERJAGA) ---
      // Setting ini mempertahankan jarak spasi sehingga kolom Saldo dan Jumlah tidak
      // menyatu
      tesseract.setTessVariable("preserve_interword_spaces", "1");
      // Mode 6: Assume a single uniform block of text. Ini ideal buat mutasi berderet
      // panjang ke bawah
      tesseract.setPageSegMode(6);

      // State antar halaman untuk mengatasi Tesseract gagal baca teks (misal: Tahun &
      // Tanggal Terakhir)
      int documentYear = document.getPeriodStart() != null ? document.getPeriodStart().getYear()
            : LocalDate.now().getYear();

      try (PDDocument pdDocument = Loader.loadPDF(new File(filePath))) {
         PDFRenderer renderer = new PDFRenderer(pdDocument);

         // Pass 1: Cari TAHUN dari beberapa halaman pertama
         for (int page = 0; page < Math.min(3, pdDocument.getNumberOfPages()); page++) {
            BufferedImage originalImage = renderer.renderImageWithDPI(page, 300);
            BufferedImage processedImage = preProcessImageForOcr(originalImage);
            String initialOcr = tesseract.doOCR(processedImage);
            // Cari pola "202x" (misal: NOVEMBER 2025)
            Matcher yearMatcher = Pattern.compile(".*?\\b(20[2-3][0-9])\\b").matcher(initialOcr);
            if (yearMatcher.find()) {
               documentYear = Integer.parseInt(yearMatcher.group(1));
               log.info("Berhasil menemukan Tahun Dokumen: {}", documentYear);
               break; // Ketemu di halaman ini, gak perlu cari di halaman lain
            }
         }

         // Array state agar nilai bisa terus di-update menembus batasan halaman
         int[] yearState = { documentYear };
         LocalDate[] dateState = { null };

         // Pass 2: Ekstraksi Data
         for (int page = 0; page < pdDocument.getNumberOfPages(); page++) {
            log.info("Memproses halaman {} dengan Tesseract...", (page + 1));

            BufferedImage originalImage = renderer.renderImageWithDPI(page, 300);
            BufferedImage processedImage = preProcessImageForOcr(originalImage);

            String ocrResult = tesseract.doOCR(processedImage);
            log.info("Berhasil membaca teks halaman {}.", page + 1);

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
         log.error("Terjadi kegagalan fatal saat Tesseract OCR berjalan: {}", e.getMessage(), e);
      }

      return transactions;
   }

   /**
    * Memperjelas kualitas gambar (Hitam & Putih) sebelum dilumat oleh Tesseract.
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
      String[] lines = ocrText.split("\n");

      // Regex BCA digabungkan lebih rileks: Angka kadang terbaca huruf I/l/O, atau
      // spasi terpisah
      // Contoh: "O1f11", "05/11", "05 / 11", "O11", "O7/11"
      Pattern patternDate = Pattern.compile("^\\s*([Oo0-3]?[0-9]\\s*[/|Iil1!.,f\\\\]?\\s*[Oo0-1]?[0-9])\\s+(.*)");

      for (String line : lines) {
         line = line.trim();
         if (line.isEmpty() || line.toLowerCase().contains("saldo awal")
               || line.toLowerCase().contains("halaman berikut"))
            continue;

         Matcher matcher = patternDate.matcher(line);
         if (matcher.find()) {
            String rawDateGroup = matcher.group(1);
            // Konversi karakter typo OCR yang sering terjadi sebelum di regex
            String cleanStr = rawDateGroup.replaceAll("[Oo]", "0").replaceAll("[Iil|]", "1");
            String dateStr = cleanStr.replaceAll("[^0-9]", ""); // "01f11" -> "0111", "011" -> "011"
            String remainingText = matcher.group(2).trim();

            LocalDate txDate = null;
            try {
               if (dateStr.length() >= 3 && dateStr.length() <= 4) {
                  int day, month;
                  if (dateStr.length() == 4) {
                     day = Integer.parseInt(dateStr.substring(0, 2));
                     month = Integer.parseInt(dateStr.substring(2, 4));
                  } else {
                     // Format rusak dari tesseract panjangnya cuma 3 digit (misal: "011").
                     // Kita ambil 2 digit pertama sebagai hari. Bulannya sering terpotong 1 digit
                     // doang, jadi kita biarkan dulu.
                     day = Integer.parseInt(dateStr.substring(0, 2));
                     month = Integer.parseInt(dateStr.substring(2, 3));
                  }

                  // Jika bulan beda dengan expected, tapi harinya masuk akal, kita paksa pakai
                  // bulan dokumen jika ketemu (atau pakai lastSeenDate nanti)
                  if (day >= 1 && day <= 31) {
                     if (month < 1 || month > 12 || dateStr.length() == 3) {
                        if (dateState[0] != null)
                           month = dateState[0].getMonthValue();
                     }

                     // Logika Transisi Tahun (Desember -> Januari)
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

            String rawDesc = remainingText;
            BigDecimal amount = BigDecimal.ZERO;
            BigDecimal balance = BigDecimal.ZERO;
            MutationType type = MutationType.DB;

            if (remainingText.toUpperCase().contains(" CR ") || remainingText.toUpperCase().endsWith(" CR")) {
               type = MutationType.CR;
            }

            // Ekstrak Nilai Uang (Amount) yang ada di akhir kalimat (biasanya:
            // "1.250.000,00 DB")
            Matcher currMatcher = Pattern.compile("((?:\\d{1,3}[., ]?)+[., ]\\d{2})").matcher(remainingText);
            List<String> currencyMatches = new ArrayList<>();
            List<Integer> startIndices = new ArrayList<>();

            while (currMatcher.find()) {
               currencyMatches.add(currMatcher.group(1));
               startIndices.add(currMatcher.start(1));
            }

            if (currencyMatches.size() >= 2) {
               String balStr = currencyMatches.get(currencyMatches.size() - 1);
               String amtStr = currencyMatches.get(currencyMatches.size() - 2);

               if (!balStr.replaceAll("[^\\d]", "").isEmpty())
                  balance = new BigDecimal(balStr.replaceAll("[^\\d]", "")).divide(new BigDecimal(100));

               if (!amtStr.replaceAll("[^\\d]", "").isEmpty())
                  amount = new BigDecimal(amtStr.replaceAll("[^\\d]", "")).divide(new BigDecimal(100));

               int amtStartIndex = startIndices.get(startIndices.size() - 2);
               rawDesc = remainingText.substring(0, amtStartIndex).trim();
            } else if (currencyMatches.size() == 1) {
               String amtStr = currencyMatches.get(0);
               if (!amtStr.replaceAll("[^\\d]", "").isEmpty())
                  amount = new BigDecimal(amtStr.replaceAll("[^\\d]", "")).divide(new BigDecimal(100));

               int amtStartIndex = startIndices.get(0);
               rawDesc = remainingText.substring(0, amtStartIndex).trim();
            }

            // Jika line ini hanya berisi string yang panjangnya < 3 (misal sisa DB / CR
            // saja), ignore
            if (rawDesc.length() < 3)
               continue;

            rawDesc = rawDesc.replaceAll("(?i)\\s*(DB|CR)$", "").trim();

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