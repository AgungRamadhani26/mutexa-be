package com.example.mutexa_be.service;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.enums.MutationType;
import com.example.mutexa_be.entity.enums.TransactionCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service kategorisasi transaksi bank (ADMIN, TAX, INTEREST, TRANSFER).
 * <p>
 * Arsitektur: Three-Phase Categorization Engine
 * <pre>
 *   Phase 1: EXACT PATTERN MATCH (Prioritas Tertinggi)
 *            → Pola unik dan tidak ambigu yang langsung menentukan kategori.
 *
 *   Phase 2: NOMINAL SIGNATURE + KEYWORD COMBO
 *            → Mendeteksi biaya bank berdasarkan kombinasi nominal khas + kata kunci.
 *
 *   Phase 3: VETO LOGIC + HIERARCHICAL FALLBACK
 *            → Veto: Pola transfer manual user → pasti TRANSFER.
 *            → Hierarchical: TAX > INTEREST > ADMIN > TRANSFER (default).
 * </pre>
 * <p>
 * Bank yang didukung: BCA, BRI, Mandiri, Mandiri Kopra, BNI, UOB.
 * Dirancang agar robust untuk berbagai nasabah/rekening di bank yang sama.
 */
@Slf4j
@Service
public class CategorizationService {

    // =====================================================================
    // PHASE 1: EXACT PATTERN KEYWORDS
    // Pola deskripsi yang sangat spesifik dan tidak ambigu.
    // Jika cocok, langsung return tanpa cek lebih lanjut.
    // =====================================================================

    // --- TAX: Pola Pajak Exact ---
    private static final String[] EXACT_TAX_PATTERNS = {
            "pajak bunga",           // BCA, Mandiri, BNI, BRI
            "pajak rekening",        // Mandiri Livin (contoh: "Pajak rekening 80.104,46")
            "pajak jasa giro",       // BNI, Mandiri (rekening giro)
            "pajak deposito",        // Semua bank (deposito)
            "pph bunga",             // BRI, BNI
            "pph jasa giro",         // BNI
            "pph deposito",          // Semua bank
            "pph final",             // Semua bank
            "pph pasal",             // Semua bank (PPh 21, 23, 4(2))
            "pph 21", "pph 23",      // Potongan karyawan/vendor
            "pot pajak",             // Mandiri, BNI ("potongan pajak")
            "pot. pajak",            // Mandiri (format titik)
            "potong pajak",          // BRI
            "potongan pajak",        // BRI, Mandiri
            "tax on interest",       // UOB (English statement)
            "withholding tax",       // UOB (English statement)
            "tax on saving",         // UOB
    };

    // --- INTEREST (CR): Bunga yang DITERIMA (uang masuk) ---
    private static final String[] EXACT_INTEREST_CR_PATTERNS = {
            "interest credit",       // BCA (giro) — contoh: "INTEREST CREDIT"
            "interest on deposit",   // UOB (English)
            "interest on saving",    // UOB (English)
            "interest on account",   // BRI (English) — contoh: "Interest on Account"
            "interest saving",       // UOB
            "bunga tabungan",        // BRI, BNI
            "bunga deposito",        // Semua bank (deposito)
            "bunga rekening",        // Mandiri Livin — contoh: "Bunga rekening 81.452,67"
            "bunga giro",            // BNI, Mandiri
            "bunga harian",          // Mandiri, BRI
            "cr bunga",              // BRI (format khusus)
            "kredit bunga",          // BRI
            "jasa giro",             // BNI, Mandiri — contoh: "JASA GIRO"
            "bagi hasil",            // Bank syariah: BSI, Mandiri Syariah
            "nisbah",                // Bank syariah
            "imbalan",               // Bank syariah
            "bonus tabungan",        // Bank syariah (wadiah)
    };

