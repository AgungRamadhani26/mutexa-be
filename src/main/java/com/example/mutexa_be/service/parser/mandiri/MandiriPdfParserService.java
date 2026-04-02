package com.example.mutexa_be.service.parser.mandiri;

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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Service
public class MandiriPdfParserService implements PdfParserService {

   private final TransactionRefinementService transactionRefinementService;

   // Mendeteksi Tanggal Awal Transaksi Mandiri (contoh: "31 Dec 2025,")
   private static final Pattern DATE_PATTERN = Pattern.compile("^(\\d{2}\\s+[A-Za-z]{3}\\s+\\d{4}),?$");

   // Mendeteksi Jam (contoh: "14:11:24" atau "23:59:00 Biaya Adm 18203")
   private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}\\s*(.*)$");

   // Mendeteksi 3 Kolom Nilai Uang di Mandiri: [Debit] [Credit] [Balance]
   private static final Pattern AMOUNT_PATTERN = Pattern
         .compile("^(.*?)\\s*([\\d,]+\\.\\d{2})\\s+([\\d,]+\\.\\d{2})\\s+([\\d,]+\\.\\d{2})$");

   // Formatter untuk mengubah "01 Dec 2025" menjadi LocalDate
   private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

   @Override
   public List<BankTransaction> parse(MutationDocument document, String filePath) {
      List<BankTransaction> transactions = new ArrayList<>();
      File file = new File(filePath);

      if (!file.exists()) {
         throw new IllegalArgumentException("File PDF Mandiri tidak ditemukan: " + filePath);
      }

      try (PDDocument pdfDocument = Loader.loadPDF(file)) {
         PDFTextStripper stripper = new PDFTextStripper();

         // SANGAT PENTING UNTUK MANDIRI KOPRA: false agar urutan paragraf terjaga dengan
         // sempurna
         stripper.setSortByPosition(false);

         String entireText = stripper.getText(pdfDocument);
         transactions = extractLinesAndBuildTransactions(document, entireText);

         log.info("Berhasil mem-parsing PDF Mandiri. Ditemukan {} buah transaksi.", transactions.size());

      } catch (Exception e) {
         log.error("Terjadi masalah saat parsing PDF Mandiri: {}", e.getMessage(), e);
         throw new RuntimeException("Gagal melakukan parsing dokumen PDF Mandiri.", e);
      }

      return transactions;
   }

   private List<BankTransaction> extractLinesAndBuildTransactions(MutationDocument document, String entireText) {
      List<BankTransaction> list = new ArrayList<>();
      java.util.Map<String, Integer> hashCounters = new java.util.HashMap<>();

      String[] lines = entireText.split("\\r?\\n");
      BankTransactionBuilder currentTxBuilder = null;

      for (String line : lines) {
         line = line.trim();

         // Hiraukan baris kosong atau footer/header page PDF
         if (line.isEmpty() || line.startsWith("Account Statement") || line.startsWith("Created ")
               || line.startsWith("Account No.") || line.startsWith("Period ") || line.startsWith("Opening Balance")
               || line.startsWith("Closing Balance") || line.contains("Page ")
               || line.contains("koprabymandiri.com") || line.contains("CreditReference No")
               || line.startsWith("IDR") || line.contains("For further questions")) {
            continue;
         }

         // 1. Cek apakah ini awal transaksi baru (Tanggal)
         Matcher dateMatcher = DATE_PATTERN.matcher(line);
         if (dateMatcher.matches()) {
            String dateStr = dateMatcher.group(1);

            currentTxBuilder = new BankTransactionBuilder();
            try {
               currentTxBuilder.dateStr = LocalDate.parse(dateStr, DATE_FORMATTER);
            } catch (DateTimeParseException e) {
               log.warn("Gagal parse tanggal Mandiri: {}", dateStr);
               currentTxBuilder = null;
            }
            continue;
         }

         // Jika kita sedang berada di dalam transaksi
         if (currentTxBuilder != null) {

            // 2. Cek apakah ini line amounts (Penutup transaksi form Mandiri)
            Matcher amountMatcher = AMOUNT_PATTERN.matcher(line);
            if (amountMatcher.matches()) {
               String beforeNumbers = amountMatcher.group(1).trim();
               if (!beforeNumbers.isEmpty() && !beforeNumbers.equals("-")) {
                  currentTxBuilder.rawDescription += " " + beforeNumbers.replace("-", "").trim();
               }

               currentTxBuilder.debitStr = amountMatcher.group(2);
               currentTxBuilder.kreditStr = amountMatcher.group(3);
               currentTxBuilder.saldoStr = amountMatcher.group(4);

               // Transaksi selesai diproses per barisan ini, masukkan ke List
               list.add(finalizeTransaction(currentTxBuilder, document, hashCounters));
               currentTxBuilder = null; // Reset
               continue;
            }

            // 3. Cek apakah ini baris waktu (Jam) - kadang bercampur dengan teks setelahnya
            Matcher timeMatcher = TIME_PATTERN.matcher(line);
            if (timeMatcher.matches()) {
               String remainingDesc = timeMatcher.group(1).trim();
               if (!remainingDesc.isEmpty() && !remainingDesc.equals("-")) {
                  currentTxBuilder.rawDescription += " " + remainingDesc;
               }
               continue;
            }

            // 4. Baris Sisanya adalah Description
            if (!line.equals("-")) {
               currentTxBuilder.rawDescription += " " + line;
            }
         }
      }

      return list;
   }

   private BankTransaction finalizeTransaction(BankTransactionBuilder builder, MutationDocument doc,
         java.util.Map<String, Integer> hashCounters) {
      BigDecimal valDebit = parseRupiahStr(builder.debitStr);
      BigDecimal valKredit = parseRupiahStr(builder.kreditStr);
      BigDecimal valSaldo = parseRupiahStr(builder.saldoStr);

      MutationType finalType = MutationType.DB;
      BigDecimal finalAmount = BigDecimal.ZERO;

      if (valKredit != null && valKredit.compareTo(BigDecimal.ZERO) > 0) {
         finalType = MutationType.CR;
         finalAmount = valKredit;
      } else if (valDebit != null && valDebit.compareTo(BigDecimal.ZERO) > 0) {
         finalType = MutationType.DB;
         finalAmount = valDebit;
      }

      String normalizedDesc = transactionRefinementService.normalizeDescription(builder.rawDescription);
      String cpName = transactionRefinementService.extractCounterpartyName(builder.rawDescription);
      TransactionCategory finalCategory = transactionRefinementService.categorizeTransaction(normalizedDesc, finalType == MutationType.CR);

      String baseHashStr = builder.dateStr.toString() + "_" + finalAmount.toPlainString() + "_" + normalizedDesc;
      int occurrenceIndex = hashCounters.getOrDefault(baseHashStr, 0);
      hashCounters.put(baseHashStr, occurrenceIndex + 1);

      String hashStr = baseHashStr + "_" + occurrenceIndex;
      String finalHash = generateMd5Hash(hashStr);

      return BankTransaction.builder()
            .mutationDocument(doc)
            .bankAccount(doc.getBankAccount())
            .transactionDate(builder.dateStr)
            .rawDescription(normalizedDesc)
            .normalizedDescription(normalizedDesc)
            .counterpartyName(cpName)
            .mutationType(finalType)
            .amount(finalAmount)
            .balance(valSaldo)
            .category(finalCategory) // diset otomatis dari RefinementService
            .isExcluded(false)
            .duplicateHash(finalHash)
            .build();
   }

   private BigDecimal parseRupiahStr(String amountStr) {
      if (amountStr == null || amountStr.trim().isEmpty() || amountStr.equals("-")) {
         return BigDecimal.ZERO;
      }
      try {
         String cleaned = amountStr.replace(",", "");
         return new BigDecimal(cleaned).setScale(4, RoundingMode.HALF_UP);
      } catch (NumberFormatException e) {
         return BigDecimal.ZERO;
      }
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
         throw new RuntimeException("MD5 algorithm missing", e);
      }
   }

   private static class BankTransactionBuilder {
      LocalDate dateStr;
      String rawDescription = "";
      String debitStr = "";
      String kreditStr = "";
      String saldoStr = "";
   }
}