package com.example.mutexa_be.service.parser.bca;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.entity.enums.MutationType;
import com.example.mutexa_be.entity.enums.TransactionCategory;
import com.example.mutexa_be.service.TransactionRefinementService;
import com.example.mutexa_be.service.parser.PdfParserService;
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
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Service
public class BcaPdfParserService implements PdfParserService {

    private final TransactionRefinementService transactionRefinementService;

    // Pattern untuk deteksi tahun dari header "PERIODE : JANUARI 2025"
    private static final Pattern PERIOD_PATTERN = Pattern.compile("PERIODE\\s*:\\s*(\\w+)\\s+(\\d{4})");

    // Pattern untuk deteksi Saldo Awal "01/01 SALDO AWAL -1,944,261,238.19"
    private static final Pattern INITIAL_BALANCE_PATTERN = Pattern.compile("^(\\d{2}/\\d{2})\\s+SALDO AWAL\\s+(-?[\\d,.]+)$");

    // Pattern untuk baris transaksi utama
    // Group 1: Date (01/01)
    // Group 2: Description + partial amount
    // Group 3: Amount (1.234.567.00)
    // Group 4: DB indicator (optional)
    // Group 5: Balance (optional)
    private static final Pattern TRANSACTION_PATTERN = Pattern.compile("^(\\d{2}/\\d{2})\\s+(.*?)\\s+([\\d,]+\\.\\d{2})\\s*(DB)?\\s*(-?[\\d,]+\\.[\\d]{2})?$");

    @Override
    public List<BankTransaction> parse(MutationDocument document, String filePath) {
        List<BankTransaction> transactions = new ArrayList<>();
        File file = new File(filePath);

        if (!file.exists()) {
            throw new IllegalArgumentException("File PDF BCA tidak ditemukan: " + filePath);
        }

        try (PDDocument pdfDocument = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true); // Sangat penting untuk BCA agar kolom sejajar
            String entireText = stripper.getText(pdfDocument);
            
            transactions = extractTransactions(document, entireText);
            log.info("Berhasil mem-parsing PDF BCA. Ditemukan {} buah transaksi.", transactions.size());

        } catch (Exception e) {
            log.error("Terjadi masalah saat parsing PDF BCA: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal melakukan parsing dokumen PDF BCA.", e);
        }