    // --- INTEREST (DB): Bunga yang DIBAYAR (bunga pinjaman/kredit, uang keluar) ---
    // Ini tetap dikategorikan INTEREST karena substantifnya adalah bunga,
    // bukan biaya administrasi. Penting bagi analis kredit untuk mengetahui
    // beban bunga pinjaman perusahaan.
    private static final String[] EXACT_INTEREST_DB_PATTERNS = {
            "bunga kredit lokal",    // BCA — contoh: "BUNGA KREDIT LOKAL" (bunga fasilitas kredit)
            "bunga pinjaman",        // Umum — bunga cicilan kredit
            "bunga kredit",          // Umum — biaya bunga fasilitas kredit
            "od int charge",         // BCA/UOB — overdraft interest (bunga cerukan)
    };

    // --- ADMIN: Pola Biaya Bank Exact ---
    private static final String[] EXACT_ADMIN_PATTERNS = {
            // === Biaya Administrasi Umum ===
            "biaya adm",             // BCA, Mandiri, BNI, BRI
            "biaya admin",           // Umum
            "biaya administrasi",    // BCA, Mandiri — contoh: "Biaya administrasi kartu debit"
            "admin fee",             // BRI (English) — contoh: "Admin Fee"
            // === Biaya Layanan ===
            "biaya sms",             // BCA, BRI, Mandiri, BNI
            "biaya sms banking",     // BRI, BNI
            "biaya notifikasi",      // Mandiri
            "biaya cetak",           // BCA, Mandiri
            "biaya rekening koran",  // Mandiri, BCA
            "biaya rek koran",       // Mandiri (singkatan)
            "biaya layanan",         // BNI, Mandiri
            "biaya buku",            // BRI
            "biaya kartu",           // BCA, BRI (kartu debit)
            "biaya atm",             // BCA, BRI, BNI
            "biaya bulanan atm",     // BRI — contoh: "Biaya Bulanan ATM"
            "biaya token",           // Mandiri, BNI
            // === Biaya Transfer ===
            "biaya transfer",        // Umum — contoh: "Biaya transfer BI Fast", "Biaya transfer ke Bank lain"
            "biaya trf",             // Umum (singkatan)
            "biaya trx",             // BCA, Mandiri
            "biaya transaksi",       // Umum
            "biaya rtgs",            // Semua bank
            "biaya kliring",         // Semua bank
            "biaya skn",             // Semua bank
            "biaya llg",             // Semua bank
            "biaya swift",           // UOB, Mandiri
            "biaya txn",             // BCA (BI-FAST) — contoh: "BIF BIAYA TXN KE"
            "biaya e-banking",       // BCA
            "biaya kirim",           // BRI
            "biaya tolakan",         // BCA — contoh: "ND-BIAYA TOLAKAN"
            "nd-biaya",              // BCA — prefix untuk biaya reject/tolakan
            // === Biaya Materai & Provisi ===
            "biaya materai",         // BRI, BNI, Mandiri
            "biaya meterai",         // Alternatif ejaan
            "biaya provisi",         // Mandiri, BCA (kredit)
            "biaya komisi",          // Mandiri

            // === English Admin Terms ===
            "monthly fee",           // UOB (English)
            "monthly fee atm",       // BRI (English) — contoh: "Monthly Fee ATM"
            "maintenance fee",       // UOB
            "service charge",        // UOB
            "bank charge",           // UOB
            "bank charges",          // UOB
            "card fee",              // UOB
            "annual fee",            // UOB, BCA
            "transfer fee",          // Mandiri Kopra — contoh: "Transfer Fee" (baris tersendiri, DB)

            "mcm fee",               // BCA — Multi Currency Management
            "mcm adm",               // BCA
    };

    // =====================================================================
    // PHASE 2: NOMINAL SIGNATURES
    // Biaya bank memiliki nominal khas yang tetap. Jika nominal cocok
    // DAN ada keyword pendukung, maka ini PASTI biaya bank (ADMIN).
    // =====================================================================

