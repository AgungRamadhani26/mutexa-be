package com.example.mutexa_be.service.parser.bri;

import com.example.mutexa_be.service.parser.PdfParserService;
import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.entity.enums.MutationType;
import com.example.mutexa_be.entity.enums.TransactionCategory;
import com.example.mutexa_be.service.TransactionRefinementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Service
public class BriPdfParserService implements PdfParserService {

   private final TransactionRefinementService transactionRefinementService;

   @Override
   public String getBankName() { return "BRI"; }

   // Regex untuk mendeteksi baris tabel yang dimulai dengan Tanggal & Jam (contoh:
   // 01/12/25 09:29:59)
   // Grup 1: Tanggal (DD/MM/YY)
   // Grup 2: Jam (HH:MM:SS)
   // Grup 3: Sisa teks di baris itu
   private static final String BRI_LINE_PATTERN = "^(\\d{2}/\\d{2}/\\d{2}) (\\d{2}:\\d{2}:\\d{2})\\s+(.*)";
   private static final Pattern linePattern = Pattern.compile(BRI_LINE_PATTERN);

   // Formatter untuk mengubah String tanggal "01/12/25" menjadi LocalDate
   private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yy");

   @Override
   public List<BankTransaction> parse(MutationDocument document, String filePath) {
      List<BankTransaction> transactions = new ArrayList<>();
      File file = new File(filePath);

      if (!file.exists()) {
         throw new IllegalArgumentException("File PDF tidak ditemukan di lokasi: " + filePath);
      }

      try (PDDocument pdfDocument = Loader.loadPDF(file)) {
         PDFTextStripper stripper = new PDFTextStripper();
         stripper.setSortByPosition(true); // Wajib agar teks tiap kolom tidak berpindah acak

         // Ekstrak seluruh teks dari halaman awal sampai akhir
         String entireText = stripper.getText(pdfDocument);

         // Masukkan logika pemecahan baris di sini ke metode terpisah
         transactions = extractLinesAndBuildTransactions(document, entireText);

         log.info("Berhasil mem-parsing PDF BRI. Ditemukan {} buah transaksi.", transactions.size());

      } catch (Exception e) {
         log.error("Terjadi masalah saat parsing PDF BRI: {}", e.getMessage(), e);
         throw new RuntimeException("Gagal melakukan parsing dokumen PDF.", e);
      }

      return transactions;
   }

