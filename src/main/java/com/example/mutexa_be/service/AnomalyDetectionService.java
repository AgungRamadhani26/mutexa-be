package com.example.mutexa_be.service;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.enums.MutationType;
import com.example.mutexa_be.entity.enums.TransactionCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * SERVICE DETEKSI ANOMALI TRANSAKSI BANK
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Service ini bertanggung jawab HANYA untuk memberikan flag isAnomaly = true
 * dan menyematkan anomalyReason pada transaksi yang mencurigakan.
 * TIDAK mengubah kategori atau data transaksi lainnya.
 *
 * === 3 PILAR DETEKSI ANOMALI ===
 *
 * PILAR 1 — WINDOW DRESSING
 * Mendeteksi dana "numpang lewat": uang masuk besar (CR) lalu ditarik
 * keluar (DB) dalam waktu ≤ 48 jam dengan selisih nominal ≤ 5%.
 * Menggunakan dynamic threshold berdasarkan profil turnover nasabah.
 *
 * PILAR 2 — OUTLIER (IQR-Based)
 * Mendeteksi transaksi dengan nominal yang jauh melebihi pola normal
 * nasabah. Menggunakan metode IQR (Interquartile Range) yang robust
 * terhadap distribusi non-normal (skewed), berbeda dari Z-Score yang
 * mengasumsikan distribusi Gaussian.
 * CR dan DB dianalisis TERPISAH karena profil pemasukan vs pengeluaran
 * berbeda secara fundamental.
 *
 * PILAR 3 — DETEKSI PINJAMAN BANK/LEASING LAIN
 * Mendeteksi transaksi yang mengandung nama lembaga keuangan lain
 * (bank kompetitor, multifinance, fintech lending). Informasi ini krusial
 * bagi credit analyst karena menunjukkan nasabah memiliki kewajiban
 * finansial di tempat lain (indikasi leverage/utang ganda).
 *
 * === PENANGANAN ANOMALY REASON ===
 * Setiap transaksi bisa terdeteksi oleh LEBIH DARI 1 pilar.
 * Reason TIDAK saling menimpa, melainkan digabung dengan separator " | "
 * sehingga credit analyst mendapat gambaran lengkap.
 * Contoh: "Window Dressing (Dana Masuk Besar) | Pinjaman Lembaga Lain (ADIRA)"
 */
@Slf4j
@Service
public class AnomalyDetectionService {

   // ═══════════════════════════════════════════════════════════════════
   // DAFTAR NAMA LEMBAGA KEUANGAN UNTUK PILAR 3
   // ═══════════════════════════════════════════════════════════════════
   // Daftar ini mencakup bank, multifinance, dan fintech lending yang
   // umum ditemukan di mutasi rekening nasabah Indonesia.
   // Menggunakan Set untuk lookup O(1).

   private static final List<String> FINANCIAL_INSTITUTION_KEYWORDS = List.of(
         // === MULTIFINANCE / LEASING ===
         // Keyword pendek (≤3 huruf) akan dicocokkan dengan WORD BOUNDARY
         // agar tidak salah match pada substring random.
         // Keyword panjang (≥5 huruf) juga mendukung FUZZY MATCH
         // untuk menangkap typo/truncation (misal: "bca financ" → "bca finance").
         "adira", "fif", "federal international finance",
         "bca finance", "bca multi finance",
         "mandiri tunas finance", "mtf",
         "mandiri utama finance", "muf",
         "oto multiartha", "oto finance",
         "summit oto finance",
         "bfi finance", "bfi indonesia",
         "clipan finance", "wahana ottomitra",
         "wom finance", "acc", "astra credit",
         "astra sedaya finance", "toyota astra financial",
         "taf", "danastra", "mitsui leasing",
         "orix indonesia", "cimb niaga auto finance",
         "mega auto finance", "mega finance",
         "mega central finance",
         "indomobil finance", "suzuki finance",
         "maybank finance", "sinarmas multifinance",
         "batavia prosperindo finance",
         "csul finance", "chandra sakti utama leasing",
         "mpm finance", "radana bhaskara finance",
         "nsc finance", "finansia multi finance",
         "kredivo", "akulaku", "home credit",

         // === BANK KOMPETITOR ===
         // Yang di-flag adalah indikasi PINJAMAN dari bank lain.
         "kredit bri", "pinjaman bri", "angsuran bri",
         "kredit bca", "pinjaman bca", "angsuran bca",
         "kredit mandiri", "pinjaman mandiri", "angsuran mandiri",
         "kredit bni", "pinjaman bni", "angsuran bni",
         "kredit cimb", "pinjaman cimb", "angsuran cimb",
         "kredit danamon", "pinjaman danamon", "angsuran danamon",
         "kredit permata", "pinjaman permata", "angsuran permata",
         "kredit panin", "pinjaman panin", "angsuran panin",
         "kredit btn", "pinjaman btn", "angsuran btn",
         "kredit ocbc", "pinjaman ocbc", "angsuran ocbc",
         "kredit maybank", "pinjaman maybank", "angsuran maybank",
         "kredit uob", "pinjaman uob", "angsuran uob",

         // === FINTECH LENDING P2P ===
         "amartha", "investree", "modalku",
         "koinworks", "danamas", "akseleran");

