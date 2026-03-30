package com.example.mutexa_be.service;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.enums.MutationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AnomalyDetectionService {

   /**
    * Memeriksa seluruh transaksi yang baru diekstrak dan mendeteksi anomali.
    * Fungsi ini tidak mengubah kategori, HANYA memberikan flag isAnomaly = true
    * dan menyematkan anomalyReason jika terdeteksi hal mencurigakan.
    *
    * @param transactions List transaksi dalam 1 rekening/dokumen
    */
   public void detectAnomalies(List<BankTransaction> transactions) {
      log.info("Memulai proses deteksi anomali pada {} transaksi...", transactions.size());

      if (transactions == null || transactions.isEmpty()) {
         return;
      }

      // 1. Deteksi Kata Kunci Pembatalan / Retur
      detectKeywordAnomalies(transactions);

      // 2. Deteksi Nilai Bulat yang Ekstrem (Round Number Anomaly - Indikasi Suntikan
      // Modal Palsu)
      detectRoundNumberAnomalies(transactions);

      // 3. Deteksi Window Dressing (Masuk Besar, Keluar Besar dalam waktu sangat
      // dekat)
      detectWindowDressing(transactions);

      // 4. Deteksi Outlier Ekstrem (Z-Score Analysis) untuk mutasi yang jauh di luar
      // kebiasaan
      detectZScoreAnomalies(transactions);

      log.info("Proses deteksi anomali selesai.");
   }

   /**
    * Algoritma 1: Mendeteksi pembatalan atau retur bank yang mencurigakan.
    * Credit Analyst sering curiga jika banyak retur, menandakan operasional tidak
    * stabil.
    */
   private void detectKeywordAnomalies(List<BankTransaction> transactions) {
      for (BankTransaction tx : transactions) {
         if (tx.getIsAnomaly() != null && tx.getIsAnomaly())
            continue; // Skip jika sudah jadi anomali

         String desc = tx.getNormalizedDescription() != null ? tx.getNormalizedDescription().toLowerCase() : "";
         String rawDesc = tx.getRawDescription() != null ? tx.getRawDescription().toLowerCase() : "";
         String textToSearch = desc + " " + rawDesc;

         if (containsKeyword(textToSearch, "koreksi", "reversal", "retur", "batal", "cancel")) {
            tx.setIsAnomaly(true);
            tx.setAnomalyReason("Indikasi Reversal Sistem (Koreksi Mutasi)");
         }
      }
   }

   /**
    * Algoritma 2: Mendeteksi transaksi nominal bulat yang SANGAT BESAR.
    * Menggunakan Profiling Dinamis (Dynamic Thresholding).
    * Jika sebuah mutasi angkanya bulat sempurna (kelipatan 10 juta)
    * DAN nilainya lebih dari 2X lipat rata-rata mutasi nasabah tersebut, maka
    * dicurigai sebagai suntikan.
    */
   private void detectRoundNumberAnomalies(List<BankTransaction> transactions) {
      if (transactions == null || transactions.isEmpty())
         return;

      // 1. Hitung dulu rata-rata mutasi (Mean Turnover) nasabah ini
      BigDecimal sumAmount = transactions.stream()
            .filter(t -> t.getAmount() != null)
            .map(BankTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

      long countValid = transactions.stream()
            .filter(t -> t.getAmount() != null)
            .count();

      if (countValid == 0)
         return;

      // Rata-rata mutasi nasabah (contoh: rata-rata transfer sehari-harinya 5 juta)
      BigDecimal meanAmount = sumAmount.divide(new BigDecimal(countValid), java.math.RoundingMode.HALF_UP);

      // Threshold dinamis = 2x dari rata-rata (jadi kalau rata2 5jt, masuk 11jt mulai
      // disorot)
      BigDecimal dynamicThreshold = meanAmount.multiply(new BigDecimal("2.0"));

      for (BankTransaction tx : transactions) {
         if (tx.getIsAnomaly() != null && tx.getIsAnomaly())
            continue;

         BigDecimal amount = tx.getAmount();

         // Cek apakah amount melebihi threshold dinamis DAN merupakan bilangan bulat
         // puluhan juta
         // (amount % 10.000.000 == 0)
         if (amount != null && amount.compareTo(dynamicThreshold) >= 0) {
            // Untuk mencegah terflagging angka kecil, kita patok nominal wajar harus > 10
            // Juta minimal
            if (amount.compareTo(new BigDecimal("10000000")) >= 0) {
               BigDecimal tenMillion = new BigDecimal("10000000"); // Kelipatan 10 Juta yang bulat
               if (amount.remainder(tenMillion).compareTo(BigDecimal.ZERO) == 0) {
                  tx.setIsAnomaly(true);
                  tx.setAnomalyReason(String.format(
                        "Indikasi Suntikan Modal Palsu (Nominal Bulat %s sangat melebihi rutinitas profil Rp %s)",
                        amount.toPlainString(), meanAmount.toPlainString()));
               }
            }
         }
      }
   }

   /**
    * Algoritma 3: Mendeteksi Window Dressing dengan Dynamic Turnover.
    * Kondisi: Uang masuk (CR) lalu ditarik hampir habis (DB) dalam waktu < 48 Jam.
    * Hanya akan menyorot transaksi yang besarnya mencapai 20% dari Total
    * Perputaran Uang nasabah.
    */
   private void detectWindowDressing(List<BankTransaction> transactions) {
      // Pisahkan Credit dan Debit untuk dicocokkan
      List<BankTransaction> credits = transactions.stream()
            .filter(t -> t.getMutationType() == MutationType.CR)
            .collect(Collectors.toList());

      List<BankTransaction> debits = transactions.stream()
            .filter(t -> t.getMutationType() == MutationType.DB)
            .collect(Collectors.toList());

      // Hitung Total Kredit (Berapa omset/uang masuk selama 1 bulan ini)
      BigDecimal totalCreditTurnover = credits.stream()
            .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

      // Jika total omset kosong, abaikan
      if (totalCreditTurnover.compareTo(BigDecimal.ZERO) == 0)
         return;

      // Threshold Window Dressing = 20% dari total Omset.
      // Jika Total omset 100 Juta, uang numpang lewat 20jt akan ditangkap.
      // Jika Total omset 10 Miliar, uang numpang lewat harus min 2 Miliar baru
      // ditangkap.
      BigDecimal dynamicLargeThreshold = totalCreditTurnover.multiply(new BigDecimal("0.20"));

      for (BankTransaction crTx : credits) {
         BigDecimal crAmt = crTx.getAmount();
         // Loloskan screening jika nilai masuknya di bawah 20% Omset bulanannya
         if (crAmt == null || crAmt.compareTo(dynamicLargeThreshold) < 0)
            continue;

         for (BankTransaction dbTx : debits) {
            // Jangan cek jika hari-nya terlalu jauh (selisih > 2 hari)
            long daysBetween = Math.abs(ChronoUnit.DAYS.between(crTx.getTransactionDate(), dbTx.getTransactionDate()));
            if (daysBetween > 2)
               continue; // Hanya peduli dana numpang lewat 0-2 hari

            BigDecimal dbAmt = dbTx.getAmount();
            if (dbAmt == null)
               continue;

            // Hitung selisih mutasi
            BigDecimal diffRaw = crAmt.subtract(dbAmt).abs();

            // Jika selisihnya kurang dari 2% dari saldo masuk, berarti debitnya hampir
            // menguras persis uang masuk tadi
            BigDecimal twoPercent = crAmt.multiply(new BigDecimal("0.02"));

            if (diffRaw.compareTo(twoPercent) <= 0) {
               // Tandai KEDUANYA sebagai anomali window dressing!
               crTx.setIsAnomaly(true);
               crTx.setAnomalyReason(
                     "Indikasi Window Dressing (Dana Masuk > 20% Omset, lalu Keluar Cepat dalam 48 Jam)");

               dbTx.setIsAnomaly(true);
               dbTx.setAnomalyReason("Indikasi Window Dressing (Uang Ditarik Cepat setelah Masuk Besar)");

               // Break agar tidak men-flag banyak debit dari 1 credit
               break;
            }
         }
      }
   }

   /**
    * Algoritma 4: Mendeteksi Anomali Z-Score (Statistika).
    * Mencari transaksi yang jumlahnya sangat jauh secara statistik
    * dari rata-rata transaksi lain di rekening tersebut (Z-Score > 3).
    */
   private void detectZScoreAnomalies(List<BankTransaction> transactions) {
      if (transactions.size() < 10)
         return; // Butuh sampel cukup agar statisik valid

      // Hitung Rata-Rata (Mean / mu)
      double sum = 0;
      for (BankTransaction tx : transactions) {
         if (tx.getAmount() != null) {
            sum += tx.getAmount().doubleValue();
         }
      }
      double mean = sum / transactions.size();

      // Hitung Standar Deviasi (Standard Deviation / sigma)
      double sumSquaredDiffs = 0;
      for (BankTransaction tx : transactions) {
         if (tx.getAmount() != null) {
            double diff = tx.getAmount().doubleValue() - mean;
            sumSquaredDiffs += diff * diff;
         }
      }
      double variance = sumSquaredDiffs / transactions.size();
      double stdDev = Math.sqrt(variance);

      // Jika deviasi standarnya 0 (semua transaksi nilainya kembar), skip
      if (stdDev == 0)
         return;

      // Hitung Z-Score tiap baris dan beri flag jika > 3
      for (BankTransaction tx : transactions) {
         if (tx.getIsAnomaly() != null && tx.getIsAnomaly())
            continue; // Skip jk sdh kena anomal lain

         if (tx.getAmount() != null) {
            double zScore = Math.abs((tx.getAmount().doubleValue() - mean) / stdDev);

            // Aturan Umum Statistika: Data dengan Z-Score >= 3.0 adalah Outlier / Anomali
            // Ekstrim
            if (zScore > 3.0) {
               tx.setIsAnomaly(true);
               String reason = String.format("Outlier Transaksi Ekstrem (Mutasi %.1fx lebih tinggi dari rata-rata)",
                     tx.getAmount().doubleValue() / mean);
               tx.setAnomalyReason(reason);
            }
         }
      }
   }

   private boolean containsKeyword(String text, String... keywords) {
      if (text == null)
         return false;
      for (String keyword : keywords) {
         if (text.contains(keyword.toLowerCase())) {
            return true;
         }
      }
      return false;
   }
}