   private List<BankTransaction> extractLinesAndBuildTransactions(MutationDocument document, String entireText) {
      List<BankTransaction> list = new ArrayList<>();
      java.util.Map<String, Integer> hashCounters = new java.util.HashMap<>();

      // Memecah teks besar PDFBox menjadi per satu baris yang berurutan
      String[] lines = entireText.split("\\r?\\n");

      // Tempat penampungan sementara saat menyusun 1 blok transaksi
      // Karena di BRI, 1 buah transaksi bisa terbagi-bagi tulisan keterangannya ke
      // baris bawah
      BankTransactionBuilder currentTxBuilder = null;

      for (String line : lines) {
         line = line.trim();
         if (line.isEmpty())
            continue;

         // Apakah baris ini dicurigai SEBAGAI BARIS AWAL TRANSAKSI BARU? (Didahului
         // tanggal)
         Matcher matcher = linePattern.matcher(line);
         if (matcher.find()) {

            // Jika sebelumnya kita sedang menyusun sebuah transaksi (dan sekarang menemukan
            // baris transaksi baru),
            // maka simpan (flush) transaksi sebelumnya ke dalam List akhir.
            if (currentTxBuilder != null) {
               list.add(finalizeTransaction(currentTxBuilder, document, hashCounters));
            }

            String dateStr = matcher.group(1); // misal: "01/12/25"
            String timeStr = matcher.group(2); // misal: "09:29:59"
            String remainingText = matcher.group(3); // misal: "QRISRNS... 8888447 49,370.00 0.00 ..."

            // Mulai buat objek kerangka transaksi baru
            currentTxBuilder = new BankTransactionBuilder();

            try {
               // Coba paksa Parse string (01/12/25) menjadi format LocalDate khusus Java
               currentTxBuilder.dateStr = LocalDate.parse(dateStr, DATE_FORMATTER);
            } catch (DateTimeParseException e) {
               log.warn("Gagal parse tanggal BRI: {}, kita hiraukan.", dateStr);
               currentTxBuilder = null;
               continue; // Skip the line
            }

            // Kita masukkan "Remaining Text" dari barisan tersebut untuk disortir angka
            // mutasi dan saldonya.
            extractNominalsFromRemainingText(remainingText, currentTxBuilder);

         } else {
            // MASUK SINI JIKA: Baris ini TIDAK DIAWALI TANGGAL.
            // Bisa jadi ini adalah "keterangan panjang bertipe multi-line" dari transaksi
            // baris sebelumnya

            // CEGAH KEBOCORAN FOOTER & HEADER HALAMAN (Page Break):
            String lowerLine = line.toLowerCase();
            if (lowerLine.contains("saldo awal") || lowerLine.contains("opening balance")
                  || lowerLine.contains("total transaksi") || lowerLine.contains("terbilang")
                  || lowerLine.contains("biaya materai") || lowerLine.contains("apabila terdapat perbedaan")
                  || lowerLine.contains("salinan rekening koran") || lowerLine.contains("ibiz_")
                  || lowerLine.contains("created by") || lowerLine.contains("laporan transaksi finansial")
                  || lowerLine.contains("statement of financial transaction") || lowerLine.contains("halaman ")
                  || lowerLine.contains("page ") || lowerLine.contains("tanggal transaksi")
                  || lowerLine.contains("uraian transaksi") || lowerLine.contains("transaction date")
                  || lowerLine.contains("transaction description") || lowerLine.matches("^\\d{15,25}$")
                  || lowerLine.matches("^\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}$")) {

               // Jika sedang membangun transaksi terakhir, selesaikan sekarang dan putus
               // pembacaan baris ini
               if (currentTxBuilder != null) {
                  list.add(finalizeTransaction(currentTxBuilder, document, hashCounters));
                  currentTxBuilder = null; // Matikan pembacaan karena sudah masuk wilayah Footer/Header Halaman
               }
               continue; // Skip baris footer ini
            }

            // Pastikan currentTxBuilder itu sedang berjalan/aktif.
            if (currentTxBuilder != null) {
               // Gabungkan keterangan ini dengan keterangan utama
               // Beri spasi agar hurufnya tidak bertabrakan saat digabung
               currentTxBuilder.rawDescription += " " + line;
            }
         }
      } // akhir dari loop baris

      // Jangan lupakan transaksi terakhir dari barisan tersebut, masukkan!
      if (currentTxBuilder != null) {
         list.add(finalizeTransaction(currentTxBuilder, document, hashCounters));
      }

      return list;
   }

   /**
    * Memecah sisa bagian teks di ujung baris menjadi 4 kelompok:
    * Keterangan, Nilai Debit (Uang Keluar), Nilai Kredit (Uang Masuk), Saldo
    */
   private void extractNominalsFromRemainingText(String remainingText, BankTransactionBuilder builder) {

      // Ciri khas tabel mutasi (berdasar log): di ujung pasti selalu ada minimal 3
      // balok angka
      // Contoh remainingText: "QRISRNS93896 8888447 49,370.00 0.00 1,622,485.00"

      // Kita pecah saja seluruh karakter berdasar spasinya
      String[] tokens = remainingText.split("\\s+");

      // Kalau kata-katanya sangat sedikit, ini tidak normal, batalkan pembedahan di
      // helper ini
      if (tokens.length < 3) {
         builder.rawDescription = remainingText;
         return;
      }

      // 3 kata dari ujung belakang PASTI merupakan urutan (Debit - Kredit - Saldo)
      String tSaldo = tokens[tokens.length - 1]; // Ujung paling kanan (1,622,485.00)
      String tKredit = tokens[tokens.length - 2]; // Lebih ke kiri (0.00)
      String tDebit = tokens[tokens.length - 3]; // Lebih ke kiri (49,370.00)

      int descriptionEndIndex = tokens.length - 3;

      // Cek apakah token terakhir dari sisa deskripsi adalah TELLER ID (angka murni
      // 4-8 karakter)
      // Contoh: "8890405", "8888447", "0".
      if (descriptionEndIndex > 0 && tokens[descriptionEndIndex - 1].matches("^\\d{1,8}$")) {
         // Abaikan Teller ID ini agar tidak masuk ke Uraian Transaksi / Deskripsi
         descriptionEndIndex--;
      }

      // Sisanya (dari kata depan sampai sebelum kolom Teller/Debit) digabung lagi
      // menjadi
      // nama "Keterangan Deskripsi"
      StringBuilder descBuilder = new StringBuilder();
      for (int i = 0; i < descriptionEndIndex; i++) {
         descBuilder.append(tokens[i]).append(" ");
      }

      // Masukkan yang sudah bersih ke dalam object kerangka
      builder.rawDescription = descBuilder.toString().trim();
      builder.debitStr = tDebit;
      builder.kreditStr = tKredit;
      builder.saldoStr = tSaldo;
   }

