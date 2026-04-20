package com.example.mutexa_be.service;

import com.example.mutexa_be.entity.enums.TransactionCategory;
import com.example.mutexa_be.service.extractor.CounterpartyExtractor;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service untuk memoles atau membersihkan teks / deskripsi raw dari mutasi,
 * mendelegasikan proses ekstraksi kepada class spesifik bank (SRP, OCP).
 */
@Slf4j
@Service
public class TransactionRefinementService {

    private final Map<String, CounterpartyExtractor> extractorMap = new HashMap<>();
    private final List<CounterpartyExtractor> allExtractors;
    private CounterpartyExtractor genericExtractor;

    public TransactionRefinementService(List<CounterpartyExtractor> allExtractors) {
        this.allExtractors = allExtractors;
    }

    @PostConstruct
    public void init() {
        for (CounterpartyExtractor extractor : allExtractors) {
            String bankName = extractor.getBankName().toUpperCase();
            extractorMap.put(bankName, extractor);
            if ("GENERIC".equals(bankName)) {
                this.genericExtractor = extractor;
            }
        }
    }

    /**
     * Membersihkan description asli dari spasi ganda, angka-angka referensi
     * yang tidak relevan, karakter khusus, dll sehingga lebih clean.
     */
    public String normalizeDescription(String rawDescription) {
        if (rawDescription == null || rawDescription.isEmpty()) {
            return "-";
        }
        String normalized = rawDescription.replaceAll("[\\r\\n]+", " ");
        normalized = normalized.replaceAll("\\s{2,}", " ");
        return normalized.trim();
    }

    /**
     * Menerima bankName untuk routing ke extractor bank-specific.
     * Ini adalah entry point utama yang dipanggil dari masing-masing parser.
     */
    public String extractCounterpartyName(String bankName, String rawDescription, boolean isCredit) {
        if (rawDescription == null || rawDescription.trim().isEmpty())
            return null;

        CounterpartyExtractor extractor = bankName != null ? extractorMap.get(bankName.toUpperCase()) : null;
        if (extractor == null) {
            extractor = genericExtractor; // fallback to generic
        }

        return extractor != null ? extractor.extract(rawDescription, isCredit) : "-";
    }

    /**
     * Method lama (backward compatible) — dipakai sebagai generic fallback.
     */
    public String extractCounterpartyName(String rawDescription, boolean isCredit) {
        return extractCounterpartyName("GENERIC", rawDescription, isCredit);
    }

    /**
     * Menganalisis description, nominal, dan tipe mutasi untuk menentukan kategori
     * secara akurat.
     * Menggunakan pendekatan Multi-Factor: Keywords + Nominal Signature + Veto
     * Logic.
     */
    public TransactionCategory categorizeTransaction(String normalizedDescription, BigDecimal amount,
            boolean isCredit) {
        if (normalizedDescription == null || normalizedDescription.trim().isEmpty()) {
            return TransactionCategory.TRANSFER;
        }

        String fullText = normalizedDescription.toLowerCase();

        // 1. SIGNATURE NOMINAL (Prioritas Tertinggi - Konfirmasi Kata Kunci Bank Dasar)
        // Mengecek nominal dulu agar biaya sistem (seperti "BI FAST") tidak terblokir Veto.
        if (!isCredit && amount != null) {
            java.util.function.Predicate<Double> isAmt = (val) -> amount.compareTo(BigDecimal.valueOf(val)) == 0;

            // Varian penulisan BI-FAST yang umum di PDF: "BI-FAST", "BI FAST", "BIFAST"
            boolean isBifastKeyword = matchesKeyword(fullText, "bi-fast", "bi fast", "bifast", "bi.fast");
            // Varian biaya bank umum
            boolean isAdminKeyword = matchesKeyword(fullText, "adm", "admin", "biaya", "fee", "pindah", "charge", "mcm");

            if (isAmt.test(2500.0) && (isBifastKeyword || isAdminKeyword)) return TransactionCategory.ADMIN;
            if (isAmt.test(6500.0) && (isAdminKeyword || matchesKeyword(fullText, "transfer", "trf", "online"))) return TransactionCategory.ADMIN;
            if (isAmt.test(2900.0) && (isAdminKeyword || matchesKeyword(fullText, "llg", "skn", "kliring"))) return TransactionCategory.ADMIN;
            if (isAmt.test(25000.0) && (isAdminKeyword || fullText.contains("rtgs"))) return TransactionCategory.ADMIN;

            // Biaya Bulanan / Admin Rekening (7.500, 10.000, 11.000, 12.000, 12.500, 14.000, 15.000, 17.000)
            if (isAmt.test(15000.0) || isAmt.test(7500.0) || isAmt.test(10000.0) || isAmt.test(11000.0) 
                || isAmt.test(12500.0) || isAmt.test(14000.0) || isAmt.test(17000.0)) {
                if (isAdminKeyword) return TransactionCategory.ADMIN;
            }
        }

        // 2. VETO LOGIC: Deteksi Aktivitas Manual User (Pindah/Transfer Orang)
        // Fokus hanya pada penanda struktur manual: "Ke Rek", "To:", "Dari:", "Memo:", dll.
        boolean isUserActivity = matchesKeyword(fullText,
                "to:", "ke ", "dari ", "memo", "ref:", "dari:", "ke rek", "daripada", "kpd:", "untuk:");

        if (isUserActivity) {
            return TransactionCategory.TRANSFER;
        }

        // 3. HIERARCHICAL KEYWORD MATCHING (Sistem Bank Sisanya)

        // Kategori: TAX (Harus Debet)
        if (!isCredit) {
            if (matchesKeyword(fullText, "pajak", "pph", "tax", "wht", "ppn", "pjk", "pajak bunga")) {
                return TransactionCategory.TAX;
            }
        }

        // Kategori: INTEREST (Bunga Bank) - Harus Kredit
        if (isCredit) {
            if (matchesKeyword(fullText, "bunga", "interest", "jasa giro", "nisbah", "bagi hasil", "int.")) {
                return TransactionCategory.INTEREST;
            }
        }

        // Kategori: ADMIN (Biaya Bank Lainnya) - Harus Debet
        if (!isCredit) {
            if (matchesKeyword(fullText, "adm", "admin", "biaya", "fee", "charge", "provision", "provisi", "materai",
                    "mcm fee", "mcm adm")) {
                return TransactionCategory.ADMIN;
            }
        }

        return TransactionCategory.TRANSFER; // Default Fallback
    }

    private boolean matchesKeyword(String text, String... keywords) {
        for (String kw : keywords) {
            // Menggunakan patern word boundary untuk semua kata kunci pendek (<= 4 char)
            // demi akurasi
            if (kw.length() <= 4) {
                if (text.matches(".*\\b" + java.util.regex.Pattern.quote(kw) + "\\b.*"))
                    return true;
            } else if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
