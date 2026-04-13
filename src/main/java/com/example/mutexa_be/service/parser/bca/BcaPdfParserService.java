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

    @Override
    public String getBankName() { return "BCA"; }

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
    // Pattern untuk baris transaksi utama: Tanggal Keterangan (CBG-Opsional) Mutasi [Type] [Saldo-Opsional]
    private static final Pattern TRANSACTION_PATTERN = Pattern.compile("^(\\d{2}/\\d{2})\\s+(.*?)(?:\\s+(\\d{4}))?\\s+([\\d,]+\\.\\d{2})\\s*(DB)?\\s*(-?[\\d,]+\\.[\\d]{2})?$");


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
        List<BcaTransactionBuilder> builders = new ArrayList<>();
        String[] lines = entireText.split("\\r?\\n");
        
        int currentYear = LocalDate.now().getYear();
        BigDecimal runningBalance = BigDecimal.ZERO;
        
        Map<String, Integer> hashCounters = new HashMap<>();
        BcaTransactionBuilder currentBuilder = null;
        boolean skipMode = false;
        int lastParsedMonth = -1; // -1 berarti belum mulai

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            String upperLine = line.toUpperCase();

            // 1. Deteksi Transisi Halaman (Header/Footer sampah)
            if (upperLine.contains("BERSAMBUNG KE HALAMAN") || upperLine.contains("HALAMAN :")) {
                skipMode = true;
                continue;
            }

            // Batas akhir dari header sampah di semua halaman BCA adalah baris Keterangan Mutasi
            if (upperLine.contains("TANGGAL KETERANGAN") || (upperLine.contains("TANGGAL") && upperLine.contains("KETERANGAN"))) {
                skipMode = false;
                continue;
            }

            // 2. Deteksi Periode/Tahun WALAUPUN sedang di skipMode (Karena periode ada di dalam header)
            Matcher periodMatcher = PERIOD_PATTERN.matcher(line);
            if (periodMatcher.find()) {
                int extractedYear = Integer.parseInt(periodMatcher.group(2));
                if (lastParsedMonth == -1 || extractedYear > currentYear) {
                    currentYear = extractedYear;
                }
                continue;
            }

            // Jika sedang dalam transisi (misal nama kota, jalan, kode pos), abaikan sisanya
            if (skipMode) {
                continue;
            }

            // 3. Deteksi Saldo Awal
            Matcher initialMatcher = INITIAL_BALANCE_PATTERN.matcher(line);
            if (initialMatcher.matches()) {
                runningBalance = parseAmount(initialMatcher.group(2));
                log.info("Ditemukan Saldo Awal: {}", runningBalance);
                continue;
            }

            // 4. Deteksi Baris Mutasi Baru
            Matcher txMatcher = TRANSACTION_PATTERN.matcher(line);
            if (txMatcher.matches()) {
                if (currentBuilder != null) {
                    builders.add(currentBuilder);
                }

                currentBuilder = new BcaTransactionBuilder();
                String dateStr = txMatcher.group(1); // Format: "DD/MM"
                
                int parsedDate = Integer.parseInt(dateStr.split("/")[0]);
                int parsedMonth = Integer.parseInt(dateStr.split("/")[1]);

                // Logika Perpindahan Tahun (Year Rollover Detection)
                // Jika dari Desember (12) tiba-tiba lompat ke Januari (01), berarti Ganti Tahun Baru!
                if (lastParsedMonth != -1) {
                    if (lastParsedMonth == 12 && parsedMonth == 1) {
                        currentYear++;
                        log.info("Tahun otomatis dikalibrasi maju ke: {} karena transisi Desember-Januari", currentYear);
                    } else if (lastParsedMonth == 1 && parsedMonth == 12) {
                        // Antisipasi jika data berjalan terbalik namun ini jarang
                        currentYear--;
                    }
                }
                lastParsedMonth = parsedMonth;

                currentBuilder.date = LocalDate.of(currentYear, parsedMonth, parsedDate);
                currentBuilder.description = txMatcher.group(2).trim();
                currentBuilder.amount = parseAmount(txMatcher.group(4));
                currentBuilder.type = "DB".equals(txMatcher.group(5)) ? MutationType.DB : MutationType.CR;
                
                String balanceGroup = txMatcher.group(6);
                if (balanceGroup != null && !balanceGroup.trim().isEmpty()) {
                    currentBuilder.explicitBalance = parseAmount(balanceGroup);
                }
                
                continue;
            }

            // 5. Kumpulkan deskripsi tambahan (multi-line)
            if (currentBuilder != null) {
                // Abaikan footer spesifik internal mutasi
                if (upperLine.contains("SALDO AWAL :") ||
                    upperLine.contains("MUTASI CR :") ||
                    upperLine.contains("MUTASI DB :") ||
                    upperLine.contains("SALDO AKHIR :") ||
                    upperLine.contains("C A T A T A N :") ||
                    upperLine.contains("REKENING GIRO")) {
                    continue;
                }

                // Abaikan jika baris hanya berisi kode cabang (4 digit angka)
                if (line.matches("^\\d{4}$")) {
                    continue;
                }

                // Bersihkan kode cabang jika terlampir di ujung baris deskripsi tambahan
                // Misal: "PEMBAYARAN PINJ... 0003" -> "PEMBAYARAN PINJ..."
                // Kita pertahankan 3 digit karena itu bisa jadi kode bank di BI-FAST
                String cleanedLine = line.replaceAll("\\s+\\d{4}$", "").trim();
                if (!cleanedLine.isEmpty()) {
                    currentBuilder.description += " " + cleanedLine;
                }
            }
        }

        if (currentBuilder != null) {
            builders.add(currentBuilder);
        }

        // 5. Sweep Algoritma Perhitungan Saldo Mundur (Reverse Back-Tracing)
        // Kita jalan mundur dari akhir ke awal
        BigDecimal knownFutureBalance = null;
        for (int i = builders.size() - 1; i >= 0; i--) {
            BcaTransactionBuilder b = builders.get(i);
            
            // Jika BCA memberi explicit balance pada baris ini, jadikan patokan!
            if (b.explicitBalance != null) {
                b.finalBalance = b.explicitBalance;
                knownFutureBalance = b.explicitBalance;
            } else {
                // Jika kosong, hitung MUNDUR dari transaksi di bawahnya (yaitu knownFutureBalance)
                if (knownFutureBalance != null) {
                    BcaTransactionBuilder nextTx = builders.get(i + 1);
                    // Logika Mundur: Jika nextTx adalah Debit (uang keluar), berarti sebelum nextTx saldonya LEBIH BESAR (ditambah)
                    if (nextTx.type == MutationType.DB) {
                        knownFutureBalance = knownFutureBalance.add(nextTx.amount);
                    } else {
                        // Jika nextTx Kredit uang masuk, berarti sebelumnya saldonya LEBIH KECIL (dikurang)
                        knownFutureBalance = knownFutureBalance.subtract(nextTx.amount);
                    }
                    b.finalBalance = knownFutureBalance;
                } else {
                   // Fallback darurat jika baris paling ujung PDF terpotong dan tdk punya explicit balance
                   b.finalBalance = BigDecimal.ZERO; 
                }
            }
        }

        // 6. Map to Entitas Database
        for (BcaTransactionBuilder b : builders) {
            String rawDesc = refineBcaDescription(b.description);
            String normalizedDesc = transactionRefinementService.normalizeDescription(rawDesc);
            String cpName = transactionRefinementService.extractCounterpartyName("BCA", rawDesc, b.type == MutationType.CR);
            
            if (cpName != null && cpName.length() > 255) {
                cpName = cpName.substring(0, 252) + "...";
            }

            TransactionCategory category = transactionRefinementService.categorizeTransaction(normalizedDesc, b.type == MutationType.CR);

            String baseHash = b.date.toString() + "_" + b.amount.toPlainString() + "_" + normalizedDesc;
            int count = hashCounters.getOrDefault(baseHash, 0);
            hashCounters.put(baseHash, count + 1);
            String finalHash = generateMd5Hash(baseHash + "_" + count);

            results.add(BankTransaction.builder()
                    .mutationDocument(document)
                    .bankAccount(document.getBankAccount())
                    .transactionDate(b.date)
                    .rawDescription(rawDesc)
                    .normalizedDescription(normalizedDesc)
                    .counterpartyName(cpName)
                    .mutationType(b.type)
                    .amount(b.amount)
                    .balance(b.finalBalance)
                    .category(category)
                    .isExcluded(false)
                    .duplicateHash(finalHash)
                    .build());
        }

        return results;
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

    private String refineBcaDescription(String desc) {
        if (desc == null) return "";
        
        // 1. Reordering BI-FAST (L1 R1 L2 R2 -> L1 L2 R1 R2)
        // Pola: BI-FAST [CR/DB] [BIF...] TANGGAL :[DD/MM]
        // Seharusnya: BI-FAST [CR/DB] TANGGAL :[DD/MM] [BIF...]
        if (desc.contains("BI-FAST")) {
            desc = desc.replaceAll(
                "(BI-FAST (?:CR|DB))\\s+(BIF\\s+.*?)\\s+(TANGGAL\\s*:\\s*\\d{2}/\\d{2})",
                "$1 $3 $2"
            );
        }
        
        // 2. Normalisasi Spasi Berlebih
        return desc.replaceAll("\\s+", " ").trim();
    }

    private static class BcaTransactionBuilder {
        LocalDate date;
        String description = "";
        BigDecimal amount;
        MutationType type;
        BigDecimal explicitBalance;
        BigDecimal finalBalance;
    }
}
