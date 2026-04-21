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
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Mandiri Livin' e-Statement PDF Parser.
 * <p>
 * Uses <b>position-based column extraction</b> instead of PDFBox getText().
 * Each character is classified into a column (No, Tanggal, Keterangan, Nominal,
 * Saldo)
 * based on its X coordinate, completely eliminating the text fragmentation
 * issues
 * inherent in PDFBox's line-merging heuristics.
 * <p>
 * Column boundaries derived from the e-Statement header positions:
 * 
 * <pre>
 *   No       Tanggal     Keterangan         Nominal        Saldo
 *   X<45     45≤X<115    115≤X<355          355≤X<500      X≥500
 * </pre>
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

    // =====================================================================
    // COLUMN X BOUNDARIES
    // =====================================================================
    private static final float COL_DATE_START = 45f;
    private static final float COL_DESC_START = 115f;
    private static final float COL_NOM_START = 355f;
    private static final float COL_SAL_START = 500f;

    // Row grouping: chars within this Y tolerance belong to the same visual row
    private static final float Y_ROW_TOLERANCE = 2.0f;

    // Visual Y gap that indicates a new transaction block
    private static final float TX_BOUNDARY_GAP = 18f;

    // Patterns
    private static final Pattern DATE_PAT = Pattern.compile(
            "\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+\\d{4}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_PAT = Pattern.compile(
            "\\d{2}:\\d{2}:\\d{2}\\s*WIB");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    // =====================================================================
    // DATA CLASSES
    // =====================================================================

    /** Character with its PDF position metadata. */
    private static class CharInfo {
        final float x, y, charWidth, spaceWidth;
        final int page;
        final String ch;

        CharInfo(float x, float y, float charWidth, float spaceWidth, int page, String ch) {
            this.x = x;
            this.y = y;
            this.charWidth = charWidth > 0 ? charWidth : 3f;
            this.spaceWidth = spaceWidth > 0 ? spaceWidth : 3f;
            this.page = page;
            this.ch = ch;
        }
    }

    /** One visual row with text separated by column. */
    private static class ColRow {
        float absY; // page*10000 + y, for absolute ordering across pages
        String no = "", date = "", desc = "", nominal = "", saldo = "";

        boolean isCoreRow() {
            return !no.isEmpty() && no.matches("\\d+")
                    && !nominal.isEmpty() && nominal.matches("[+\\-]?[\\d.,]+");
        }

        boolean hasDate() {
            return DATE_PAT.matcher(date).find();
        }

        boolean hasTime() {
            return TIME_PAT.matcher(date).find();
        }

        String fullText() {
            return (no + " " + date + " " + desc + " " + nominal + " " + saldo).trim().toLowerCase();
        }
    }

    /** Raw transaction data before entity conversion. */
    private static class RawTx {
        LocalDate date;
        final List<String> descParts = new ArrayList<>();
        String nominalStr = "";
        String saldoStr = "";
    }

    // =====================================================================
    // MAIN PARSE METHOD
    // =====================================================================

    @Override
    public List<BankTransaction> parse(MutationDocument document, String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("File PDF Mandiri tidak ditemukan: " + filePath);
        }

        try (PDDocument pdf = Loader.loadPDF(file)) {
            List<CharInfo> chars = collectChars(pdf); // Phase 1
            List<ColRow> rows = buildColumnRows(chars); // Phase 2
            rows = filterNonData(rows); // Phase 3
            List<RawTx> rawTxs = assembleTransactions(rows); // Phase 4
            List<BankTransaction> result = toEntities(rawTxs, document); // Phase 5

            log.info("Berhasil mem-parsing PDF Mandiri. Ditemukan {} buah transaksi.", result.size());
            return result;
        } catch (Exception e) {
            log.error("Gagal parsing PDF Mandiri: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal parsing PDF Mandiri.", e);
        }
    }

    // =====================================================================
    // PHASE 1: COLLECT CHARACTER POSITIONS
    // =====================================================================

    private List<CharInfo> collectChars(PDDocument pdf) throws IOException {
        CharExtractor ex = new CharExtractor();
        ex.setSortByPosition(true);
        ex.getText(pdf);
        return ex.result;
    }

    /**
     * Custom PDFTextStripper that captures every character with its X/Y position.
     */
    private static class CharExtractor extends PDFTextStripper {
        final List<CharInfo> result = new ArrayList<>();

        CharExtractor() throws IOException {
            super();
        }

        @Override
        protected void processTextPosition(TextPosition tp) {
            float y = tp.getYDirAdj();
            // Filter out exact page header and footer regions (where garbled text occurs)
            if (y < 150f || y > 800f) {
                return;
            }

            String ch = tp.getUnicode();
            if (ch != null && !ch.isEmpty()) {
                result.add(new CharInfo(
                        tp.getXDirAdj(), y,
                        tp.getWidthDirAdj(), tp.getWidthOfSpace(),
                        getCurrentPageNo(), ch));
            }
        }
    }

    // =====================================================================
    // PHASE 2: GROUP CHARS INTO COLUMN-SPLIT ROWS
    // =====================================================================

    private List<ColRow> buildColumnRows(List<CharInfo> chars) {
        // Group characters by (page, Y) with tolerance
        TreeMap<Float, List<CharInfo>> groups = new TreeMap<>();
        for (CharInfo c : chars) {
            float key = c.page * 10000f + c.y;
            Float existing = groups.floorKey(key + Y_ROW_TOLERANCE);
            if (existing != null && Math.abs(existing - key) <= Y_ROW_TOLERANCE) {
                groups.get(existing).add(c);
            } else {
                List<CharInfo> list = new ArrayList<>();
                list.add(c);
                groups.put(key, list);
            }
        }

        // Convert each group into a ColRow with per-column text
        List<ColRow> rows = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            List<CharInfo> rowChars = entry.getValue();
            rowChars.sort(Comparator.comparingDouble(c -> c.x));

            ColRow row = new ColRow();
            row.absY = entry.getKey();

            // Per-column text builders with spacing tracking
            StringBuilder[] sbs = new StringBuilder[5];
            float[] prevX = new float[5];
            float[] prevW = new float[5];
            float[] prevSW = new float[5];
            for (int i = 0; i < 5; i++) {
                sbs[i] = new StringBuilder();
                prevX[i] = -999;
                prevW[i] = 0;
                prevSW[i] = 3f;
            }

            for (CharInfo c : rowChars) {
                int col = classifyColumn(c.x);
                StringBuilder sb = sbs[col];

                // Insert space based on gap between end of previous char and start of this one
                if (prevX[col] > -900) {
                    float endPrev = prevX[col] + prevW[col];
                    float gap = c.x - endPrev;
                    float spW = Math.max(c.spaceWidth, prevSW[col]);
                    if (gap > spW * 0.5f) {
                        sb.append(' ');
                    }
                }
                sb.append(c.ch);
                prevX[col] = c.x;
                prevW[col] = c.charWidth;
                prevSW[col] = c.spaceWidth;
            }

            row.no = sbs[0].toString().trim();
            row.date = sbs[1].toString().trim();
            row.desc = sbs[2].toString().trim();
            row.nominal = sbs[3].toString().trim();
            row.saldo = sbs[4].toString().trim();
            rows.add(row);
        }
        return rows;
    }

    /**
     * Classify an X coordinate into a column index (0=No, 1=Date, 2=Desc,
     * 3=Nominal, 4=Saldo).
     */
    private int classifyColumn(float x) {
        if (x < COL_DATE_START)
            return 0;
        if (x < COL_DESC_START)
            return 1;
        if (x < COL_NOM_START)
            return 2;
        if (x < COL_SAL_START)
            return 3;
        return 4;
    }

    // =====================================================================
    // PHASE 3: FILTER OUT HEADERS, FOOTERS, AND DISCLAIMERS
    // =====================================================================

    private List<ColRow> filterNonData(List<ColRow> rows) {
        List<ColRow> out = new ArrayList<>();
        boolean inDisclaimer = false;

        for (ColRow r : rows) {
            String ft = r.fullText();
            if (ft.isBlank() || ft.equals("-"))
                continue;

            // Disclaimer mode toggle
            if (ft.contains("batas akhir transaksi") || ft.equals("disclaimer")) {
                inDisclaimer = true;
                continue;
            }
            if (ft.contains("saldo awal") || ft.contains("initial balance")) {
                inDisclaimer = false;
                continue;
            }
            if (inDisclaimer)
                continue;

            if (isHeaderOrFooter(ft))
                continue;

            // Standalone number in No column with nothing else (page number residue)
            if (r.no.matches("\\d+") && r.date.isEmpty() && r.desc.isEmpty()
                    && r.nominal.isEmpty() && r.saldo.isEmpty())
                continue;

            out.add(r);
        }
        return out;
    }

    private boolean isHeaderOrFooter(String ft) {
        // Strip all spaces to catch heavily fragmented texts like "T a b u n g a n"
        String spaceless = ft.replaceAll("\\s+", "").toLowerCase();
        return (spaceless.contains("estatement") && spaceless.length() < 25)
                || spaceless.contains("menaramandiri") || spaceless.contains("plazamandiri")
                || spaceless.contains("nama/name") || spaceless.contains("cabang/branch")
                || spaceless.contains("tabungan") || spaceless.matches(".*\\d+of\\d+.*")
                || spaceless.contains("saldoakhir") || spaceless.contains("closingbalance")
                || spaceless.contains("nomorrekening") || spaceless.contains("accountnumber")
                || spaceless.contains("matauang") || spaceless.contains("currency")
                || spaceless.contains("danamasuk") || spaceless.contains("incomingtransaction")
                || spaceless.contains("danakeluar") || spaceless.contains("outgoingtransaction")
                || spaceless.contains("notanggalketerangan") || spaceless.contains("nodateremarks")
                || spaceless.contains("ptbankmandiri") || spaceless.contains("mandiricall")
                || spaceless.contains("sertamerupakanpeserta")
                || spaceless.contains("estatementinimerupakan")
                || spaceless.contains("segalabentukpenggunaan")
                || spaceless.contains("nasabahdapatmengajukan")
                || spaceless.contains("keberatanatas") || spaceless.contains("discrepancies")
                || spaceless.contains("14harikalender") || spaceless.contains("14calendar")
                || spaceless.contains("nasabahtunduk") || spaceless.contains("customer's")
                || spaceless.contains("frombankmandiri") || spaceless.contains("pejabatbank")
                || spaceless.contains("objectionsregarding") || spaceless.contains("boundbythelivin")
                || spaceless.matches("^\\d+dari\\d+$");
    }

    // =====================================================================
    // PHASE 4: ASSEMBLE ROWS INTO TRANSACTIONS
    // =====================================================================

    /**
     * Groups rows into transactions using a forward scan with state tracking.
     * <p>
     * Transaction boundaries are detected by:
     * <ul>
     * <li>Core rows (No + Nominal + Saldo filled)</li>
     * <li>Large Y gaps indicating visual separation between transaction blocks</li>
     * <li>Pending before-context accumulation (desc/date found before core)</li>
     * </ul>
     */
    private List<RawTx> assembleTransactions(List<ColRow> rows) {
        List<RawTx> transactions = new ArrayList<>();
        RawTx current = null;
        List<String> pendingDesc = new ArrayList<>();
        LocalDate pendingDate = null;
        float lastAbsY = -99999f;

        for (ColRow r : rows) {
            float gap = r.absY - lastAbsY;

            if (r.isCoreRow()) {
                // ── CORE ROW: Finalize previous TX, start new one ──
                if (current != null)
                    transactions.add(current);

                current = new RawTx();
                current.nominalStr = r.nominal;
                current.saldoStr = r.saldo;

                // Date: prefer pending (from before-context), fallback to core row's own
                if (pendingDate != null) {
                    current.date = pendingDate;
                } else if (r.hasDate()) {
                    current.date = parseDate(r.date);
                }

                // Description: prepend pending before-context, then core's own desc
                current.descParts.addAll(pendingDesc);
                if (!r.desc.isEmpty())
                    current.descParts.add(r.desc);

                pendingDesc.clear();

                pendingDate = null;

            } else if (r.hasDate()) {
                // ── DATE ROW: Save as pending for next core row ──
                pendingDate = parseDate(r.date);
                if (!r.desc.isEmpty())
                    pendingDesc.add(r.desc);

            } else if (r.hasTime()) {

                // ── TIME ROW: After-context for current transaction ──
                if (current != null && !r.desc.isEmpty()) {
                    current.descParts.add(r.desc);
                }

            } else if (!r.desc.isEmpty()) {
                // ── DESCRIPTION-ONLY ROW ──
                // Determine if this is after-context (current TX) or before-context (next TX)
                boolean isBeforeContext = current == null // no transaction yet
                        || pendingDate != null // date already found for next TX
                        || !pendingDesc.isEmpty() // already accumulating before-context
                        || gap > TX_BOUNDARY_GAP; // large visual gap = new block

                if (isBeforeContext) {
                    pendingDesc.add(r.desc);
                } else {
                    current.descParts.add(r.desc);
                }
            }

            lastAbsY = r.absY;
        }

        if (current != null)
            transactions.add(current);
        return transactions;
    }

    // =====================================================================
    // PHASE 5: CONVERT TO BANK TRANSACTION ENTITIES
    // =====================================================================

    private List<BankTransaction> toEntities(List<RawTx> rawTxs, MutationDocument doc) {
        List<BankTransaction> results = new ArrayList<>();
        Map<String, Integer> hashCounters = new HashMap<>();

        for (RawTx raw : rawTxs) {
            // Date
            LocalDate txDate = raw.date != null ? raw.date : LocalDate.now();
            if (raw.date == null) {
                log.warn("Tanggal transaksi Mandiri tidak ditemukan, fallback ke hari ini.");
            }

            // Description
            String rawDesc = String.join(" ", raw.descParts).replaceAll("\\s+", " ").trim();

            // Nominal & type (+ = CR, - = DB)
            String nomStr = raw.nominalStr.trim();
            MutationType type;
            if (nomStr.startsWith("+")) {
                type = MutationType.CR;
                nomStr = nomStr.substring(1);
            } else if (nomStr.startsWith("-")) {
                type = MutationType.DB;
                nomStr = nomStr.substring(1);
            } else {
                type = MutationType.DB;

            }

            BigDecimal amount = parseIndonesianAmount(nomStr);
            BigDecimal balance = parseIndonesianAmount(raw.saldoStr);

            // Refinement
            String normalizedDesc = transactionRefinementService.normalizeDescription(rawDesc);
            String cpName = transactionRefinementService.extractCounterpartyName(
                    "Mandiri", rawDesc, type == MutationType.CR);
            TransactionCategory category = transactionRefinementService.categorizeTransaction(
                    normalizedDesc, amount, type == MutationType.CR);

            // Hash (MD5, consistent with other parsers)
            // Scoped Hash: Masukkan ID Rekening agar transaksi identik di rekening berbeda
            // tidak tabrakan
            String baseHash = doc.getBankAccount().getId() + "_" + txDate + "_" + amount.toPlainString() + "_"
                    + normalizedDesc;
            int occ = hashCounters.getOrDefault(baseHash, 0);
            hashCounters.put(baseHash, occ + 1);

            results.add(BankTransaction.builder()
                    .mutationDocument(doc)
                    .bankAccount(doc.getBankAccount())
                    .transactionDate(txDate)
                    .rawDescription(rawDesc)
                    .normalizedDescription(normalizedDesc)
                    .counterpartyName(cpName)
                    .mutationType(type)
                    .amount(amount)
                    .balance(balance)
                    .category(category)
                    .isExcluded(category == TransactionCategory.ADMIN || 
                                category == TransactionCategory.TAX || 
                                category == TransactionCategory.INTEREST)
                    .duplicateHash(generateMd5Hash(baseHash + "_" + occ))
                    .build());
        }
        return results;
    }

    // =====================================================================
    // UTILITY METHODS
    // =====================================================================

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank())
            return null;
        try {
            // Extract just the date part (in case of trailing text)
            var m = DATE_PAT.matcher(s.trim());
            if (m.find()) {
                return LocalDate.parse(m.group(), DATE_FMT);
            }
            return null;
        } catch (DateTimeParseException e) {
            log.warn("Gagal parse tanggal Mandiri: {} -> {}", s, e.getMessage());
            return null;
        }
    }

    /** Parse Indonesian-format amount (dot = thousands, comma = decimal). */
    private BigDecimal parseIndonesianAmount(String s) {
        if (s == null || s.isBlank())
            return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.replace(".", "").replace(",", ".").trim())
                    .setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            log.warn("Gagal parse nominal Mandiri: {}", s);
            return BigDecimal.ZERO;
        }
    }

    /** Generate MD5 hash (consistent with BCA, BRI, UOB, Kopra parsers). */
    private String generateMd5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
