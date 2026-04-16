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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser untuk e-Statement Mandiri (Livin').
 *
 * PDFBox dengan setSortByPosition(true) menghasilkan baris inti dengan format:
 * [NoUrut] [DD Mon YYYY] [teks opsional] [+-Nominal] [Saldo]
 *
 * Contoh baris inti dari PDFBox:
 * "1 03 Jan 2025 +100.000.000,00 115.120.997,10"
 * "2 04 Jan 2025 DESSY ARIZTIA SAVITR 1010006427981 +95.000.000,00
 * 210.120.997,10"
 * "5 07 Jan 2025 Biaya transfer BI Fast -2.500,00 282.618.497,10"
 * "10 +10.700.000,00 18.815.997,10" (tanggal pada baris sebelumnya)
 *
 * Konteks tambahan (before/after):
 * - Before: label transfer ("Transfer BI Fast", "Transfer dari BANK MANDIRI"),
 * arah ("Ke BRI", "Dari BCA")
 * - After: waktu (HH:mm:ss WIB), nama counterparty, deskripsi tambahan
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MandiriPdfParserService implements PdfParserService {

    private final TransactionRefinementService transactionRefinementService;

    @Override
    public String getBankName() {
        return "MANDIRI";
    }

    // === REGEX PATTERNS ===

    /**
     * Mendeteksi "baris inti" transaksi (v2 - disesuaikan dengan output PDFBox).
     * Format PDFBOX: [NoUrut] [DD Mon YYYY opsional] [teks opsional] [+-nominal]
     * [saldo]
     * Tanggal bisa ada di dalam baris inti, atau tidak ada sama sekali.
     *
     * Yang penting: baris diakhiri dengan [+-]nominal [saldo].
     * Group 1: nomor urut (1-3 digit)
     * Group 2: sisa teks (tanggal + head text) — akan diparsing terpisah
     * Group 3: nominal (+/-)
     * Group 4: saldo
     */
    private static final Pattern CORE_LINE_PATTERN = Pattern.compile(
            "^\\s*(\\d{1,3})\\s+(.*?)\\s*([+-]\\d[\\d.]*,\\d{2})\\s+(\\d[\\d.]*,\\d{2})\\s*$");

    /**
     * Mendeteksi tanggal Mandiri: DD Mon YYYY (misal "03 Jan 2025" atau "8 Feb
     * 2025")
     * Toleransi terhadap spasi tambahan akibat kolom terpisah (misal "20 5", "202
     * ")
     */
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{2}(?:\\s*\\d{1,2})?)");

    /**
     * Mendeteksi baris yang DIMULAI dengan tanggal: "DD Mon YYYY [sisa teks]"
     * Dipakai untuk mendeteksi baris before-context yang berisi tanggal
     * ketika tanggal berada pada baris terpisah (bukan di dalam core line).
     */
    private static final Pattern STANDALONE_DATE_LINE = Pattern.compile(
            "^\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{4}(?:\\s+.*)?$");

    /**
     * Mendeteksi baris waktu: HH:mm:ss WIB [sisa teks opsional]
     */
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "^(\\d{2}:\\d{2}:\\d{2})\\s*WIB(.*)$");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    /** Bulan Indonesia ke English mapping */
    private static final Map<String, String> MONTH_MAP = Map.ofEntries(
            Map.entry("JAN", "Jan"), Map.entry("FEB", "Feb"), Map.entry("MAR", "Mar"),
            Map.entry("APR", "Apr"), Map.entry("MEI", "May"), Map.entry("MAY", "May"),
            Map.entry("JUN", "Jun"), Map.entry("JUL", "Jul"), Map.entry("AGU", "Aug"),
            Map.entry("AUG", "Aug"), Map.entry("SEP", "Sep"), Map.entry("OKT", "Oct"),
            Map.entry("OCT", "Oct"), Map.entry("NOV", "Nov"), Map.entry("DES", "Dec"),
            Map.entry("DEC", "Dec"));

    // ===================================================================
    // PUBLIC ENTRY POINT
    // ===================================================================

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
            transactions = extractTransactions(document, entireText);

            log.info("Berhasil mem-parsing PDF Mandiri. Ditemukan {} buah transaksi.", transactions.size());
        } catch (Exception e) {
            log.error("Terjadi masalah saat parsing PDF Mandiri: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal melakukan parsing dokumen PDF Mandiri.", e);
        }

        return transactions;
    }

    // ===================================================================
    // CORE EXTRACTION ALGORITHM
    // ===================================================================

    private List<BankTransaction> extractTransactions(MutationDocument document, String entireText) {
        List<BankTransaction> list = new ArrayList<>();
        Map<String, Integer> hashCounters = new HashMap<>();

        String[] lines = entireText.split("\\r?\\n");

        // === FASE 1: Identifikasi semua indeks baris inti ===
        List<Integer> coreIndices = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (isHeaderOrFooter(trimmed))
                continue;
            if (CORE_LINE_PATTERN.matcher(trimmed).matches()) {
                coreIndices.add(i);
            }
        }

        if (coreIndices.isEmpty()) {
            log.warn("Tidak ditemukan baris transaksi dalam dokumen Mandiri.");
            return list;
        }

        log.debug("Ditemukan {} baris inti transaksi.", coreIndices.size());

        // === FASE 2 & 3: Kumpulkan konteks & bangun transaksi ===
        int previousAfterEnd = 0;

        for (int idx = 0; idx < coreIndices.size(); idx++) {
            int currentCoreIdx = coreIndices.get(idx);
            int nextCoreIdx = (idx < coreIndices.size() - 1)
                    ? coreIndices.get(idx + 1)
                    : lines.length;

            // --- Parse baris inti ---
            String coreLine = lines[currentCoreIdx].trim();
            Matcher coreMatcher = CORE_LINE_PATTERN.matcher(coreLine);
            if (!coreMatcher.matches())
                continue;

            String middleText = coreMatcher.group(2).trim(); // Bisa berisi: tanggal + head text
            String amountStr = coreMatcher.group(3);
            String balanceStr = coreMatcher.group(4);

            // Pisahkan tanggal dan head text dari middleText
            String coreDate = null;
            String headText = middleText;

            Matcher dateMatcher = DATE_PATTERN.matcher(middleText);
            if (dateMatcher.find()) {
                coreDate = dateMatcher.group(1);
                // Head text adalah sisa setelah tanggal
                headText = (middleText.substring(0, dateMatcher.start())
                        + " " + middleText.substring(dateMatcher.end())).trim();
            }

            // --- Kumpulkan BEFORE context ---
            List<String> beforeLines = new ArrayList<>();
            for (int k = previousAfterEnd; k < currentCoreIdx; k++) {
                String line = lines[k].trim();
                if (!line.isEmpty() && !isHeaderOrFooter(line)) {
                    beforeLines.add(line);
                }
            }

            // --- Kumpulkan AFTER context ---
            List<String> afterLines = new ArrayList<>();
            previousAfterEnd = nextCoreIdx;

            for (int k = currentCoreIdx + 1; k < nextCoreIdx; k++) {
                String line = lines[k].trim();
                if (line.isEmpty() || isHeaderOrFooter(line))
                    continue;

                if (isBeforeContextStart(line)) {
                    previousAfterEnd = k;
                    break;
                }

                afterLines.add(line);
            }

            // --- Ekstrak tanggal (prioritas: dari core line, lalu before context) ---
            String dateStr = coreDate;
            if (dateStr == null) {
                dateStr = extractDateFromContext(beforeLines);
            }

            // --- Bangun raw description ---
            String rawDesc = buildRawDescription(beforeLines, headText, afterLines);

            // --- Bangun objek transaksi ---
            BankTransaction tx = buildTransaction(
                    document, dateStr, rawDesc, amountStr, balanceStr, hashCounters);
            if (tx != null) {
                list.add(tx);
            }
        }

        return list;
    }

    // ===================================================================
    // BOUNDARY DETECTION
    // ===================================================================

    /**
     * Menentukan apakah sebuah baris menandai awal konteks transaksi berikutnya.
     * Baris dianggap "awal konteks baru" jika:
     * - Merupakan baris yang DIMULAI dengan tanggal (DD Mon YYYY)
     * - Merupakan label tipe transfer (bukan "Transfer Fee")
     * - Merupakan label arah transfer ("Dari BCA", "Ke BRI", "DARI MERPATI...")
     */
    private boolean isBeforeContextStart(String line) {
        String trimmed = line.trim();

        // Baris dimulai tanggal (cek sebelum kita menghapus digit depan!)
        if (STANDALONE_DATE_LINE.matcher(trimmed).matches()) {
            return true;
        }

        // Hapus digit di awal baris (misal: "2 Transfer dari BANK MANDIRI" -> "Transfer
        // dari BANK MANDIRI")
        // Ini sering terjadi karena PDFBox menggabungkan karakter yang terpisah pada
        // layout
        String cleanLine = trimmed.replaceAll("^\\d+\\s+", "").trim();
        String upper = cleanLine.toUpperCase();

        // Label tipe transfer
        if (upper.startsWith("TRANSFER DARI ")
                || upper.startsWith("TRANSFER KE ")
                || upper.startsWith("TRANSFER BI FAST")
                || upper.startsWith("TRANSFER ANTAR MANDIRI")) {
            return true;
        }

        // Arah transfer pada baris tersendiri (output PDFBox bisa pisah "Dari BCA" /
        // "Ke BRI")
        if (upper.startsWith("DARI ") || upper.startsWith("KE ")) {
            // Pastikan ini arah transfer, bukan deskripsi umum
            if (upper.matches("^(DARI|KE)\\s+(BANK\\s+)?[A-Z]{2,}$")
                    || upper.matches("^DARI\\s+[A-Z\\s]+$")) {
                return true;
            }
        }

        // Label "Pembayaran" standalone (sebelum core line)
        // Hindari memotong pada "pembayaran" huruf kecil karena itu deskripsi user.
        if (upper.startsWith("PEMBAYARAN ") && !cleanLine.startsWith("pembayaran")) {
            return true;
        }

        return false;
    }

    // ===================================================================
    // DATE EXTRACTION
    // ===================================================================

    /**
     * Ekstrak tanggal dari baris-baris konteks sebelum.
     * Baris before-context kadang berisi tanggal standalone: "04 Feb 2025 Transfer
     * dari BANK MANDIRI"
     */
    private String extractDateFromContext(List<String> beforeLines) {
        for (String line : beforeLines) {
            Matcher dm = DATE_PATTERN.matcher(line);
            if (dm.find()) {
                return dm.group(1);
            }
        }
        return null;
    }

    // ===================================================================
    // RAW DESCRIPTION BUILDING
    // ===================================================================

    /**
     * Membangun raw description dari konteks sebelum, teks kepala, dan konteks
     * sesudah.
     */
    private String buildRawDescription(List<String> beforeLines, String headText, List<String> afterLines) {
        StringBuilder sb = new StringBuilder();

        // 1. Konteks sebelum (hilangkan tanggal & timestamp, pertahankan label
        // transfer)
        for (String line : beforeLines) {
            // Bersihkan tanggal dahulu sebelum menghapus nomor bocor
            String cleaned = removeDateFromLine(line);
            cleaned = cleaned.replaceAll("^\\d+\\s+", "");
            cleaned = removeTimeFromLine(cleaned);
            if (!cleaned.isEmpty() && !cleaned.equals("-")) {
                sb.append(cleaned).append(" ");
            }
        }

        // 2. Teks kepala dari baris inti (sudah bebas tanggal dari parsing sebelumnya)
        if (headText != null && !headText.isEmpty()) {
            sb.append(headText).append(" ");
        }

        // 3. Konteks sesudah (hilangkan timestamp, abaikan dash tunggal)
        for (String line : afterLines) {
            String cleaned = removeTimeFromLine(line);
            if (!cleaned.isEmpty() && !cleaned.equals("-")) {
                sb.append(cleaned).append(" ");
            }
        }

        return cleanFinalRawDesc(sb.toString().trim());
    }

    private String removeDateFromLine(String line) {
        return DATE_PATTERN.matcher(line).replaceAll("").trim();
    }

    private String removeTimeFromLine(String line) {
        Matcher m = TIME_PATTERN.matcher(line);
        if (m.matches()) {
            return m.group(2).trim();
        }
        return line.replaceAll("\\d{2}:\\d{2}:\\d{2}\\s*WIB", "").trim();
    }

    // ===================================================================
    // TRANSACTION BUILDING
    // ===================================================================

    private BankTransaction buildTransaction(MutationDocument document, String dateStr,
            String rawDesc, String amountStr, String balanceStr,
            Map<String, Integer> hashCounters) {

        boolean isCredit = amountStr.startsWith("+");
        BigDecimal amount = parseIndonesianAmount(amountStr.substring(1));
        BigDecimal balance = parseIndonesianAmount(balanceStr);

        LocalDate txDate = parseDate(dateStr);

        MutationType mutationType = isCredit ? MutationType.CR : MutationType.DB;

        String normalizedDesc = transactionRefinementService.normalizeDescription(rawDesc);
        String cpName = transactionRefinementService.extractCounterpartyName(
                "MANDIRI", rawDesc, isCredit);
        TransactionCategory category = transactionRefinementService.categorizeTransaction(
                normalizedDesc, isCredit);

        String baseHashStr = txDate.toString() + "_" + amount.toPlainString() + "_" + normalizedDesc;
        int occurrence = hashCounters.getOrDefault(baseHashStr, 0);
        hashCounters.put(baseHashStr, occurrence + 1);
        String finalHash = generateHash(baseHashStr + "_" + occurrence);

        return BankTransaction.builder()
                .mutationDocument(document)
                .bankAccount(document.getBankAccount())
                .transactionDate(txDate)
                .rawDescription(normalizedDesc)
                .normalizedDescription(normalizedDesc)
                .counterpartyName(cpName)
                .mutationType(mutationType)
                .amount(amount)
                .balance(balance)
                .category(category)
                .isExcluded(false)
                .duplicateHash(finalHash)
                .build();
    }

    // ===================================================================
    // AMOUNT & DATE PARSING
    // ===================================================================

    /**
     * Parse nominal format Indonesia: titik sebagai pemisah ribuan, koma sebagai
     * desimal.
     * Contoh: "100.000.000,00" → 100000000.0000
     */
    private BigDecimal parseIndonesianAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty())
            return BigDecimal.ZERO;
        try {
            String cleaned = amountStr.replace(".", "").replace(",", ".");
            return new BigDecimal(cleaned).setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            log.warn("Gagal parse nominal Mandiri: {}", amountStr);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Parse tanggal dari string "DD Mon YYYY".
     * Mendukung bulan Indonesia & Inggris.
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null) {
            log.warn("Tanggal transaksi Mandiri tidak ditemukan, fallback ke hari ini.");
            return LocalDate.now();
        }
        try {
            // Perbaiki tahun yang terpisah karena PDFBox (misal: "20 5", "20 25")
            String normalized = dateStr.replaceAll("\\b20\\s+([0-9])\\b", "202$1");
            normalized = normalized.replaceAll("\\b20\\s+(2[0-9])\\b", "20$1");
            // Tambahkan padding 0 untuk tanggal 1 digit (misal "8 Feb" -> "08 Feb")
            normalized = normalized.replaceAll("^(\\d)\\s+", "0$1 ");
            normalized = normalized.replaceAll("\\s+", " ").trim();

            // Normalize Indonesian month names to English
            String[] parts = normalized.split(" ");
            if (parts.length == 3) {
                String monthKey = parts[1].toUpperCase();
                String engMonth = MONTH_MAP.get(monthKey);
                if (engMonth != null) {
                    normalized = parts[0] + " " + engMonth + " " + parts[2];
                }
            }

            return LocalDate.parse(normalized, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("Gagal parse tanggal Mandiri: {} -> fallback to now. Error: {}",
                    dateStr, e.getMessage());
            return LocalDate.now();
        }
    }

    // ===================================================================
    // DESCRIPTION CLEANUP
    // ===================================================================

    private String cleanFinalRawDesc(String rawDesc) {
        if (rawDesc == null)
            return "";

        // Hapus sisa timestamp
        String cleaned = rawDesc.replaceAll("\\d{2}:\\d{2}:\\d{2}\\s*(?:WIB)?", " ");

        // Potong jika terdapat fragmen footer hukum
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

        cleaned = cleaned.replaceAll("\\s{2,}", " ");

        for (String f : footers) {
            Pattern p = Pattern.compile("(?i)" + Pattern.quote(f));
            Matcher m = p.matcher(cleaned);
            if (m.find()) {
                cleaned = cleaned.substring(0, m.start());
            }
        }

        // Hapus trailing noise
        cleaned = cleaned.replaceAll("[/., ]+$", "").trim();
        cleaned = cleaned.replaceAll("\\s{2,}", " ");

        return cleaned.trim();
    }

    // ===================================================================
    // HEADER/FOOTER FILTER
    // ===================================================================

    private boolean isHeaderOrFooter(String line) {
        String ln = line.trim();
        if (ln.isEmpty() || ln.equals("-"))
            return true;

        // Normalisasi spasi ganda untuk menghindari gagal match pada "Segala bentuk"
        String lnNorm = ln.replaceAll("\\s{2,}", " ");

        return lnNorm.startsWith("e-Statement")
                || lnNorm.startsWith("Plaza Mandiri")
                || lnNorm.startsWith("Nama/Name")
                || lnNorm.startsWith("Cabang/Branch")
                || lnNorm.startsWith("Nomor Rekening")
                || lnNorm.matches("^\\d{1,3}$") // Tangkap nomor halaman mandiri yang tercecer (contoh: "2")
                || lnNorm.startsWith("Mata Uang")
                || lnNorm.startsWith("Saldo Awal")
                || lnNorm.startsWith("Dana Masuk")
                || lnNorm.startsWith("Dana Keluar")
                || lnNorm.startsWith("Saldo Akhir")
                || lnNorm.startsWith("Saldo (IDR)")
                || lnNorm.startsWith("Balance (IDR)")
                || lnNorm.startsWith("Tabungan Bisnis")
                || lnNorm.startsWith("Tabungan Rupiah")
                || lnNorm.startsWith("No Tanggal Keterangan")
                || lnNorm.startsWith("No Date Remarks")
                || lnNorm.startsWith("PT Bank Mandiri")
                || lnNorm.startsWith("Mandiri Call")
                || lnNorm.startsWith("serta merupakan")
                || lnNorm.startsWith("Disclaimer")
                || lnNorm.startsWith("Issued on")
                || lnNorm.startsWith("Dicetak pada")
                || lnNorm.startsWith("Periode/Period")
                || lnNorm.contains("ini adalah batas akhir")
                || lnNorm.contains("e-Statement ini")
                || lnNorm.contains("Segala bentuk")
                || lnNorm.contains("Nasabah dapat")
                || lnNorm.contains("Nasabah tunduk")
                || lnNorm.contains("Mandiri (Persero)")
                || lnNorm.contains("berizin dan diawasi")
                || lnNorm.contains("Electronic document generated")
                || lnNorm.contains("Dokumen ini dihasilkan secara")
                || lnNorm.contains("valid without signature")
                || lnNorm.contains("pejabat Bank Mandri")
                || lnNorm.contains("Nasabah sepenuhnya")
                || lnNorm.contains("from Bank Mandiri")
                || lnNorm.contains("role responsibility")
                || lnNorm.contains("dokumen elektronik")
                || lnNorm.matches("\\d+\\s+dari\\s+\\d+")
                || lnNorm.matches("\\d+\\s+of\\s+\\d+")
                || lnNorm.matches("Page\\s+\\d+.*");
    }

    // ===================================================================
    // HASH GENERATION
    // ===================================================================

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