    /**
     * Set nominal-nominal khas biaya bank di Indonesia.
     * Digunakan bersama dengan keyword pendukung untuk mendeteksi biaya admin.
     */
    private static final Set<Double> ADMIN_NOMINAL_SIGNATURES = Set.of(
            // Biaya BI-FAST (semua bank)
            2500.0,
            // Biaya SKN/LLG (kliring)
            2900.0, 3500.0, 5000.0,
            // Biaya transfer online / e-banking
            6500.0,
            // Biaya admin bulanan berbagai bank
            6000.0,     // BRI Simpedes
            7500.0,     // BNI Taplus Muda
            8500.0,     // Mandiri — contoh: "Biaya administrasi kartu debit 8.500"
            10000.0,    // BCA Tahapan Xpresi
            11000.0,    // BNI Taplus
            12000.0,    // BRI BritAma
            12500.0,    // Mandiri Tabungan Rupiah
            14000.0,    // Mandiri Giro
            15000.0,    // BCA Tahapan
            17000.0,    // BCA Tahapan Gold
            20000.0,    // BCA Gold
            // Biaya RTGS
            25000.0, 30000.0, 35000.0,
            // Biaya ADM Giro Besar (BCA Giro)
            40000.0     // BCA — contoh: "BIAYA ADM 40,000.00 DB"
    );

    /**
     * Keyword pendukung untuk Phase 2 (nominal signature).
     * Salah satu dari keyword ini harus ada agar nominal diakui sebagai biaya admin.
     */
    private static final String[] NOMINAL_SUPPORT_KEYWORDS = {
            "adm", "admin", "biaya", "fee", "charge",
            "pindah", "mcm", "transfer", "trf", "online",
            "bi-fast", "bi fast", "bifast", "bi.fast",
            "llg", "skn", "kliring", "rtgs",
            "e-banking", "ibank", "internet banking",
    };

    // =====================================================================
    // PHASE 3: HIERARCHICAL KEYWORDS (Fallback)
    // Keyword umum yang digunakan jika Phase 1 & 2 tidak menangkap.
    // Lebih longgar, tapi tetap menggunakan word boundary.
    // =====================================================================

    private static final String[] HIERARCHICAL_TAX_KEYWORDS = {
            "pajak", "pph", "ppn", "pjk", "tax", "wht",
    };

    private static final String[] HIERARCHICAL_INTEREST_KEYWORDS = {
            "bunga", "interest", "jasa giro", "nisbah", "bagi hasil",
    };

    private static final String[] HIERARCHICAL_ADMIN_KEYWORDS = {
            "biaya", "fee", "charge", "provision", "provisi",
            "materai", "meterai", "denda", "penalty", "penalti", "pinalty",
    };

    // =====================================================================
    // VETO PATTERNS
    // Jika salah satu pattern ini cocok, transaksi PASTI transfer manual.
    // Ini mencegah "BIAYA TRANSFER KE REKENING xxx" dikategorikan sebagai ADMIN
    // (yang seharusnya TRANSFER karena menyebutkan tujuan transfer).
    // =====================================================================