   // ═══════════════════════════════════════════════════════════════════
   // ENTRY POINT UTAMA
   // ═══════════════════════════════════════════════════════════════════

   /**
    * Menjalankan seluruh pipeline deteksi anomali pada daftar transaksi.
    * Setiap pilar berjalan INDEPENDEN — tidak ada early termination.
    * Jika 1 transaksi kena >1 pilar, reason-nya DIGABUNG (tidak overwrite).
    *
    * @param transactions Seluruh transaksi dalam 1 dokumen/rekening
    */
   public void detectAnomalies(List<BankTransaction> transactions) {
      log.info("Memulai deteksi anomali pada {} transaksi...", transactions.size());

      if (transactions == null || transactions.isEmpty()) {
         return;
      }

      // ─── FILTER: Hanya kategori TRANSFER yang dideteksi anomali ───
      // Transaksi berkategori TAX, ADMIN, INTEREST sudah jelas sifatnya
      // (biaya rutin bank, pajak, bunga) sehingga TIDAK perlu di-flag.
      // Hanya transaksi TRANSFER yang berpotensi mencurigakan.
      List<BankTransaction> transferOnly = transactions.stream()
            .filter(t -> t.getCategory() == TransactionCategory.TRANSFER)
            .collect(Collectors.toList());

      log.info("Dari {} total transaksi, {} berkategori TRANSFER untuk dianalisis.",
            transactions.size(), transferOnly.size());

      if (transferOnly.isEmpty()) {
         log.info("Tidak ada transaksi TRANSFER untuk dideteksi anomali.");
         return;
      }

      // Jalankan ketiga pilar secara berurutan.
      // Setiap pilar menggunakan method appendAnomalyReason() untuk
      // menambahkan reason TANPA menimpa reason dari pilar sebelumnya.

      // PILAR 1: Window Dressing — dana numpang lewat ≤ 48 jam
      detectWindowDressing(transferOnly);

      // PILAR 2: Outlier Z-Score — nominal jauh di luar kebiasaan (CR & DB terpisah)
      detectOutlierZScore(transferOnly);

      // PILAR 3: Pinjaman Bank/Leasing Lain — indikasi kewajiban di lembaga lain
      detectCompetingLenders(transferOnly);

      long totalAnomali = transferOnly.stream()
            .filter(t -> Boolean.TRUE.equals(t.getIsAnomaly()))
            .count();
      log.info("Deteksi anomali selesai. Total anomali ditemukan: {}/{}", totalAnomali, transferOnly.size());
   }

   // ═══════════════════════════════════════════════════════════════════
   // PILAR 1: DETEKSI WINDOW DRESSING
   // ═══════════════════════════════════════════════════════════════════
   //
   // KONSEP:
   // Window Dressing adalah praktik memasukkan dana besar ke rekening
   // sesaat sebelum tanggal laporan, lalu menariknya kembali setelahnya.
   // Tujuannya: membuat saldo/omzet terlihat besar di mata analis kredit.
   //
   // ALGORITMA:
   // 1. Pisahkan transaksi CR (masuk) dan DB (keluar)
   // 2. Hitung total turnover CR untuk menentukan threshold dinamis
   // 3. Threshold = 15% dari total turnover (proporsional terhadap skala usaha)
   // 4. Untuk setiap CR ≥ threshold, cari DB yang terjadi dalam ≤ 2 hari
   // dengan selisih nominal ≤ 5% dari CR tersebut
   // 5. Jika cocok, KEDUA transaksi (CR & DB) di-flag sebagai window dressing
   //
   // TOLERANSI 5%:
   // Lebih longgar dari versi lama (2%) karena di dunia nyata, pelaku
   // tidak selalu menarik dana persis sama. Ada biaya admin, bunga, dll.