   /**
    * Menyulap object Kerangka Sementara (String) menjadi Entity Database aseli
    * (BigDecimal dan LocalDate)
    */
   private BankTransaction finalizeTransaction(BankTransactionBuilder builder, MutationDocument doc,
         java.util.Map<String, Integer> hashCounters) {

      // Mengubah string nilai ("49,370.00") menjadi angka murni BigDecimal (49370.00)
      BigDecimal valDebit = parseRupiahStr(builder.debitStr);
      BigDecimal valKredit = parseRupiahStr(builder.kreditStr);
      BigDecimal valSaldo = parseRupiahStr(builder.saldoStr);

      MutationType finalType = MutationType.DB;
      BigDecimal finalAmount = BigDecimal.ZERO;

      // Analisa menentukan Mutasi Masuk atau Keluar (DB/CR)
      // Di log BRI: Jika DB bernilai lebih dari 0, berarti UANG KELUAR ->
      // MutationType.DB
      // Di log BRI: Jika CR/Kredit lebih dari 0, berarti UANG MASUK ->
      // MutationType.CR
      if (valKredit != null && valKredit.compareTo(BigDecimal.ZERO) > 0) {
         finalType = MutationType.CR;
         finalAmount = valKredit;
      } else if (valDebit != null && valDebit.compareTo(BigDecimal.ZERO) > 0) {
         finalType = MutationType.DB;
         finalAmount = valDebit;
      }

      // NORMALISASI MENGGUNAKAN SERVICE BARU
      String normalizedDesc = transactionRefinementService.normalizeDescription(builder.rawDescription);
      String cpName = transactionRefinementService.extractCounterpartyName("BRI", builder.rawDescription, finalType == MutationType.CR);
      TransactionCategory finalCategory = transactionRefinementService.categorizeTransaction(normalizedDesc, finalType == MutationType.CR);

      // Base string pembentuk Hash anti duplikasi
      String baseHashStr = builder.dateStr.toString() + "_" + finalAmount.toPlainString() + "_" + normalizedDesc;

      // Ambil urutan ke-berapa transaksi dengan hash persis sama ini muncul di
      // dokumen ini
      int occurrenceIndex = hashCounters.getOrDefault(baseHashStr, 0);
      hashCounters.put(baseHashStr, occurrenceIndex + 1);

      // Hash ditambahkan dengan angka occurrence agar transaksi identik dalam 1
      // dokumen punya hash unik
      String hashStr = baseHashStr + "_" + occurrenceIndex;
      String finalHash = generateMd5Hash(hashStr);

      // Buat objek entitas dan bungkus kembali...
      return BankTransaction.builder()
            .mutationDocument(doc)
            .bankAccount(doc.getBankAccount())
            .transactionDate(builder.dateStr)
            .rawDescription(builder.rawDescription)
            .normalizedDescription(normalizedDesc)
            .counterpartyName(cpName)
            .mutationType(finalType)
            .amount(finalAmount)
            .balance(valSaldo)
            .category(finalCategory) // Diset otomatis berdasarkan rule engine
            .isExcluded(false)
            .duplicateHash(finalHash)
            .build();
   }

   /**
    * Helper Untuk membersihkan Format Komputer "49,370.00" menuju pure Math
    * (49370.00) Java BigDecimal
    */
   private BigDecimal parseRupiahStr(String amountStr) {
      if (amountStr == null || amountStr.isEmpty() || amountStr.equals("-")) {
         return BigDecimal.ZERO;
      }
      try {
         // Hancurkan karakter komanya (hilangkan string tanda ribuan) tapi tetap biarkan
         // titik desimalnya
         String cleaned = amountStr.replace(",", "");
         return new BigDecimal(cleaned).setScale(4, RoundingMode.HALF_UP);
      } catch (NumberFormatException e) {
         return BigDecimal.ZERO;
      }
   }

   /**
    * Membentuk Hash Code Enkripsi 1 Arah.
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

   // Mini class penampung string mentah sebelum dibangun menjadi Entity
   private static class BankTransactionBuilder {
      LocalDate dateStr;
      String rawDescription = "";
      String debitStr = "";
      String kreditStr = "";
      String saldoStr = "";
   }
}