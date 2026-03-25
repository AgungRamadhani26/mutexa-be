package com.example.mutexa_be.service.parser.uob;

import com.example.mutexa_be.service.parser.PdfParserService;
import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.entity.enums.MutationType;
import com.example.mutexa_be.entity.enums.TransactionCategory;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class UobPdfParserService implements PdfParserService {

   // 1. Deteksi Statement Date (Awal Transaksi) - contoh: "03/11/2025"
   // Hanya tanggal tok, karena waktu transaksinya numpang di baris bawahnya
   private static final Pattern STATEMENT_DATE_PATTERN = Pattern.compile("^(\\d{2}/\\d{2}/\\d{4})$");

   // 2. Deteksi Transaction DateTime (Baris berikutnya) - contoh: "03/11/2025
   // 11:03:38"
   private static final Pattern TX_DATETIME_PATTERN = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}$");

   // 3. Deteksi baris penutup mutasi yang mengandung Deposit, Withdrawal, Saldo
   // (contoh: "35,000,000,000 0 25,686,151,736")
   // Group 1: Teks keterangan ekstra (kalo nyambung di baris yang sama)
   // Group 2: Deposit
   // Group 3: Withdrawal
   // Group 4: Ledger Balance
   private static final Pattern AMOUNTS_PATTERN = Pattern
         .compile("^(.*?)\\s*([\\d,\\.]+)\\s+([\\d,\\.]+)\\s+([\\d,\\.-]+)$");

   private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

   @Override
   public List<BankTransaction> parse(MutationDocument document, String filePath) {
      List<BankTransaction> transactions = new ArrayList<>();
      File file = new File(filePath);

      if (!file.exists()) {
         throw new IllegalArgumentException("File PDF UOB tidak ditemukan: " + filePath);
      }

      try (PDDocument pdfDocument = Loader.loadPDF(file)) {
         PDFTextStripper stripper = new PDFTextStripper();
         // Untuk UOB juga harus FALSE karena kalau true, kolom Description yang
         // panjang-panjang akan menimpa kolom sebelahnya
         stripper.setSortByPosition(false);

         String entireText = stripper.getText(pdfDocument);
         transactions = extractLinesAndBuildTransactions(document, entireText);

         log.info("Berhasil mem-parsing PDF UOB. Ditemukan {} buah transaksi.", transactions.size());

      } catch (Exception e) {
         log.error("Terjadi masalah saat parsing PDF UOB: {}", e.getMessage(), e);
         throw new RuntimeException("Gagal melakukan parsing dokumen PDF UOB.", e);
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

         // 0. Filter Baris Sampah (Header/Footer)
         if (line.isEmpty() || line.startsWith("Account Activities") || line.startsWith("Company / Account")
               || line.startsWith("Account Balance") || line.startsWith("Account Ledger Balance")
               || line.startsWith("Account Details") || line.startsWith("Account Transactions")
               || line.startsWith("Total Float") || line.startsWith("Account Type") || line.contains("Statement Date")
               || line.contains("records (Note:") || line.startsWith("Date of Export") || line.contains(" of ")
               || line.startsWith("Deposit Insurance Scheme") || line.startsWith("For Customer's Remarks")
               || line.startsWith("Deposits in the forms of saving")
               || line.startsWith("similar characteristic with maximum")
               || line.startsWith("agreed between Bank and Customer") || line.startsWith("s regulated by LPS")
               || line.startsWith("Total Deposits") || line.startsWith("Note") || line.startsWith("IDR ")
               || line.startsWith("Balances and details reflected") || line.startsWith("Your deposit")
               || line.startsWith("Information on the prevailing deposit")) {
            continue;
         }

         // 1. Deteksi Baris Tanggal Baru (Tanda Mulai Transaksi UOB)
         Matcher dateMatcher = STATEMENT_DATE_PATTERN.matcher(line);
         if (dateMatcher.matches()) {

            // Kalau transaksi sebelumnya udah punya saldo dsb, berarti bloknya selesai
            if (currentTxBuilder != null && currentTxBuilder.saldoStr != null && !currentTxBuilder.saldoStr.isEmpty()) {
               list.add(finalizeTransaction(currentTxBuilder, document, hashCounters));
            }

            currentTxBuilder = new BankTransactionBuilder();
            try {
               currentTxBuilder.dateStr = LocalDate.parse(dateMatcher.group(1), DATE_FORMATTER);
            } catch (DateTimeParseException e) {
               currentTxBuilder = null;
            }
            continue; // lanjut ke baris berikutnya
         }

         // 2. Kumpulkan isi transaksi
         if (currentTxBuilder != null) {

            // Abaikan baris Jam (seperti "03/11/2025 11:03:38" atau "AM"/"PM")
            if (TX_DATETIME_PATTERN.matcher(line).matches() || line.equals("AM") || line.equals("PM")) {
               continue;
            }

            // Cek penutup nominal saldo/debit/kredit
            // Hanya deteksi jika sebelumnya kita belum pernah nyatet nominal
            // Karena kadang deskripsi transaksi kebetulan angka doang jadi numbur
            if (currentTxBuilder.saldoStr != null && currentTxBuilder.saldoStr.isEmpty()) {
               Matcher amountMatcher = AMOUNTS_PATTERN.matcher(line);
               // Pastikan formatnya bener, minimal nol nya UOB di tengah
               if (amountMatcher.matches() && (line.contains(" 0 ") || line.startsWith("0 "))) {
                  String beforeNumbers = amountMatcher.group(1).trim();
                  if (!beforeNumbers.isEmpty()) {
                     currentTxBuilder.rawDescription += " " + beforeNumbers;
                  }

                  currentTxBuilder.depositStr = amountMatcher.group(2);
                  currentTxBuilder.withdrawStr = amountMatcher.group(3);
                  currentTxBuilder.saldoStr = amountMatcher.group(4);

                  // JANGAN di-null-in, biarin aja nampung deskripsi sampe ketemu tanggal baru!
                  continue;
               }
            }

            // Kalau bukan angka dan jam (atau sudah lewat baris angkanya),
            // berarti murni Deskripsi / Keterangan Transfer sisanya yg nyampur
            currentTxBuilder.rawDescription += " " + line;
         }
      }

      // Jaga-jaga kalau ada transaksi menggantung di akhir
      if (currentTxBuilder != null && currentTxBuilder.saldoStr != null && !currentTxBuilder.saldoStr.isEmpty()) {
         list.add(finalizeTransaction(currentTxBuilder, document, hashCounters));
      }

      return list;
   }

   private BankTransaction finalizeTransaction(BankTransactionBuilder builder, MutationDocument doc,
         java.util.Map<String, Integer> hashCounters) {

      BigDecimal valDeposit = parseRupiahStr(builder.depositStr);
      BigDecimal valWithdraw = parseRupiahStr(builder.withdrawStr);
      BigDecimal valSaldo = parseRupiahStr(builder.saldoStr);

      MutationType finalType = MutationType.DB;
      BigDecimal finalAmount = BigDecimal.ZERO;

      // Di UOB: 'Deposit' berarti uang kita bertambah (Credit)
      // Di UOB: 'Withdrawal' berarti penarikan uang (Debit)
      if (valDeposit != null && valDeposit.compareTo(BigDecimal.ZERO) > 0) {
         finalType = MutationType.CR;
         finalAmount = valDeposit;
      } else if (valWithdraw != null && valWithdraw.compareTo(BigDecimal.ZERO) > 0) {
         finalType = MutationType.DB;
         finalAmount = valWithdraw;
      }

      String normalizedDesc = builder.rawDescription.trim().replaceAll("\\s+", " ");

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
            .mutationType(finalType)
            .amount(finalAmount)
            .balance(valSaldo)
            .category(TransactionCategory.UNCLASSIFIED)
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
      String depositStr = "";
      String withdrawStr = "";
      String saldoStr = "";
   }
}