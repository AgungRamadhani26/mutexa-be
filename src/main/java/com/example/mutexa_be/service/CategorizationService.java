package com.example.mutexa_be.service;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.enums.TransactionCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CategorizationService {

    public void enrichUnclassified(List<BankTransaction> transactions) {
        log.info("Memulai Klasifikasi Robust v2 (Veto Logic + Hierarchical Word Bank)...");

        if (transactions.isEmpty()) {
            return;
        }

        for (BankTransaction tx : transactions) {
            String desc = tx.getNormalizedDescription() != null ? tx.getNormalizedDescription().toLowerCase() : "";
            String rawDesc = tx.getRawDescription() != null ? tx.getRawDescription().toLowerCase() : "";
            // Normalisasi: buang spasi ganda untuk pencocokan kata yang konsisten
            String fullText = (desc + " " + rawDesc).replaceAll("\\s+", " ").trim();

            boolean isCredit = tx.getMutationType() == com.example.mutexa_be.entity.enums.MutationType.CR;
            java.math.BigDecimal amount = tx.getAmount();

            TransactionCategory categorizedAs = TransactionCategory.TRANSFER; // Default

            // 1. SIGNATURE NOMINAL (Prioritas Tertinggi - Konfirmasi Kata Kunci Bank Dasar)
            // Mengecek nominal dulu agar biaya sistem (seperti "BI FAST") tidak terblokir
            // Veto.
            if (!isCredit && amount != null) {
                java.util.function.Predicate<Double> isAmt = (
                        val) -> amount.compareTo(java.math.BigDecimal.valueOf(val)) == 0;

                // Varian penulisan BI-FAST yang umum di PDF: "BI-FAST", "BI FAST", "BIFAST"
                boolean isBifastKeyword = matchesKeyword(fullText, true, "bi-fast", "bi fast", "bifast", "bi.fast");
                // Varian biaya bank umum
                boolean isAdminKeyword = matchesKeyword(fullText, true, "adm", "admin", "biaya", "fee", "pindah",
                        "charge", "mcm");

                if (isAmt.test(2500.0) && (isBifastKeyword || isAdminKeyword)) {
                    tx.setCategory(TransactionCategory.ADMIN);
                    continue;
                }
                if (isAmt.test(6500.0)
                        && (isAdminKeyword || matchesKeyword(fullText, true, "transfer", "trf", "online"))) {
                    tx.setCategory(TransactionCategory.ADMIN);
                    continue;
                }
                if (isAmt.test(2900.0) && (isAdminKeyword || matchesKeyword(fullText, true, "llg", "skn", "kliring"))) {
                    tx.setCategory(TransactionCategory.ADMIN);
                    continue;
                }
                if (isAmt.test(25000.0) && (isAdminKeyword || fullText.contains("rtgs"))) {
                    tx.setCategory(TransactionCategory.ADMIN);
                    continue;
                }

                // Biaya Bulanan / Admin Rekening (7.500, 10.000, 11.000, 12.000, 12.500,
                // 14.000, 15.000, 17.000)
                if (isAmt.test(15000.0) || isAmt.test(7500.0) || isAmt.test(10000.0) || isAmt.test(11000.0)
                        || isAmt.test(12500.0) || isAmt.test(14000.0) || isAmt.test(17000.0)) {
                    if (isAdminKeyword) {
                        tx.setCategory(TransactionCategory.ADMIN);
                        continue;
                    }
                }
            }

            // 2. VETO LOGIC: Deteksi Aktivitas Manual User (Pindah/Transfer Orang)
            // Fokus hanya pada penanda struktur manual: "Ke Rek", "To:", "Dari:", "Memo:",
            // dll.
            boolean isManualTransfer = matchesKeyword(fullText, true,
                    "to:", "ke ", "dari ", "memo", "ref:", "dari:", "ke rek", "daripada", "kpd:", "untuk:");

            if (isManualTransfer) {
                tx.setCategory(TransactionCategory.TRANSFER);
                continue;
            }

            // 3. HIERARCHICAL MATCHING (Sisanya)

            // PRIORITAS 1: TAX (Harus DB)
            if (!isCredit
                    && matchesKeyword(fullText, true, "pajak", "pph", "tax", "wht", "ppn", "pjk", "pajak bunga")) {
                categorizedAs = TransactionCategory.TAX;
            }

            // PRIORITAS 2: INTEREST (Harus CR)
            if (categorizedAs == TransactionCategory.TRANSFER && isCredit) {
                if (matchesKeyword(fullText, true, "bunga", "interest", "int.", "jasa giro", "nisbah", "bagi hasil")) {
                    categorizedAs = TransactionCategory.INTEREST;
                }
            }

            // PRIORITAS 3: ADMIN (Harus DB)
            if (categorizedAs == TransactionCategory.TRANSFER && !isCredit) {
                if (matchesKeyword(fullText, true, "adm", "admin", "biaya", "fee", "charge", "provision", "provisi",
                        "materai", "mcm fee", "mcm adm")) {
                    categorizedAs = TransactionCategory.ADMIN;
                }
            }

            tx.setCategory(categorizedAs);
        }

        log.info("Selesai. Klasifikasi Robust v2 berhasil diterapkan.");
    }

    /**
     * Helper pencocokan kata kunci.
     * 
     * @param text       Teks sumber
     * @param exactMatch Jika true, maka kata harus berdiri sendiri (menggunakan
     *                   spasi/titik sebagai pembatas)
     * @param keywords   Daftar kata kunci
     */
    private boolean matchesKeyword(String text, boolean exactMatch, String... keywords) {
        if (text == null || text.isBlank())
            return false;

        for (String keyword : keywords) {
            String kw = keyword.toLowerCase();
            if (exactMatch) {
                // Menggunakan regex untuk memastikan kata berdiri sendiri (Whole Word)
                // \b (word boundary) mencakup spasi, titik, koma, dsb.
                String pattern = ".*\\b" + java.util.regex.Pattern.quote(kw) + "\\b.*";
                if (text.matches(pattern)) {
                    return true;
                }
            } else {
                // Partial match biasa (contains)
                if (text.contains(kw)) {
                    return true;
                }
            }
        }
        return false;
    }
}
