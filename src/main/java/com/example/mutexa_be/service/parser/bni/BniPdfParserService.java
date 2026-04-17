package com.example.mutexa_be.service.parser.bni;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Service
public class BniPdfParserService implements PdfParserService {

    private final TransactionRefinementService transactionRefinementService;

    @Override
    public String getBankName() {
        return "BNI";
    }

    // =======================================================================
    // Pola Regex Transaksi Utama BNI (Core Row Pattern)
    // -----------------------------------------------------------------------
    // Keunikan PDF BNI saat dibaca Horizontal:
    // Kolom-kolom akan tergabung menjadi format yang presisi dalam satu baris.
    // Contoh riil: "1 01/08/2025 DIVISI 959065 TRF/PAY/TOP-UP ECHANNEL | PEMINDAHAN
    // KE 1,000,000.00 D 3,507,992.00"
    // Regex di bawah ini secara presisi menangkap No, Tanggal, Deskripsi, Jumlah,
    // Tipe (D/C), dan Saldo.
    // =======================================================================
    private static final String CORE_ROW_PATTERN = "^(?<no>\\d+)\\s+(?<date>\\d{2}/\\d{2}/\\d{4})\\s+(?<desc>.*?)\\s+(?<amount>\\d{1,3}(?:,\\d{3})*\\.\\d{2})\\s+(?<type>[DC])\\s+(?<balance>\\d{1,3}(?:,\\d{3})*\\.\\d{2})$";
    private static final Pattern coreRowRegex = Pattern.compile(CORE_ROW_PATTERN);

