package com.example.mutexa_be.service.parser.mandiri;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    @Override
    public String getBankName() {
        return "MANDIRI";
    }

    // This pattern identifies the "core" line which contains the amount and balance
    private static final Pattern CORE_TX_PATTERN = Pattern.compile("^(.*?)\\s+([+-][\\d,.]+,\\d{2})\\s+([\\d,.]+,\\d{2})$");
    private static final Pattern FLEX_CORE_PATTERN = Pattern.compile("([+-][\\d,.]+,\\d{2})\\s+([\\d,.]+,\\d{2})$");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2}\\s+[A-Z][a-z]{2}\\s+[\\d\\s]{2,6})");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    @Override
    public List<BankTransaction> parse(MutationDocument document, String filePath) {
        List<BankTransaction> transactions = new ArrayList<>();
        File file = new File(filePath);

        if (!file.exists()) {
            throw new IllegalArgumentException("File PDF Mandiri tidak ditemukan: " + filePath);
        }

        try (PDDocument pdfDocument = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

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
        List<Integer> txLineIndices = new ArrayList<>();
        
        // This pattern handles the case where Amount is at the end, possibly preceded by Balance and Entry Number
        // Example from debug: "115.120.997,101 +100.000.000,00"
        // Regex: (.*?) [\d,.]+(?:,\d{2})[\d]+ [+-][\d,.]+(?:,\d{2})
        // But if sortByPosition(true) works, it might be back to:
        // [Entry] [Date] [Desc] [Amount] [Balance]
        // Let's use a very flexible regex for the "Main Line"
        final Pattern FLEX_CORE_PATTERN = Pattern.compile("([+-][\\d,.]+,\\d{2})\\s+([\\d,.]+,\\d{2})$");

        // Phase 1: Identify all line indices that are core transaction lines
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (FLEX_CORE_PATTERN.matcher(line).find()) {
                txLineIndices.add(i);
            }
        }

        // Phase 2: Build transactions by collecting surrounding remarks
        for (int i = 0; i < txLineIndices.size(); i++) {
            int currentIdx = txLineIndices.get(i);
            int prevIdx = (i == 0) ? -1 : txLineIndices.get(i - 1);
            int nextIdx = (i == txLineIndices.size() - 1) ? lines.length : txLineIndices.get(i + 1);

            String mainLine = lines[currentIdx].trim();
            Matcher matcher = FLEX_CORE_PATTERN.matcher(mainLine);
            
            if (matcher.find()) {
                String amountStr = matcher.group(1).trim();
                String balanceStr = matcher.group(2).trim();
                String head = mainLine.substring(0, matcher.start()).trim();

                StringBuilder rawDesc = new StringBuilder();
                String dateStr = null;

                // 2a. Collect remarks and date from lines BEFORE the main line (but after previous TX)
                for (int k = prevIdx + 1; k < currentIdx; k++) {
                    String prevLine = lines[k].trim();
                    if (!isHeaderOrFooter(prevLine)) {
                        rawDesc.append(prevLine).append(" ");
                        if (dateStr == null) {
                            Matcher dm = DATE_PATTERN.matcher(prevLine);
                            if (dm.find()) {
                                dateStr = dm.group(1);
                            }
                        }
                    }
                }

                // 2b. Add head of main line (if not just entry number)
                // Filter out entry number at start (e.g., "1 ", "10 ")
                String headNoEntry = head.replaceAll("^\\d+\\s*", "");
                if (dateStr == null) {
                    Matcher dm = DATE_PATTERN.matcher(headNoEntry);
                    if (dm.find()) {
                        dateStr = dm.group(1);
                        headNoEntry = headNoEntry.replace(dateStr, "").trim();
                    }
                }
                if (!headNoEntry.isEmpty()) {
                    rawDesc.append(headNoEntry).append(" ");
                }

                // 2c. Lines after (from currentIdx+1 to nextIdx-1)
                for (int k = currentIdx + 1; k < nextIdx; k++) {
                    String nextLine = lines[k].trim();
                    if (!isHeaderOrFooter(nextLine)) {
                        rawDesc.append(nextLine).append(" ");
                    }
                }

                String finalRawDesc = cleanFinalRawDesc(rawDesc.toString());

                // 3. Build Transaction
                BankTransaction tx = new BankTransaction();
                tx.setMutationDocument(document);
                tx.setBankAccount(document.getBankAccount());
                tx.setRawDescription(finalRawDesc);
                
                // Parse Amounts
                boolean isCredit = amountStr.startsWith("+");
                BigDecimal amount = new BigDecimal(amountStr.substring(1).replace(".", "").replace(",", "."));
                BigDecimal balance = new BigDecimal(balanceStr.replace(".", "").replace(",", "."));
                
                tx.setAmount(amount.abs());
                tx.setBalance(balance);
                tx.setMutationType(isCredit ? MutationType.CR : MutationType.DB);

                // Extract Date
                if (dateStr != null) {
                    try {
                        String cleanDate = dateStr.replaceAll("\\s+", " ").trim();
                        String[] parts = cleanDate.split(" ");
                        if (parts.length >= 3) {
                             String day = parts[0];
                             String month = parts[1];
                             StringBuilder yearBuilder = new StringBuilder();
                             for (int p = 2; p < parts.length; p++) {
                                 yearBuilder.append(parts[p]);
                             }
                             String year = yearBuilder.toString();
                             if (year.length() == 2) year = "20" + year;
                             else if (year.equals("205") || year.equals("20 5")) year = "2025";
                             else if (year.length() > 4) year = year.substring(0, 4);
                             
                             cleanDate = day + " " + month + " " + year;
                        }
                        
                        tx.setTransactionDate(LocalDate.parse(cleanDate, DATE_FORMATTER));
                    } catch (Exception e) {
                        log.warn("Gagal parse tanggal Mandiri: {} -> fallback to now. Error: {}", dateStr, e.getMessage());
                        tx.setTransactionDate(LocalDate.now());
                    }
                } else {
                    tx.setTransactionDate(LocalDate.now());
                }

                // Duplicate Hash
                String rawStrForHash = tx.getTransactionDate().toString() + "_" + tx.getAmount() + "_" + tx.getRawDescription();
                int count = hashCounters.getOrDefault(rawStrForHash, 0) + 1;
                hashCounters.put(rawStrForHash, count);
                tx.setDuplicateHash(generateHash(rawStrForHash + "_" + count));

                // Refinement
                String normalizedDesc = transactionRefinementService.normalizeDescription(tx.getRawDescription());
                String cpName = transactionRefinementService.extractCounterpartyName("MANDIRI", tx.getRawDescription(), tx.getMutationType() == MutationType.CR);
                TransactionCategory cat = transactionRefinementService.categorizeTransaction(normalizedDesc, tx.getMutationType() == MutationType.CR);
                
                tx.setNormalizedDescription(normalizedDesc);
                tx.setCounterpartyName(cpName);
                tx.setCategory(cat);

                list.add(tx);
            }
        }
        return list;
    }

    private String cleanFinalRawDesc(String rawDesc) {
        if (rawDesc == null) return "";
        
        // 1. Bersihkan Timestamp redundant (misal: 23:59:00 WIB)
        // Kita hapus semua polanya karena biasanya jam sudah ada di awal atau tidak diperlukan di counterparty name/dashboard
        String cleaned = rawDesc.replaceAll("\\d{2}:\\d{2}:\\d{2}\\s*WIB", " ");
        
        // 2. Deteksi Footer hukum yang menempel dan potong
        String[] footers = {
            "pejabat Bank Mandri", 
            "This e-Statement",
            "electronic document issued",
            "valid for use without",
            "from Bank Mandiri",
            "Segala bentuk", 
            "All forms of usage", 
            "Nasabah dapat menyampaikan", 
            "objections regarding information", 
            "Nasabah tunduk pada", 
            "bound by the Livin",
            "Electronic document generated",
            "Dokumen ini dihasilkan secara",
            "Customer's role responsibility",
            "Customer's responsibility",
            "role responsibility"
        };
        
        // Bersihkan ganda spasi agar pencocokan lebih mudah
        cleaned = cleaned.replaceAll("\\s{2,}", " ");
        
        for (String f : footers) {
            Pattern p = Pattern.compile("(?i)" + Pattern.quote(f));
            Matcher m = p.matcher(cleaned);
            if (m.find()) {
                cleaned = cleaned.substring(0, m.start());
            }
        }
        
        // 3. Bersihkan sisa karakter aneh di akhir (misal: sisa slash atau titik dari disclaimer)
        cleaned = cleaned.replaceAll("[/., ]+$", "").trim();
        
        // 4. Pastikan spasi ganda menjadi tunggal
        cleaned = cleaned.replaceAll("\\s{2,}", " ");
        
        return cleaned.trim();
    }

    private boolean isHeaderOrFooter(String line) {
        String ln = line.trim();
        if (ln.isEmpty() || ln.equals("-")) return true;
        
        return ln.startsWith("e-Statement") ||
               ln.startsWith("Plaza Mandiri") ||
               ln.startsWith("Nama/Name") ||
               ln.startsWith("Cabang/Branch") ||
               ln.startsWith("Nomor Rekening") ||
               ln.startsWith("Mata Uang") ||
               ln.startsWith("Saldo Awal") ||
               ln.startsWith("Dana Masuk") ||
               ln.startsWith("Dana Keluar") ||
               ln.startsWith("Saldo Akhir") ||
               ln.startsWith("Saldo (IDR)") ||
               ln.startsWith("Balance (IDR)") ||
               ln.startsWith("Tabungan Bisnis IDR") ||
               ln.startsWith("No Tanggal Keterangan") ||
               ln.startsWith("No Date Remarks") ||
               ln.startsWith("PT Bank Mandiri") ||
               ln.startsWith("Mandiri Call") ||
               ln.startsWith("serta merupakan") ||
               ln.startsWith("Disclaimer") ||
               ln.startsWith("Issued on") ||
               ln.startsWith("Dicetak pada") ||
               ln.startsWith("Periode/Period") ||
               ln.contains("ini adalah batas akhir") ||
               ln.contains("e-Statement") ||
               ln.contains("Segala bentuk") ||
               ln.contains("Nasabah dapat") ||
               ln.contains("Nasabah tunduk") ||
               ln.contains("Mandiri (Persero)") ||
               ln.contains("berizin dan diawasi") ||
               ln.contains("Electronic document generated") ||
               ln.contains("Dokumen ini dihasilkan secara") ||
               ln.contains("valid without signature") ||
               ln.contains("pejabat Bank Mandri") ||
               ln.contains("Nasabah sepenuhnya") ||
               ln.contains("from Bank Mandiri") ||
               ln.contains("role responsibility") ||
               ln.matches(".*\\d+/\\d+.*") || // 1/5
               ln.matches("\\d+\\s+dari\\s+\\d+") ||
               ln.matches("\\d+\\s+of\\s+\\d+") ||
               ln.matches("Page\\s+\\d+.*");
    }

    private String generateHash(String rawString) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(rawString.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