   private void detectWindowDressing(List<BankTransaction> transactions) {

      // Langkah 1: Pisahkan transaksi berdasarkan tipe mutasi
      List<BankTransaction> credits = transactions.stream()
            .filter(t -> t.getMutationType() == MutationType.CR && t.getAmount() != null)
            .sorted(Comparator.comparing(BankTransaction::getTransactionDate))
            .collect(Collectors.toList());

      List<BankTransaction> debits = transactions.stream()
            .filter(t -> t.getMutationType() == MutationType.DB && t.getAmount() != null)
            .sorted(Comparator.comparing(BankTransaction::getTransactionDate))
            .collect(Collectors.toList());

      // Langkah 2: Hitung total turnover CR sebagai basis threshold dinamis
      BigDecimal totalCreditTurnover = credits.stream()
            .map(BankTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

      if (totalCreditTurnover.compareTo(BigDecimal.ZERO) == 0)
         return;

      // Langkah 3: Threshold = 15% dari total omset CR
      BigDecimal threshold = totalCreditTurnover.multiply(new BigDecimal("0.15"));

      // Set untuk melacak DB yang sudah dipasangkan, mencegah 1 DB di-match ke >1 CR
      Set<BankTransaction> matchedDebits = new HashSet<>();
      // Set untuk melacak CR yang sudah berhasil di-match
      Set<BankTransaction> matchedCredits = new HashSet<>();

      // ─────────────────────────────────────────────────────────────
      // PASS 1: Exact Pair (1 CR ↔ 1 DB)
      // ─────────────────────────────────────────────────────────────
      // Cari DB tunggal yang nominalnya mendekati CR (selisih ≤ 5%).
      // Ini menangkap window dressing pola sederhana.
      for (BankTransaction crTx : credits) {
         BigDecimal crAmt = crTx.getAmount();
         if (crAmt.compareTo(threshold) < 0)
            continue;

         for (BankTransaction dbTx : debits) {
            if (matchedDebits.contains(dbTx))
               continue;

            long daysBetween = ChronoUnit.DAYS.between(crTx.getTransactionDate(), dbTx.getTransactionDate());
            // DB harus terjadi HARI ITU JUGA (0) atau MAKSIMAL 2 HARI SETELAH (2) CR
            if (daysBetween < 0 || daysBetween > 2)
               continue;

            // Pengecekan kemiripan nama akun (counterparty)
            if (!isCounterpartySimilar(crTx.getCounterpartyName(), dbTx.getCounterpartyName())) {
               continue;
            }

            BigDecimal diff = crAmt.subtract(dbTx.getAmount()).abs();
            BigDecimal tolerance = crAmt.multiply(new BigDecimal("0.05"));

            if (diff.compareTo(tolerance) <= 0) {
               appendAnomalyReason(crTx,
                     "Window Dressing (Dana Masuk Rp " + crAmt.toPlainString()
                           + " lalu Keluar dalam 48 Jam)");
               appendAnomalyReason(dbTx,
                     "Window Dressing (Dana Ditarik Rp " + dbTx.getAmount().toPlainString()
                           + " setelah Masuk Besar)");

               matchedDebits.add(dbTx);
               matchedCredits.add(crTx);
               break;
            }
         }
      }

      // ─────────────────────────────────────────────────────────────
      // PASS 2: Split Withdrawal (1 CR ↔ banyak DB)
      // ─────────────────────────────────────────────────────────────
      // Untuk CR besar yang BELUM terdeteksi di Pass 1, cek apakah
      // ada KUMPULAN debit dalam ≤ 2 hari yang totalnya mendekati
      // nominal CR (selisih ≤ 5%).
      //
      // Contoh:
      // CR = 100 Juta (masuk)
      // DB = 20jt + 20jt + 20jt + 20jt + 20jt = 100jt (keluar dipecah)
      //
      // Ini pola window dressing yang lebih canggih karena pelaku
      // sengaja memecah penarikan agar tidak terlihat mencurigakan.
      //
      // ALGORITMA: Greedy accumulation
      // 1. Kumpulkan semua DB yang belum di-match dalam ≤ 2 hari dari CR
      // 2. Urutkan dari nominal terbesar ke terkecil
      // 3. Akumulasi sampai total mendekati nominal CR (± 5%)
      // 4. Jika tercapai → flag semua DB yang terlibat + CR-nya

      for (BankTransaction crTx : credits) {
         if (matchedCredits.contains(crTx))
            continue; // Sudah kena di Pass 1
         BigDecimal crAmt = crTx.getAmount();
         if (crAmt.compareTo(threshold) < 0)
            continue;

         // Kumpulkan semua DB kandidat dalam ≤ 2 hari yang belum di-match
         List<BankTransaction> candidates = new ArrayList<>();
         for (BankTransaction dbTx : debits) {
            if (matchedDebits.contains(dbTx))
               continue;
            long daysBetween = ChronoUnit.DAYS.between(crTx.getTransactionDate(), dbTx.getTransactionDate());
            // Hanya kumpulkan debit yang terjadi di rentang hari yang sama sampai 2 hari
            // setelah CR
            if (daysBetween >= 0 && daysBetween <= 2) {
               // Pastikan nama pihak lawan mirip
               if (isCounterpartySimilar(crTx.getCounterpartyName(), dbTx.getCounterpartyName())) {
                  candidates.add(dbTx);
               }
            }
         }

         // Butuh minimal 2 DB agar dianggap "split" (1 DB sudah dicek di Pass 1)
         if (candidates.size() < 2)
            continue;

         // Urutkan dari nominal terbesar → greedy accumulation
         candidates.sort(Comparator.comparing(BankTransaction::getAmount).reversed());

         BigDecimal runningSum = BigDecimal.ZERO;
         List<BankTransaction> accumulated = new ArrayList<>();
         BigDecimal tolerance = crAmt.multiply(new BigDecimal("0.05"));

         for (BankTransaction dbCandidate : candidates) {
            // Jangan akumulasi jika sudah melebihi CR + tolerance
            if (runningSum.add(dbCandidate.getAmount()).compareTo(crAmt.add(tolerance)) > 0) {
               continue; // Skip DB ini, coba yang lebih kecil
            }

            runningSum = runningSum.add(dbCandidate.getAmount());
            accumulated.add(dbCandidate);

            // Cek apakah total sudah mendekati CR (± 5%)
            BigDecimal diff = crAmt.subtract(runningSum).abs();
            if (diff.compareTo(tolerance) <= 0) {
               // MATCH! Flag CR dan semua DB yang terlibat
               appendAnomalyReason(crTx,
                     "Window Dressing - Split Withdrawal (Dana Masuk Rp " + crAmt.toPlainString()
                           + " lalu Ditarik Bertahap " + accumulated.size()
                           + " transaksi dalam 48 Jam)");

               for (BankTransaction matched : accumulated) {
                  appendAnomalyReason(matched,
                        "Window Dressing - Split Withdrawal (Bagian penarikan bertahap dari Rp "
                              + crAmt.toPlainString() + ")");
                  matchedDebits.add(matched);
               }
               matchedCredits.add(crTx);
               break; // Berhenti akumulasi, CR ini sudah ter-match
            }
         }
      }
   }

   // ═══════════════════════════════════════════════════════════════════
   // PILAR 2: DETEKSI OUTLIER DENGAN Z-SCORE
   // ═══════════════════════════════════════════════════════════════════
   //
   // Menggunakan metode Z-Score untuk mengukur seberapa jauh sebuah nominal
   // menyimpang dari rata-rata (mean) dalam satuan Standar Deviasi (StdDev).

   private void detectOutlierZScore(List<BankTransaction> transactions) {

      // Pisahkan amount berdasarkan tipe mutasi
      List<BigDecimal> creditAmounts = transactions.stream()
            .filter(t -> t.getMutationType() == MutationType.CR && t.getAmount() != null)
            .map(BankTransaction::getAmount)
            .collect(Collectors.toList());

      List<BigDecimal> debitAmounts = transactions.stream()
            .filter(t -> t.getMutationType() == MutationType.DB && t.getAmount() != null)
            .map(BankTransaction::getAmount)
            .collect(Collectors.toList());

      // Hitung Mean dan Standar Deviasi
      BigDecimal creditMean = calculateMean(creditAmounts);
      BigDecimal creditStdDev = calculateStdDev(creditAmounts, creditMean);

      BigDecimal debitMean = calculateMean(debitAmounts);
      BigDecimal debitStdDev = calculateStdDev(debitAmounts, debitMean);

      // Batas Z-Score (biasanya 3.0 dianggap sebagai extreme outlier)
      BigDecimal zScoreThreshold = new BigDecimal("3.0");
      // Batas minimal mutlak untuk dianggap outlier (misal: 1 juta Rupiah)
      BigDecimal absoluteMinThreshold = new BigDecimal("1000000");

      for (BankTransaction tx : transactions) {
         if (tx.getAmount() == null)
            continue;

         BigDecimal mean = (tx.getMutationType() == MutationType.CR) ? creditMean : debitMean;
         BigDecimal stdDev = (tx.getMutationType() == MutationType.CR) ? creditStdDev : debitStdDev;

         // Jika data tidak cukup atau deviasi = 0
         if (mean == null || stdDev == null || stdDev.compareTo(BigDecimal.ZERO) == 0)
            continue;

         // Hitung Z-Score = |(X - Mean)| / StdDev
         BigDecimal diff = tx.getAmount().subtract(mean).abs();
         BigDecimal zScore = diff.divide(stdDev, 4, java.math.RoundingMode.HALF_UP);

         if (zScore.compareTo(zScoreThreshold) > 0 && tx.getAmount().compareTo(absoluteMinThreshold) >= 0) {
            appendAnomalyReason(tx,
                  "Outlier Transaksi (Z-Score: " + zScore.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                        + " melampaui batas " + zScoreThreshold.toPlainString() + " StdDev untuk profil "
                        + (tx.getMutationType() == MutationType.CR ? "Kredit" : "Debit")
                        + " nasabah)");
         }
      }
   }

   /**
    * Menghitung nilai Rata-rata (Mean).
    * Rumus: (Total Semua Nominal) / (Jumlah Transaksi)
    */
   private BigDecimal calculateMean(List<BigDecimal> amounts) {
      if (amounts == null || amounts.isEmpty())
         return null;
      // Menjumlahkan semua nominal dalam list
      BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
      // Membagi total dengan jumlah elemen (menggunakan skala 4 desimal)
      return sum.divide(new BigDecimal(amounts.size()), 4, java.math.RoundingMode.HALF_UP);
   }

   /**
    * Menghitung nilai Standar Deviasi (Sample Standard Deviation).
    * Berfungsi mengukur seberapa jauh sebaran data menyimpang dari rata-rata.
    * Rumus: Akar Kuadrat dari [ Total((Nominal - Rata_rata)^2) / (N - 1) ]
    */
   private BigDecimal calculateStdDev(List<BigDecimal> amounts, BigDecimal mean) {
      // Butuh minimal 2 data, jika 1 data maka tidak ada penyebaran (deviasi)
      if (amounts == null || amounts.size() < 2 || mean == null)
         return null;

      BigDecimal varianceSum = BigDecimal.ZERO;

      // Langkah 1: Hitung kuadrat selisih setiap nominal terhadap rata-rata
      for (BigDecimal amt : amounts) {
         BigDecimal diff = amt.subtract(mean);
         // (X - Mean) ^ 2
         varianceSum = varianceSum.add(diff.multiply(diff));
      }

      // Langkah 2: Hitung Varians (dibagi N-1 untuk Sample Standard Deviation)
      BigDecimal variance = varianceSum.divide(new BigDecimal(amounts.size() - 1), 10, java.math.RoundingMode.HALF_UP);

      // Langkah 3: Standar Deviasi adalah akar kuadrat dari Varians
      // Membutuhkan Java 9+ untuk BigDecimal.sqrt()
      return variance.sqrt(new java.math.MathContext(10, java.math.RoundingMode.HALF_UP));
   }

   // ═══════════════════════════════════════════════════════════════════
   // PILAR 3: DETEKSI PINJAMAN DARI BANK / LEASING LAIN
   // ═══════════════════════════════════════════════════════════════════
   //
   // KONTEKS BISNIS:
   // Dalam analisis kredit, mengetahui apakah nasabah memiliki kewajiban
   // finansial di lembaga lain sangat penting untuk menilai:
   // - Debt Service Ratio (DSR) → apakah penghasilan cukup bayar semua cicilan
   // - Risiko over-leverage → terlalu banyak utang dari berbagai sumber
   // - Gali lubang tutup lubang → pinjam dari A untuk bayar B
   //
   // ALGORITMA:
   // Scan deskripsi transaksi (raw + normalized) terhadap daftar nama
   // lembaga keuangan yang komprehensif. Jika ditemukan, flag sebagai
   // anomali dengan menyebutkan nama lembaga yang terdeteksi.
   //
   // FOKUS PADA DEBIT (DB):
   // Transaksi DB ke lembaga keuangan = pembayaran cicilan/angsuran.
   // Transaksi CR dari lembaga keuangan = pencairan pinjaman baru.
   // Keduanya penting, tapi reason-nya dibedakan untuk kejelasan.

   private void detectCompetingLenders(List<BankTransaction> transactions) {
      for (BankTransaction tx : transactions) {
         // Gabungkan semua teks deskripsi untuk pencarian yang menyeluruh
         String desc = buildSearchableText(tx);
         if (desc.isEmpty())
            continue;

         // Cek setiap keyword lembaga keuangan dengan fuzzy matching
         for (String keyword : FINANCIAL_INSTITUTION_KEYWORDS) {
            if (fuzzyMatchKeyword(desc, keyword)) {
               // Capitalize nama lembaga untuk tampilan yang rapi
               String lembaga = keyword.toUpperCase();

               if (tx.getMutationType() == MutationType.DB) {
                  // DB = pembayaran ke lembaga lain (cicilan/angsuran)
                  appendAnomalyReason(tx,
                        "Pembayaran ke Lembaga Keuangan (" + lembaga + ")");
               } else {
                  // CR = pencairan dana dari lembaga lain (pinjaman baru)
                  appendAnomalyReason(tx,
                        "Pencairan dari Lembaga Keuangan (" + lembaga + ")");
               }
               // Break setelah match pertama agar tidak double-flag
               break;
            }
         }
      }
   }

   // ═══════════════════════════════════════════════════════════════════
   // UTILITY METHODS
   // ═══════════════════════════════════════════════════════════════════

   /**
    * Fuzzy matching keyword terhadap teks deskripsi transaksi.
    *
    * STRATEGI MATCHING BERTINGKAT:
    *
    * 1. KEYWORD PENDEK (≤ 3 karakter) → contoh: "mtf", "acc", "fif", "taf"
    * Menggunakan WORD BOUNDARY matching.
    * Keyword harus muncul sebagai kata utuh, bukan bagian dari kata lain.
    * Contoh:
    * "mtf" MATCH di "pembayaran mtf oktober" → ✅ (kata utuh)
    * "acc" TIDAK MATCH di "account transfer" → ❌ (bagian dari "account")
    * "acc" MATCH di "bayar acc cicilan" → ✅ (kata utuh)
    *
    * 2. KEYWORD PANJANG (≥ 5 karakter) → contoh: "bca finance", "adira"
    * Menggunakan EXACT + TRUNCATION matching.
    * Selain exact match, juga menangkap keyword yang terpotong
    * 1-2 karakter terakhir (umum terjadi karena format PDF/OCR).
    * Contoh:
    * "bca finance" MATCH di "bca financ" → ✅ (terpotong 1 huruf)
    * "bca finance" MATCH di "bca finane" → ❌ (bukan truncation)
    * "adira" MATCH di "adir" → ✅ (terpotong 1 huruf)
    *
    * 3. KEYWORD 4 KARAKTER → exact substring match saja (default).
    *
    * @param text    Teks deskripsi transaksi (lowercase)
    * @param keyword Keyword lembaga keuangan (lowercase)
    * @return true jika keyword cocok dengan teks
    */
   private boolean fuzzyMatchKeyword(String text, String keyword) {
      if (text == null || keyword == null)
         return false;

      // === STRATEGI 1: Keyword pendek → word boundary ===
      if (keyword.length() <= 3) {
         return matchWithWordBoundary(text, keyword);
      }

      // === STRATEGI 2: Exact match dulu (selalu dicek) ===
      if (text.contains(keyword)) {
         return true;
      }

      // === STRATEGI 3: Truncation match untuk keyword ≥ 5 karakter ===
      // Menangkap keyword yang terpotong 1-2 huruf terakhir di hasil OCR/PDF.
      // PENTING: Menggunakan word boundary agar prefix yang terpotong tidak
      // salah match di tengah kata lain.
      // Contoh: "kredivo" → "kredi" TIDAK boleh match di "kredit lokal"
      // "bca finance" → "bca financ" BOLEH match (kata terpotong di akhir)
      if (keyword.length() >= 5) {
         String minus1 = keyword.substring(0, keyword.length() - 1);
         if (matchTruncatedAtWordEnd(text, minus1))
            return true;

         if (keyword.length() >= 7) {
            String minus2 = keyword.substring(0, keyword.length() - 2);
            if (matchTruncatedAtWordEnd(text, minus2))
               return true;
         }
      }

      return false;
   }

   /**
    * Mengecek apakah keyword muncul sebagai KATA UTUH dalam teks.
    * Kata utuh = dikelilingi oleh spasi, awal/akhir string, atau karakter
    * non-alfanumerik.
    *
    * Ini penting untuk keyword pendek seperti "mtf", "acc", "fif" agar tidak
    * salah match pada kata yang mengandung substring tersebut.
    *
    * Contoh:
    * matchWithWordBoundary("bayar acc oktober", "acc") → true (kata utuh)
    * matchWithWordBoundary("account transfer", "acc") → false (bagian kata lain)
    */
   private boolean matchWithWordBoundary(String text, String keyword) {
      int idx = 0;
      while ((idx = text.indexOf(keyword, idx)) != -1) {
         boolean startOk = (idx == 0) || !Character.isLetterOrDigit(text.charAt(idx - 1));
         int endIdx = idx + keyword.length();
         boolean endOk = (endIdx >= text.length()) || !Character.isLetterOrDigit(text.charAt(endIdx));

         if (startOk && endOk) {
            return true;
         }
         idx++;
      }
      return false;
   }

   /**
    * Mengecek apakah prefix (keyword terpotong) ditemukan di teks DAN
    * diakhiri oleh word boundary (spasi, akhir string, atau non-alfanumerik).
    *
    * Ini mencegah false positive dimana prefix terpotong kebetulan
    * muncul sebagai awal kata lain yang tidak terkait.
    *
    * Contoh:
    * "bca financ" di "pembayaran bca financ oktober" → true (akhir di spasi)
    * "kredi" di "bunga kredit lokal" → false ('t' mengikuti)
    */
   private boolean matchTruncatedAtWordEnd(String text, String prefix) {
      int idx = 0;
      while ((idx = text.indexOf(prefix, idx)) != -1) {
         int endIdx = idx + prefix.length();
         // Prefix harus diakhiri boundary: akhir string ATAU karakter non-alfanumerik
         boolean endOk = (endIdx >= text.length()) || !Character.isLetterOrDigit(text.charAt(endIdx));
         if (endOk) {
            return true;
         }
         idx++;
      }
      return false;
   }

   /**
    * Mengecek apakah dua nama counterparty (pihak lawan) mirip.
    * Berguna untuk mendeteksi Window Dressing dengan nama akun yang hampir sama
    * (misal: "Agung Ramadhani" vs "Agung Ramadhan").
    */
   private boolean isCounterpartySimilar(String name1, String name2) {
      // Jika salah satu tidak ada, kita asumsikan BISA JADI window dressing (fallback
      // ke cek nominal)
      // agar tidak kehilangan deteksi murni karena parser gagal baca nama di PDF.
      if (name1 == null || name2 == null || name1.isBlank() || name2.isBlank()) {
         return true;
      }

      String n1 = name1.toLowerCase().replaceAll("[^a-z0-9]", " ").trim();
      String n2 = name2.toLowerCase().replaceAll("[^a-z0-9]", " ").trim();

      // Cek kemiripan string dasar
      if (n1.equals(n2))
         return true;
      if (n1.contains(n2) || n2.contains(n1))
         return true;

      // Cek kemiripan per-kata (Token Overlap & Levenshtein)
      Set<String> tokens1 = new HashSet<>(Arrays.asList(n1.split("\\s+")));
      Set<String> tokens2 = new HashSet<>(Arrays.asList(n2.split("\\s+")));

      // Abaikan gelar/prefix umum
      List<String> ignoreWords = Arrays.asList("pt", "cv", "tbk", "bapak", "ibu", "sdr", "sdri", "transfer", "ke",
            "dari");
      tokens1.removeAll(ignoreWords);
      tokens2.removeAll(ignoreWords);

      for (String t1 : tokens1) {
         if (t1.length() < 4)
            continue; // Abaikan kata terlalu pendek (misal: "tf", "dr")
         for (String t2 : tokens2) {
            if (t2.length() < 4)
               continue;

            // Match jika kata saling jadi prefix atau selisih 1 huruf (typo/suffix)
            if (t1.startsWith(t2) || t2.startsWith(t1) || computeLevenshteinDistance(t1, t2) <= 1) {
               return true;
            }
         }
      }
      return false;
   }

   /**
    * Menghitung Jarak Levenshtein antara dua string.
    * Menghitung berapa banyak operasi (sisip, hapus, ganti huruf)
    * yang dibutuhkan untuk mengubah string 1 menjadi string 2.
    * Contoh: "Agung" dan "Agun" punya jarak 1 (karena 1 huruf hilang).
    */
   private int computeLevenshteinDistance(String s1, String s2) {
      // Membuat tabel matriks untuk dynamic programming
      int[][] dp = new int[s1.length() + 1][s2.length() + 1];

      // Mengisi matriks secara iteratif
      for (int i = 0; i <= s1.length(); i++) {
         for (int j = 0; j <= s2.length(); j++) {
            if (i == 0) {
               // Jika s1 kosong, butuh j operasi penyisipan
               dp[i][j] = j;
            } else if (j == 0) {
               // Jika s2 kosong, butuh i operasi penghapusan
               dp[i][j] = i;
            } else {
               // Jika huruf sama, biaya (cost) = 0, jika beda cost = 1
               int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;

               // Cari operasi termurah:
               // 1. Hapus (dp[i-1][j] + 1)
               // 2. Sisip (dp[i][j-1] + 1)
               // 3. Ganti huruf (dp[i-1][j-1] + cost)
               dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
         }
      }
      // Kembalikan nilai di pojok kanan bawah matriks (total jarak minimum)
      return dp[s1.length()][s2.length()];
   }

   /**
    * Menggabungkan anomalyReason TANPA menimpa reason sebelumnya.
    * Jika transaksi sudah punya reason dari pilar lain, reason baru
    * ditambahkan dengan separator " | ".
    */
   private void appendAnomalyReason(BankTransaction tx, String newReason) {
      tx.setIsAnomaly(true);

      String existing = tx.getAnomalyReason();
      if (existing == null || existing.isBlank()) {
         tx.setAnomalyReason(newReason);
      } else {
         tx.setAnomalyReason(existing + " | " + newReason);
      }
   }

   /**
    * Membangun teks yang bisa dicari dari seluruh field deskripsi transaksi.
    * Menggabungkan rawDescription, normalizedDescription, dan counterpartyName
    * dalam lowercase untuk pencarian case-insensitive.
    */
   private String buildSearchableText(BankTransaction tx) {
      StringBuilder sb = new StringBuilder();
      if (tx.getRawDescription() != null)
         sb.append(tx.getRawDescription().toLowerCase()).append(" ");
      if (tx.getNormalizedDescription() != null)
         sb.append(tx.getNormalizedDescription().toLowerCase()).append(" ");
      if (tx.getCounterpartyName() != null)
         sb.append(tx.getCounterpartyName().toLowerCase());
      return sb.toString().trim();
   }
}
