package com.example.mutexa_be.service.parser.bni;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.entity.enums.MutationType;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class BniPdfParserService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // =====================================================================
    // Coordinate Thresholds for BNI PDF Columns
    // Values derived from empirical observation of BNI e-Statement PDF
    // =====================================================================
    private static final float NO_MAX = 50f;
    private static final float DATE_MAX = 140f;
    private static final float BRANCH_MAX = 230f;
    private static final float JOURNAL_MAX = 300f;
    private static final float DESC_MAX = 585f;
    private static final float AMOUNT_MAX = 672f;
    private static final float TYPE_MAX = 710f;
    // BALANCE > TYPE_MAX

    public List<BankTransaction> parsePdf(String filePath, MutationDocument document, String password) {
        log.info("Memulai parsing file PDF BNI: {}", filePath);
        File file = new File(filePath);

        if (!file.exists()) {
            throw new IllegalArgumentException("File PDF BNI tidak ditemukan: " + filePath);
        }

        try (PDDocument pdf = (password != null && !password.isEmpty()) ? Loader.loadPDF(file, password) : Loader.loadPDF(file)) {
            List<CharInfo> chars = collectChars(pdf);
            List<ColRow> rows = buildColumnRows(chars);
            List<RawTx> rawTxs = assembleTransactions(rows);
            List<BankTransaction> result = toEntities(rawTxs, document);

            log.info("Berhasil mem-parsing PDF BNI. Ditemukan {} buah transaksi.", result.size());
            return result;
        } catch (Exception e) {
            log.error("Gagal parsing PDF BNI: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal parsing PDF BNI.", e);
        }
    }

    // =====================================================================
    // PHASE 1: COLLECT CHARACTER POSITIONS
    // =====================================================================

    private List<CharInfo> collectChars(PDDocument pdf) throws IOException {
        CharExtractor ex = new CharExtractor();
        ex.setSortByPosition(true); // Memastikan urutan pembacaan terprediksi
        ex.getText(pdf);
        return ex.result;
    }

    private static class CharExtractor extends PDFTextStripper {
        final List<CharInfo> result = new ArrayList<>();
        CharExtractor() throws IOException { super(); }
        @Override
        protected void processTextPosition(TextPosition tp) {
            float x = tp.getXDirAdj();
            float y = tp.getYDirAdj();
            float w = tp.getWidthDirAdj();
            String t = tp.getUnicode();
            if (t != null && !t.trim().isEmpty()) {
                result.add(new CharInfo(getCurrentPageNo(), x, y, w, t));
            }
        }
    }

    private static class CharInfo {
        final int page;
        final float x, y, width;
        final String text;
        CharInfo(int page, float x, float y, float width, String text) {
            this.page = page; this.x = x; this.y = y; this.width = width; this.text = text;
        }
    }

    // =====================================================================
    // PHASE 2: BUILD HIGHER-LEVEL ROWS & COLUMNS
    // =====================================================================

    private static class ColRow {
        String no = "";
        String date = "";
        String branch = "";
        String journal = "";
        String desc = "";
        String amount = "";
        String type = "";
        String balance = "";
        float yApprox;
    }

    private List<ColRow> buildColumnRows(List<CharInfo> chars) {
        // Sort by Page first, then Y, then X to ensure consistent grouping
        chars.sort((c1, c2) -> {
            int pCmp = Integer.compare(c1.page, c2.page);
            if (pCmp != 0) return pCmp;
            int yCmp = Float.compare(c1.y, c2.y);
            if (Math.abs(c1.y - c2.y) < 3.0f) {
                yCmp = 0;
            }
            if (yCmp != 0) return yCmp;
            return Float.compare(c1.x, c2.x);
        });

        List<List<CharInfo>> rawLines = new ArrayList<>();
        List<CharInfo> currentLine = new ArrayList<>();
        Integer bucketPage = null;
        Float bucketY = null;

        for (CharInfo c : chars) {
            if (bucketPage == null || c.page != bucketPage || Math.abs(c.y - bucketY) > 3.0f) {
                if (!currentLine.isEmpty()) {
                    rawLines.add(new ArrayList<>(currentLine));
                    currentLine.clear();
                }
                bucketPage = c.page;
                bucketY = c.y;
            }
            currentLine.add(c);
        }
        if (!currentLine.isEmpty()) rawLines.add(currentLine);

        List<ColRow> rows = new ArrayList<>();

        for (List<CharInfo> lineChars : rawLines) {
            // Sort line's chars by X to reconstruct string sequentially
            lineChars.sort(Comparator.comparingDouble(c -> c.x));

            StringBuilder noB = new StringBuilder();
            StringBuilder dateB = new StringBuilder();
            StringBuilder branchB = new StringBuilder();
            StringBuilder jourB = new StringBuilder();
            StringBuilder descB = new StringBuilder();
            StringBuilder amtB = new StringBuilder();
            StringBuilder typeB = new StringBuilder();
            StringBuilder balB = new StringBuilder();

            Float lastX = null;

            for (CharInfo c : lineChars) {
                // Add space if gap is large enough (e.g. > 2.0 points)
                boolean addSpace = (lastX != null && (c.x - lastX) > 2.0f);

                if (c.x < NO_MAX) {
                    if (addSpace && noB.length() > 0) noB.append(" ");
                    noB.append(c.text);
                } else if (c.x < DATE_MAX) {
                    if (addSpace && dateB.length() > 0) dateB.append(" ");
                    dateB.append(c.text);
                } else if (c.x < BRANCH_MAX) {
                    if (addSpace && branchB.length() > 0) branchB.append(" ");
                    branchB.append(c.text);
                } else if (c.x < JOURNAL_MAX) {
                    if (addSpace && jourB.length() > 0) jourB.append(" ");
                    jourB.append(c.text);
                } else if (c.x < DESC_MAX) {
                    if (addSpace && descB.length() > 0) descB.append(" ");
                    descB.append(c.text);
                } else if (c.x < AMOUNT_MAX) {
                    if (addSpace && amtB.length() > 0) amtB.append(" ");
                    amtB.append(c.text);
                } else if (c.x < TYPE_MAX) {
                    if (addSpace && typeB.length() > 0) typeB.append(" ");
                    typeB.append(c.text);
                } else {
                    if (addSpace && balB.length() > 0) balB.append(" ");
                    balB.append(c.text);
                }
                
                // Track exact character ending for next gap check
                lastX = c.x + c.width;
            }

            ColRow row = new ColRow();
            row.no = noB.toString().trim();
            row.date = dateB.toString().trim();
            row.branch = branchB.toString().trim(); // We capture it, but ignore it later
            row.journal = jourB.toString().trim();
            row.desc = descB.toString().trim();
            row.amount = amtB.toString().trim();
            row.type = typeB.toString().trim();
            row.balance = balB.toString().trim();
            if (!lineChars.isEmpty()) {
                row.yApprox = lineChars.get(0).y;
            }

            if (isDataRow(row)) {
                System.out.println("Parsed Row -> DATE:[" + row.date + "] DESC:[" + row.desc + "] AMT:[" + row.amount + "] TYPE:[" + row.type + "] NO:[" + row.no + "] BR:[" + row.branch + "]");
                rows.add(row);
            }
        }
        return rows;
    }

    private boolean isDataRow(ColRow r) {
        // Teks halaman, header kolom, dll akan diabaikan karena tdak ada format tanggal atau desimal
        // Row berharga setidaknya punya Deskripsi, Tipe, atau Amount
        if (r.desc.equalsIgnoreCase("Description") && r.amount.equalsIgnoreCase("Amount")) return false;
        if (r.desc.toLowerCase().contains("total debit")) return false; // Footer indicator
        
        return !r.desc.isEmpty() || !r.date.isEmpty() || !r.amount.isEmpty();
    }

    // =====================================================================
    // PHASE 3: ASSEMBLE RAW TRANSACTIONS
    // =====================================================================

    private static class RawTx {
        String date;
        StringBuilder descBuilder = new StringBuilder();
        String amount;
        String type;
        String balance;
    }

    private List<RawTx> assembleTransactions(List<ColRow> rows) {
        List<RawTx> transactions = new ArrayList<>();
        RawTx currentTx = null;

        for (ColRow row : rows) {
            // Indikator bahwa row adalah AWAL transaksi yang baru:
            // Terdapat value Date, Amount, Type, Balance
            boolean hasCoreElements = !row.date.isEmpty() && !row.amount.isEmpty() && !row.type.isEmpty();

            if (hasCoreElements && row.date.matches("\\d{2}/\\d{2}/\\d{4}")) {
                if (currentTx != null) {
                    transactions.add(currentTx);
                }
                currentTx = new RawTx();
                currentTx.date = row.date;
                currentTx.amount = row.amount;
                currentTx.type = row.type;
                currentTx.balance = row.balance;
                
                // Hanya masukkan Description saja, biarkan Branch tertinggal!
                if (!row.desc.isEmpty()) {
                    currentTx.descBuilder.append(row.desc);
                }
            } else {
                // Continuation line
                if (currentTx != null && !row.desc.isEmpty()) {
                    currentTx.descBuilder.append(" ").append(row.desc);
                }
            }
        }
        
        if (currentTx != null) {
            transactions.add(currentTx);
        }
        return transactions;
    }

    // =====================================================================
    // PHASE 4: ENTITY CONVERSION
    // =====================================================================

    private List<BankTransaction> toEntities(List<RawTx> rawTxs, MutationDocument doc) {
        List<BankTransaction> result = new ArrayList<>();

        for (RawTx r : rawTxs) {
            try {
                LocalDate tDate = LocalDate.parse(r.date, DATE_FORMATTER);
                
                // Angka dari PDFBox bisa mengandung spasi dan koma
                String amtClean = r.amount.replace(",", "").replace(" ", "");
                String balClean = r.balance.replace(",", "").replace(" ", "");
                
                BigDecimal amount = new BigDecimal(amtClean);
                BigDecimal balance = balClean.isEmpty() ? BigDecimal.ZERO : new BigDecimal(balClean);

                MutationType type = r.type.equalsIgnoreCase("C") ? MutationType.CR : MutationType.DB;
                String finalDesc = r.descBuilder.toString().trim();

                BankTransaction tx = BankTransaction.builder()
                        .mutationDocument(doc)
                        .bankAccount(doc.getBankAccount())
                        .transactionDate(tDate)
                        .rawDescription(finalDesc) // SEKARANG MURNI TANPA BRANCH
                        .normalizedDescription(finalDesc)
                        .mutationType(type)
                        .amount(amount)
                        .balance(balance)
                        //duplicate hash diisi di luar oleh service
                        .build();

                result.add(tx);
            } catch (Exception e) {
                log.warn("Gagal konversi RawTx ke entity (BNI). Date: {}, Desc: {}, Amt: {}", r.date, r.descBuilder.toString(), r.amount, e);
            }
        }
        return result;
    }
}