        return transactions;
    }

    private List<BankTransaction> extractTransactions(MutationDocument document, String entireText) {
        List<BankTransaction> results = new ArrayList<>();
        String[] lines = entireText.split("\\r?\\n");
        
        int currentYear = LocalDate.now().getYear();
        BigDecimal runningBalance = BigDecimal.ZERO;
        boolean initialBalanceFound = false;
        
        Map<String, Integer> hashCounters = new HashMap<>();
        BcaTransactionBuilder currentBuilder = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 1. Deteksi Periode/Tahun
            Matcher periodMatcher = PERIOD_PATTERN.matcher(line);
            if (periodMatcher.find()) {
                currentYear = Integer.parseInt(periodMatcher.group(2));
                continue;
            }

            // 2. Deteksi Saldo Awal
            Matcher initialMatcher = INITIAL_BALANCE_PATTERN.matcher(line);
            if (initialMatcher.matches()) {
                runningBalance = parseAmount(initialMatcher.group(2));
                initialBalanceFound = true;
                log.info("Ditemukan Saldo Awal: {}", runningBalance);
                continue;
            }

            // 3. Deteksi Baris Mutasi Baru
            Matcher txMatcher = TRANSACTION_PATTERN.matcher(line);
            if (txMatcher.matches()) {
                // Jika ada transaksi sebelumnya yang belum di-finalize (multi-line desc)
                if (currentBuilder != null) {
                    processAndAdd(results, currentBuilder, document, runningBalance, hashCounters);
                    runningBalance = results.get(results.size() - 1).getBalance();
                }

                currentBuilder = new BcaTransactionBuilder();
                String dateStr = txMatcher.group(1);
                currentBuilder.date = parseDate(dateStr, currentYear);
                currentBuilder.description = txMatcher.group(2);
                currentBuilder.amount = parseAmount(txMatcher.group(3));
                currentBuilder.type = "DB".equals(txMatcher.group(4)) ? MutationType.DB : MutationType.CR;
                
                String balanceGroup = txMatcher.group(5);
                if (balanceGroup != null && !balanceGroup.trim().isEmpty()) {
                    currentBuilder.explicitBalance = parseAmount(balanceGroup);
                }
                
                continue;
            }

            // 4. Kumpulkan deskripsi tambahan (multi-line)
            if (currentBuilder != null) {
                // Abaikan footer/header yang terselip
                String upperLine = line.toUpperCase();
                if (upperLine.contains("BERSAMBUNG KE HALAMAN BERIKUT") || 
                    upperLine.contains("HALAMAN :") || 
                    upperLine.contains("TANGGAL KETERANGAN") ||
                    upperLine.contains("REKENING GIRO") ||
                    upperLine.contains("NO. REKENING :") ||
                    upperLine.contains("PERIODE :") ||
                    upperLine.contains("MATA UANG :") ||
                    upperLine.contains("CITRA 1 BLOK G") || // Lokasi spesifik user
                    upperLine.contains("JAKARTA 11840") ||
                    upperLine.contains("INDONESIA") ||
                    upperLine.contains("C A T A T A N :") ||
                    upperLine.contains("SALDO AWAL :") ||
                    upperLine.contains("MUTASI CR :") ||
                    upperLine.contains("MUTASI DB :") ||
                    upperLine.contains("SALDO AKHIR :")) {
                    continue;
                }
                currentBuilder.description += " " + line;
            }
        }

        // Finalize transaksi terakhir
        if (currentBuilder != null) {
            processAndAdd(results, currentBuilder, document, runningBalance, hashCounters);
        }

        return results;
    }

    private void processAndAdd(List<BankTransaction> results, BcaTransactionBuilder builder, MutationDocument doc, BigDecimal prevBalance, Map<String, Integer> hashCounters) {
        BigDecimal finalBalance;
        if (builder.explicitBalance != null) {
            finalBalance = builder.explicitBalance;
        } else {
            // Hitung running balance jika di PDF kosong
            if (builder.type == MutationType.CR) {
                finalBalance = prevBalance.add(builder.amount);
            } else {
                finalBalance = prevBalance.subtract(builder.amount);
            }
        }

        String rawDesc = builder.description.trim();
        String normalizedDesc = transactionRefinementService.normalizeDescription(rawDesc);
        String cpName = transactionRefinementService.extractCounterpartyName("BCA", rawDesc);
        
        // Safety guard: Truncate counterpartyName to 255 chars (DB limit)
        if (cpName != null && cpName.length() > 255) {
            cpName = cpName.substring(0, 252) + "...";
        }

        TransactionCategory category = transactionRefinementService.categorizeTransaction(normalizedDesc, builder.type == MutationType.CR);

        // Anti-duplicate hash
        String baseHash = builder.date.toString() + "_" + builder.amount.toPlainString() + "_" + normalizedDesc;
        int count = hashCounters.getOrDefault(baseHash, 0);
        hashCounters.put(baseHash, count + 1);
        String finalHash = generateMd5Hash(baseHash + "_" + count);

        results.add(BankTransaction.builder()
                .mutationDocument(doc)
                .bankAccount(doc.getBankAccount())
                .transactionDate(builder.date)
                .rawDescription(rawDesc)
                .normalizedDescription(normalizedDesc)
                .counterpartyName(cpName)
                .mutationType(builder.type)
                .amount(builder.amount)
                .balance(finalBalance)
                .category(category)
                .isExcluded(false)
                .duplicateHash(finalHash)
                .build());
    }

    private LocalDate parseDate(String dateStr, int year) {
        String[] parts = dateStr.split("/");
        int day = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        return LocalDate.of(year, month, day);
    }

    private BigDecimal parseAmount(String amountStr) {
        if (amountStr == null) return BigDecimal.ZERO;
        String cleaned = amountStr.replace(",", "");
        return new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
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
            throw new RuntimeException(e);
        }
    }

    private static class BcaTransactionBuilder {
        LocalDate date;
        String description = "";
        BigDecimal amount;
        MutationType type;
        BigDecimal explicitBalance;
    }
}