    // Time pattern that appears at the start of continuation lines
    private static final String TIME_PREFIX_PATTERN = "^\\d{2}\\.\\d{2}\\.\\d{2}\\s+";
    private static final Pattern timePrefixRegex = Pattern.compile(TIME_PREFIX_PATTERN);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public List<BankTransaction> parse(MutationDocument document, String filePath) {
        List<BankTransaction> transactions = new ArrayList<>();
        File file = new File(filePath);

        if (!file.exists()) {
            throw new IllegalArgumentException("File PDF tidak ditemukan di lokasi: " + filePath);
        }

        PDDocument pdfDocument = null;
        try {
            try {
                // PDFBox v3: Loader.loadPDF without password argument
                pdfDocument = Loader.loadPDF(file);
            } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
                // File dilindungi password
                throw new IllegalArgumentException(
                        "File PDF BNI dilindungi kata sandi/password. Harap hilangkan sandi terlebih dahulu sebelum mengunggah.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true); // Consolidates left-to-right to perfect horizontal rows

            String entireText = stripper.getText(pdfDocument);
            transactions = extractLinesAndBuildTransactions(document, entireText);

            log.info("Berhasil mem-parsing PDF BNI. Ditemukan {} buah transaksi.", transactions.size());

        } catch (IllegalArgumentException e) {
            throw e; // Rethrow password indicator cleanly
        } catch (Exception e) {
            log.error("Terjadi masalah saat parsing PDF BNI: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal melakukan parsing dokumen PDF BNI.", e);
        } finally {
            if (pdfDocument != null) {
                try {
                    pdfDocument.close();
                } catch (java.io.IOException e) {
                    log.error("Gagal menutup dokumen PDF BNI", e);
                }
            }
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
            if (line.isEmpty())
                continue;

            // 1. MATCH CORE ROW
            Matcher coreMatcher = coreRowRegex.matcher(line);
            if (coreMatcher.matches()) {
                // If previous tx wasn't finalized, finalize it now
                if (currentTxBuilder != null) {
                    list.add(finalizeTransaction(currentTxBuilder, document, hashCounters));
                }

                currentTxBuilder = new BankTransactionBuilder();
                try {
                    currentTxBuilder.dateStr = LocalDate.parse(coreMatcher.group("date"), DATE_FORMATTER);
                } catch (DateTimeParseException e) {
                    log.warn("Gagal parse tanggal BNI: {}, dilewati.", coreMatcher.group("date"));
                    currentTxBuilder = null;
                    continue;
                }

                currentTxBuilder.rawDescription = coreMatcher.group("desc");
                currentTxBuilder.amountStr = coreMatcher.group("amount");
                currentTxBuilder.typeStr = coreMatcher.group("type");
                currentTxBuilder.balanceStr = coreMatcher.group("balance");
                continue;
            }

            // If we are not currently building a transaction (e.g. header/footer), ignore
            // the line
            if (currentTxBuilder == null) {
                continue;
            }

            // ---------------------------------------------------------
            // Cek Batas Akhir Transaksi (Footer)
            // ---------------------------------------------------------
            // Digunakan untuk mengeksekusi penghentian pencarian deskripsi
            // di transaksi terakhir jika tabel bank sudah menyentuh rekap Saldo/Total
            String lowerLine = line.toLowerCase();
            if (lowerLine.startsWith("total debit")
                    || lowerLine.startsWith("total credit")
                    || lowerLine.contains("beginning balance")
                    || lowerLine.contains("post date branch")
                    || lowerLine.contains("transaction inquiry")) {
                list.add(finalizeTransaction(currentTxBuilder, document, hashCounters));
                currentTxBuilder = null;
                continue;
            }

            // 2. CONTINUATION LINE (Description part)
            // Sometimes continuation lines start with time "11.57.45", strip it.
            Matcher timeMatcher = timePrefixRegex.matcher(line);
            if (timeMatcher.find()) {
                line = line.substring(timeMatcher.end());
            }

            // Special case for time line alone "00.00.00"
            if (line.matches("^\\d{2}\\.\\d{2}\\.\\d{2}$")) {
                continue;
            }

            if (!line.isEmpty()) {
                currentTxBuilder.rawDescription += " " + line;
            }
        }

        // Catch the very last transaction in case PDF ends abruptly without footer
        if (currentTxBuilder != null) {
            list.add(finalizeTransaction(currentTxBuilder, document, hashCounters));
        }

        return list;
    }

    private BankTransaction finalizeTransaction(BankTransactionBuilder builder, MutationDocument doc,
            java.util.Map<String, Integer> hashCounters) {
        BigDecimal valAmount = parseRupiahStr(builder.amountStr);
        BigDecimal valBalance = parseRupiahStr(builder.balanceStr);
        MutationType finalType = "D".equalsIgnoreCase(builder.typeStr) ? MutationType.DB : MutationType.CR;

        // Normalization
        String normalizedDesc = transactionRefinementService.normalizeDescription(builder.rawDescription);
        String cpName = transactionRefinementService.extractCounterpartyName("BNI", builder.rawDescription,
                finalType == MutationType.CR);
        TransactionCategory finalCategory = transactionRefinementService.categorizeTransaction(normalizedDesc,
                finalType == MutationType.CR);

        // Deduplication Hash
        String baseHashStr = builder.dateStr.toString() + "_" + valAmount.toPlainString() + "_" + normalizedDesc;
        int occurrenceIndex = hashCounters.getOrDefault(baseHashStr, 0);
        hashCounters.put(baseHashStr, occurrenceIndex + 1);
        String hashStr = baseHashStr + "_" + occurrenceIndex;
        String finalHash = generateMd5Hash(hashStr);

        return BankTransaction.builder()
                .mutationDocument(doc)
                .bankAccount(doc.getBankAccount())
                .transactionDate(builder.dateStr)
                .rawDescription(builder.rawDescription.trim())
                .normalizedDescription(normalizedDesc)
                .counterpartyName(cpName)
                .mutationType(finalType)
                .amount(valAmount)
                .balance(valBalance)
                .category(finalCategory)
                .isExcluded(false)
                .duplicateHash(finalHash)
                .build();
    }

    private BigDecimal parseRupiahStr(String amountStr) {
        if (amountStr == null || amountStr.isEmpty() || amountStr.equals("-")) {
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
        String amountStr = "";
        String typeStr = "";
        String balanceStr = "";
    }
}