    // Regex pattern untuk deteksi transfer manual (compiled untuk performa)
    private static final Pattern VETO_TRANSFER_PATTERN = Pattern.compile(
            "\\b(?:" +
                    "ke\\s+rek" +            // "ke rekening", "ke rek."
                    "|dari\\s+rek" +          // "dari rekening"
                    "|kepada\\s" +            // "kepada NAMA"
                    "|kpd[:\\s]" +            // "kpd: NAMA" (BRI)
                    "|daripada\\s" +          // "daripada NAMA" (UOB)
                    "|untuk[:\\s]" +          // "untuk: NAMA"
                    "|to[:\\s]" +             // "to: NAMA" (UOB English)
                    "|from[:\\s]" +           // "from: NAMA" (UOB English)
                    "|memo[:\\s]" +           // "memo: xxx"
                    "|ref[:\\s]" +            // "ref: xxx" (referensi user)
                    "|trsf\\s" +             // "TRSF E-BANKING" (BCA)
                    "|transfer\\s+(?:dr|ke|dari|bi-fast|bi fast)" + // BCA, BRI, Mandiri
                    "|transfer\\s+dana" +     // UOB — contoh: "TRANSFER DANA"
                    "|pindah\\s+buku" +       // "PINDAH BUKU" (BCA internal)
                    "|overbooking" +          // Mandiri internal transfer
                    "|pinbuk" +               // BRI/Mandiri Kopra — contoh: "PINBUK AHSA"
                    "|setoran" +              // BCA, Mandiri Kopra — contoh: "SETORAN SALES", "SETORAN 01/12"
                    "|mcm\\s+inhousetrf" +    // Mandiri Kopra — contoh: "MCM InhouseTrf KE JUI"
                    "|kliring" +              // Mandiri Kopra — contoh: "Kliring 10187983"
                    ")" ,
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Keyword/pola yang MEMBATALKAN deteksi admin meskipun kata "biaya" ada.
     * Contoh: "TRSF E-BANKING DB 1234 KE PT ABADI" — ini transfer, bukan admin.
     * Tapi "BIAYA ADM" tanpa pola transfer = admin.
     */
    private static final Pattern ANTI_FALSE_ADMIN_PATTERN = Pattern.compile(
            "\\b(?:" +
                    "setoran\\s+tunai" +      // Setoran tunai (BCA, BRI)
                    "|penarikan\\s+tunai" +    // Penarikan ATM
                    "|setor\\s+tunai" +        // Singkatan
                    "|tarik\\s+tunai" +        // Singkatan
                    ")" ,
            Pattern.CASE_INSENSITIVE
    );

    // =====================================================================
    // MAIN ENTRY POINT
    // =====================================================================

    /**
     * Meng-enrich kategori transaksi untuk seluruh batch.
     * Dipanggil setelah parser bank-specific mengekstrak transaksi.
     * Method ini OVERRIDE kategori yang sudah di-set parser karena memiliki
     * logika yang lebih komprehensif (three-phase engine).
     *
     * @param transactions List transaksi yang akan dikategorisasi
     */
    public void enrichUnclassified(List<BankTransaction> transactions) {
        log.info("Memulai Klasifikasi v3 (Three-Phase Engine) untuk {} transaksi...",
                transactions.size());

        if (transactions.isEmpty()) {
            return;
        }

        int countAdmin = 0, countTax = 0, countInterest = 0, countTransfer = 0;

        for (BankTransaction tx : transactions) {
            String desc = tx.getNormalizedDescription() != null
                    ? tx.getNormalizedDescription().toLowerCase() : "";
            String rawDesc = tx.getRawDescription() != null
                    ? tx.getRawDescription().toLowerCase() : "";

            // Gabungkan kedua deskripsi dan normalisasi spasi
            String fullText = (desc + " " + rawDesc).replaceAll("\\s+", " ").trim();

            boolean isCredit = tx.getMutationType() == MutationType.CR;
            BigDecimal amount = tx.getAmount();

            TransactionCategory result = categorize(fullText, isCredit, amount);
            tx.setCategory(result);

            // Update isExcluded flag sesuai kategori
            tx.setIsExcluded(result == TransactionCategory.ADMIN ||
                    result == TransactionCategory.TAX ||
                    result == TransactionCategory.INTEREST);

            switch (result) {
                case ADMIN -> countAdmin++;
                case TAX -> countTax++;
                case INTEREST -> countInterest++;
                default -> countTransfer++;
            }
        }

        log.info("Klasifikasi v3 selesai. ADMIN={}, TAX={}, INTEREST={}, TRANSFER={}",
                countAdmin, countTax, countInterest, countTransfer);
    }

    // =====================================================================
    // THREE-PHASE ENGINE
    // =====================================================================

    /**
     * Kategorisasi satu transaksi melalui three-phase engine.
     *
     * @param fullText Teks gabungan (normalized + raw), sudah lowercase
     * @param isCredit true jika transaksi kredit (uang masuk)
     * @param amount   Nominal transaksi
     * @return Kategori transaksi
     */
    private TransactionCategory categorize(String fullText, boolean isCredit, BigDecimal amount) {

        // ================================================================
        // PHASE 1: EXACT PATTERN MATCH (Prioritas Tertinggi)
        // Pola ini sangat spesifik sehingga tidak perlu cek tambahan.
        // Hanya mengecek arah mutasi (CR/DB) untuk validasi dasar.
        // ================================================================

        // TAX: Harus debit (pajak dipotong dari saldo)
        if (!isCredit) {
            for (String pattern : EXACT_TAX_PATTERNS) {
                if (containsPhrase(fullText, pattern)) {
                    log.debug("[CAT] TAX (Phase1-Exact): '{}'", truncateLog(fullText));
                    return TransactionCategory.TAX;
                }
            }
        }

        // INTEREST (CR): Bunga yang diterima (uang masuk)
        if (isCredit) {
            for (String pattern : EXACT_INTEREST_CR_PATTERNS) {
                if (containsPhrase(fullText, pattern)) {
                    log.debug("[CAT] INTEREST (Phase1-CR): '{}'", truncateLog(fullText));
                    return TransactionCategory.INTEREST;
                }
            }
        }

        // INTEREST (DB): Bunga pinjaman/kredit yang dibayar (uang keluar)
        // Tetap INTEREST karena substantifnya bunga, bukan biaya admin.
        if (!isCredit) {
            for (String pattern : EXACT_INTEREST_DB_PATTERNS) {
                if (containsPhrase(fullText, pattern)) {
                    log.debug("[CAT] INTEREST (Phase1-DB-Loan): '{}'", truncateLog(fullText));
                    return TransactionCategory.INTEREST;
                }
            }
        }

        // ADMIN: Harus debit (biaya dipotong dari saldo)
        if (!isCredit) {
            for (String pattern : EXACT_ADMIN_PATTERNS) {
                if (containsPhrase(fullText, pattern)) {
                    // Anti-false-positive: pastikan bukan setoran/penarikan tunai
                    if (!ANTI_FALSE_ADMIN_PATTERN.matcher(fullText).find()) {
                        log.debug("[CAT] ADMIN (Phase1-Exact): '{}'", truncateLog(fullText));
                        return TransactionCategory.ADMIN;
                    }
                }
            }
        }

        // ================================================================
        // PHASE 2: NOMINAL SIGNATURE + KEYWORD COMBO
        // Cocokkan nominal khas biaya bank + keyword pendukung.
        // Hanya untuk debit (biaya selalu mengurangi saldo).
        // ================================================================

        if (!isCredit && amount != null) {
            double amtValue = amount.doubleValue();

            if (ADMIN_NOMINAL_SIGNATURES.contains(amtValue)) {
                // Cek apakah ada keyword pendukung di deskripsi
                if (matchesAnyKeyword(fullText, NOMINAL_SUPPORT_KEYWORDS)) {
                    // Veto check: jika ini sebenarnya transfer manual, jangan kategorikan
                    if (!VETO_TRANSFER_PATTERN.matcher(fullText).find()) {
                        log.debug("[CAT] ADMIN (Phase2-NomSig): amt={}, '{}'",
                                amtValue, truncateLog(fullText));
                        return TransactionCategory.ADMIN;
                    }
                }
            }
        }

        // ================================================================
        // PHASE 3: VETO + HIERARCHICAL FALLBACK
        // ================================================================

        // --- 3A: VETO CHECK ---
        // Jika ada indikasi transfer manual user, langsung return TRANSFER.
        // Ini mencegah kata "biaya" di "TRSF E-BANKING BIAYA..." di-catch
        // sebagai ADMIN.
        if (VETO_TRANSFER_PATTERN.matcher(fullText).find()) {
            return TransactionCategory.TRANSFER;
        }

        // --- 3B: HIERARCHICAL KEYWORD MATCHING ---
        // Urutan prioritas: TAX > INTEREST > ADMIN > TRANSFER

        // TAX (debit only)
        if (!isCredit && matchesAnyKeyword(fullText, HIERARCHICAL_TAX_KEYWORDS)) {
            log.debug("[CAT] TAX (Phase3-Hierarchical): '{}'", truncateLog(fullText));
            return TransactionCategory.TAX;
        }

        // INTEREST (credit only)
        if (isCredit && matchesAnyKeyword(fullText, HIERARCHICAL_INTEREST_KEYWORDS)) {
            log.debug("[CAT] INTEREST (Phase3-Hierarchical): '{}'", truncateLog(fullText));
            return TransactionCategory.INTEREST;
        }

        // ADMIN (debit only)
        if (!isCredit && matchesAnyKeyword(fullText, HIERARCHICAL_ADMIN_KEYWORDS)) {
            // Anti-false-positive: pastikan bukan setoran/penarikan tunai
            if (!ANTI_FALSE_ADMIN_PATTERN.matcher(fullText).find()) {
                log.debug("[CAT] ADMIN (Phase3-Hierarchical): '{}'", truncateLog(fullText));
                return TransactionCategory.ADMIN;
            }
        }

        // Default: TRANSFER
        return TransactionCategory.TRANSFER;
    }

    // =====================================================================
    // HELPER METHODS
    // =====================================================================

    /**
     * Mengecek apakah teks mengandung frasa EXACT (multi-word).
     * Menggunakan word boundary di awal dan akhir frasa agar "adm" tidak
     * tertangkap di "administrator".
     * <p>
     * Untuk frasa multi-word (mengandung spasi), menggunakan contains biasa
     * karena word boundary di tengah frasa sudah terjamin oleh spasi.
     *
     * @param text   Teks sumber (sudah lowercase)
     * @param phrase Frasa yang dicari (sudah lowercase)
     * @return true jika frasa ditemukan
     */
    private boolean containsPhrase(String text, String phrase) {
        if (text == null || text.isBlank() || phrase == null) return false;

        // Multi-word phrase: gunakan contains (spasi di frasa sudah jadi boundary)
        if (phrase.contains(" ") || phrase.contains(".") || phrase.contains("-")
                || phrase.contains("(") || phrase.contains(":")) {
            return text.contains(phrase);
        }

        // Single word: gunakan word boundary regex
        String regex = ".*\\b" + Pattern.quote(phrase) + "\\b.*";
        return text.matches(regex);
    }

    /**
     * Mengecek apakah teks mengandung salah satu dari keyword list.
     * Menggunakan word boundary untuk keyword pendek (≤4 karakter)
     * dan contains untuk keyword panjang (>4 karakter).
     *
     * @param text     Teks sumber (sudah lowercase)
     * @param keywords Array keyword yang dicari
     * @return true jika salah satu keyword ditemukan
     */
    private boolean matchesAnyKeyword(String text, String[] keywords) {
        if (text == null || text.isBlank()) return false;

        for (String kw : keywords) {
            if (kw.contains(" ") || kw.contains(".") || kw.contains("-")) {
                // Multi-word / special char: contains match
                if (text.contains(kw)) return true;
            } else if (kw.length() <= 4) {
                // Short keyword: word boundary match (menghindari false positive)
                if (text.matches(".*\\b" + Pattern.quote(kw) + "\\b.*")) return true;
            } else {
                // Long keyword: contains match (cukup spesifik)
                if (text.contains(kw)) return true;
            }
        }
        return false;
    }

    /**
     * Truncate teks untuk log agar tidak terlalu panjang.
     */
    private String truncateLog(String text) {
        if (text == null) return "";
        return text.length() > 80 ? text.substring(0, 80) + "..." : text;
    }
}
