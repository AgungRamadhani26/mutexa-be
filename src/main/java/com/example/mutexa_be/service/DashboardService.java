package com.example.mutexa_be.service;

import com.example.mutexa_be.dto.response.DetailTransaksiResponse;
import com.example.mutexa_be.dto.response.PengendapanBulanResponse;
import com.example.mutexa_be.dto.response.PengendapanResponse;
import com.example.mutexa_be.dto.response.PengendapanRowResponse;
import com.example.mutexa_be.dto.response.RingkasanSaldoResponse;
import com.example.mutexa_be.dto.response.SummaryPerbulanResponse;
import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.enums.MutationType;
import com.example.mutexa_be.repository.BankTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;
import com.example.mutexa_be.dto.response.TopFreqResponse;

@Service
@RequiredArgsConstructor
public class DashboardService {

   private final BankTransactionRepository bankTransactionRepository;

   public List<SummaryPerbulanResponse> getSummaryPerbulan(Long documentId) {
      List<Object[]> rawSummaries = bankTransactionRepository.getMonthlySummaryByDocumentId(documentId);

      return rawSummaries.stream().map(row -> {
         Integer year = ((Number) row[0]).intValue();
         Integer month = ((Number) row[1]).intValue();
         BigDecimal totalCredit = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
         BigDecimal totalDebit = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
         Long freqCredit = row[4] != null ? ((Number) row[4]).longValue() : 0L;
         Long freqDebit = row[5] != null ? ((Number) row[5]).longValue() : 0L;
         BigDecimal saldoAkhir = row[6] != null ? new BigDecimal(row[6].toString()) : BigDecimal.ZERO;
         BigDecimal cleanedTotalCredit = row[7] != null ? new BigDecimal(row[7].toString()) : BigDecimal.ZERO;
         BigDecimal cleanedTotalDebit = row[8] != null ? new BigDecimal(row[8].toString()) : BigDecimal.ZERO;
         Long cleanedFreqCredit = row[9] != null ? ((Number) row[9]).longValue() : 0L;
         Long cleanedFreqDebit = row[10] != null ? ((Number) row[10]).longValue() : 0L;

         String monthName = Month.of(month).getDisplayName(TextStyle.FULL, new Locale("id", "ID"));
         String periode = monthName + " " + year;

         return SummaryPerbulanResponse.builder()
               .periode(periode)
               .totalCredit(totalCredit)
               .totalDebit(totalDebit)
               .cleanedTotalCredit(cleanedTotalCredit)
               .cleanedTotalDebit(cleanedTotalDebit)
               .freqCredit(freqCredit)
               .freqDebit(freqDebit)
               .cleanedFreqCredit(cleanedFreqCredit)
               .cleanedFreqDebit(cleanedFreqDebit)
               .saldoAkhir(saldoAkhir)
               .build();
      }).collect(Collectors.toList());
   }

   public RingkasanSaldoResponse getRingkasanSaldo(Long documentId) {
      List<Object[]> rows = bankTransactionRepository.getRingkasanSaldoByDocumentId(documentId);

      BigDecimal totalCredit = BigDecimal.ZERO;
      BigDecimal totalDebit = BigDecimal.ZERO;
      BigDecimal avgCredit = BigDecimal.ZERO;
      BigDecimal avgDebit = BigDecimal.ZERO;
      int jumlahBulan = 0;

      BigDecimal cleanedTotalCredit = BigDecimal.ZERO;
      BigDecimal cleanedTotalDebit = BigDecimal.ZERO;
      BigDecimal cleanedAvgCredit = BigDecimal.ZERO;
      BigDecimal cleanedAvgDebit = BigDecimal.ZERO;

      if (!rows.isEmpty() && rows.get(0) != null) {
         Object[] row = rows.get(0);
         totalCredit = row[0] != null ? new BigDecimal(row[0].toString()) : BigDecimal.ZERO;
         totalDebit = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
         jumlahBulan = row[2] != null ? ((Number) row[2]).intValue() : 1;

         cleanedTotalCredit = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
         cleanedTotalDebit = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;

         if (jumlahBulan > 0) {
            BigDecimal bJumlahBulan = BigDecimal.valueOf(jumlahBulan);
            avgCredit = totalCredit.divide(bJumlahBulan, 2, java.math.RoundingMode.HALF_UP);
            avgDebit = totalDebit.divide(bJumlahBulan, 2, java.math.RoundingMode.HALF_UP);

            cleanedAvgCredit = cleanedTotalCredit.divide(bJumlahBulan, 2, java.math.RoundingMode.HALF_UP);
            cleanedAvgDebit = cleanedTotalDebit.divide(bJumlahBulan, 2, java.math.RoundingMode.HALF_UP);
         }
      }

      return RingkasanSaldoResponse.builder()
            .totalCredit(totalCredit)
            .totalDebit(totalDebit)
            .avgCredit(avgCredit)
            .avgDebit(avgDebit)
            .jumlahBulan(jumlahBulan)
            .cleanedTotalCredit(cleanedTotalCredit)
            .cleanedTotalDebit(cleanedTotalDebit)
            .cleanedAvgCredit(cleanedAvgCredit)
            .cleanedAvgDebit(cleanedAvgDebit)
            .build();
   }

   public List<DetailTransaksiResponse> getDetailSemuaTransaksi(Long documentId) {
      List<BankTransaction> transactions = bankTransactionRepository
            .findAllByMutationDocumentIdOrderByTransactionDateAscIdAsc(documentId);
      return transactions.stream().map(tx -> DetailTransaksiResponse.builder()
            .id(tx.getId())
            .tanggal(tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null)
            .keterangan(tx.getNormalizedDescription() != null ? tx.getNormalizedDescription() : tx.getRawDescription())
            .flag(tx.getMutationType() != null ? tx.getMutationType().name() : "N/A")
            .jumlah(tx.getAmount())
            .saldo(tx.getBalance())
            .isExcluded(tx.getIsExcluded())
            .category(tx.getCategory() != null ? tx.getCategory().name() : "TRANSFER")
            .anomalyReason(tx.getAnomalyReason())
            .build()).collect(Collectors.toList());
   }

   public List<DetailTransaksiResponse> getTransactionsByCategory(Long documentId,
         com.example.mutexa_be.entity.enums.TransactionCategory category) {
      List<BankTransaction> transactions = bankTransactionRepository
            .findAllByMutationDocumentIdAndCategoryOrderByTransactionDateAscIdAsc(documentId, category);
      return transactions.stream().map(tx -> DetailTransaksiResponse.builder()
            .id(tx.getId())
            .tanggal(tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null)
            .keterangan(tx.getNormalizedDescription() != null ? tx.getNormalizedDescription() : tx.getRawDescription())
            .flag(tx.getMutationType() != null ? tx.getMutationType().name() : "N/A")
            .jumlah(tx.getAmount())
            .saldo(tx.getBalance())
            .isExcluded(tx.getIsExcluded())
            .category(tx.getCategory() != null ? tx.getCategory().name() : "TRANSFER")
            .anomalyReason(tx.getAnomalyReason())
            .build()).collect(Collectors.toList());
   }

   public List<DetailTransaksiResponse> getTop10CreditAmount(Long documentId) {
      List<BankTransaction> transactions = bankTransactionRepository
            .findTop10ByMutationDocumentIdAndMutationTypeOrderByAmountDesc(
                  documentId, com.example.mutexa_be.entity.enums.MutationType.CR);
      return transactions.stream().map(tx -> DetailTransaksiResponse.builder()
            .id(tx.getId())
            .tanggal(tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null)
            .keterangan(tx.getNormalizedDescription() != null ? tx.getNormalizedDescription() : tx.getRawDescription())
            .flag(tx.getMutationType() != null ? tx.getMutationType().name() : "N/A")
            .jumlah(tx.getAmount())
            .isExcluded(tx.getIsExcluded())
            .category(tx.getCategory() != null ? tx.getCategory().name() : "TRANSFER")
            .anomalyReason(tx.getAnomalyReason())
            .build()).collect(Collectors.toList());
   }

   public List<DetailTransaksiResponse> getTop10DebitAmount(Long documentId) {
      List<BankTransaction> transactions = bankTransactionRepository
            .findTop10ByMutationDocumentIdAndMutationTypeOrderByAmountDesc(
                  documentId, com.example.mutexa_be.entity.enums.MutationType.DB);
      return transactions.stream().map(tx -> DetailTransaksiResponse.builder()
            .id(tx.getId())
            .tanggal(tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null)
            .keterangan(tx.getNormalizedDescription() != null ? tx.getNormalizedDescription() : tx.getRawDescription())
            .flag(tx.getMutationType() != null ? tx.getMutationType().name() : "N/A")
            .jumlah(tx.getAmount())
            .isExcluded(tx.getIsExcluded())
            .category(tx.getCategory() != null ? tx.getCategory().name() : "TRANSFER")
            .anomalyReason(tx.getAnomalyReason())
            .build()).collect(Collectors.toList());
   }

   public List<DetailTransaksiResponse> getTop10CreditAmountCleaned(Long documentId) {
      List<BankTransaction> transactions = bankTransactionRepository.findTop10CreditAmountCleaned(documentId);
      return transactions.stream().map(tx -> DetailTransaksiResponse.builder()
            .id(tx.getId())
            .tanggal(tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null)
            .keterangan(tx.getNormalizedDescription() != null ? tx.getNormalizedDescription() : tx.getRawDescription())
            .flag(tx.getMutationType() != null ? tx.getMutationType().name() : "N/A")
            .jumlah(tx.getAmount())
            .isExcluded(tx.getIsExcluded())
            .category(tx.getCategory() != null ? tx.getCategory().name() : "TRANSFER")
            .anomalyReason(tx.getAnomalyReason())
            .build()).collect(Collectors.toList());
   }

   public List<DetailTransaksiResponse> getTop10DebitAmountCleaned(Long documentId) {
      List<BankTransaction> transactions = bankTransactionRepository.findTop10DebitAmountCleaned(documentId);
      return transactions.stream().map(tx -> DetailTransaksiResponse.builder()
            .id(tx.getId())
            .tanggal(tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null)
            .keterangan(tx.getNormalizedDescription() != null ? tx.getNormalizedDescription() : tx.getRawDescription())
            .flag(tx.getMutationType() != null ? tx.getMutationType().name() : "N/A")
            .jumlah(tx.getAmount())
            .isExcluded(tx.getIsExcluded())
            .category(tx.getCategory() != null ? tx.getCategory().name() : "TRANSFER")
            .build()).collect(Collectors.toList());
   }

   // Fungsi untuk mengambil List hasil konversi Object[] menjadi DTO
   // TopFreqResponse (Top 10 Credit Frequency)
   public List<TopFreqResponse> getTop10CreditFreq(Long documentId) {
      List<Object[]> rawFreqData = bankTransactionRepository.findTop10CreditFreqByDocumentId(documentId);

      // Mengubah setiap baris data mentah ke wujud Response Objek yang gampang dibaca
      // Angular
      return rawFreqData.stream().map(row -> {
         String keterangan = row[0] != null ? row[0].toString() : "TANPA KETERANGAN"; // Kolom pertama: keterangan
         Long frekuensi = row[1] != null ? ((Number) row[1]).longValue() : 0L; // Kolom kedua: frekuensi COUNT()
         return TopFreqResponse.builder()
               .keterangan(keterangan)
               .frekuensi(frekuensi)
               .build();
      }).collect(Collectors.toList());
   }

   // Fungsi untuk mengambil data Top 10 Debit Frequency
   public List<TopFreqResponse> getTop10DebitFreq(Long documentId) {
      List<Object[]> rawFreqData = bankTransactionRepository.findTop10DebitFreqByDocumentId(documentId);

      return rawFreqData.stream().map(row -> {
         String keterangan = row[0] != null ? row[0].toString() : "TANPA KETERANGAN";
         Long frekuensi = row[1] != null ? ((Number) row[1]).longValue() : 0L;
         return TopFreqResponse.builder()
               .keterangan(keterangan)
               .frekuensi(frekuensi)
               .build();
      }).collect(Collectors.toList());
   }

   public List<TopFreqResponse> getTop10CreditFreqCleaned(Long documentId) {
      List<Object[]> rawFreqData = bankTransactionRepository.findTop10CreditFreqCleaned(documentId);
      return rawFreqData.stream().map(row -> TopFreqResponse.builder()
            .keterangan(row[0] != null ? row[0].toString() : "TANPA KETERANGAN")
            .frekuensi(row[1] != null ? ((Number) row[1]).longValue() : 0L)
            .build()).collect(Collectors.toList());
   }

   public List<TopFreqResponse> getTop10DebitFreqCleaned(Long documentId) {
      List<Object[]> rawFreqData = bankTransactionRepository.findTop10DebitFreqCleaned(documentId);
      return rawFreqData.stream().map(row -> TopFreqResponse.builder()
            .keterangan(row[0] != null ? row[0].toString() : "TANPA KETERANGAN")
            .frekuensi(row[1] != null ? ((Number) row[1]).longValue() : 0L)
            .build()).collect(Collectors.toList());
   }

   public void toggleExclude(Long transactionId) {
      BankTransaction tx = bankTransactionRepository.findById(transactionId)
            .orElseThrow(() -> new IllegalArgumentException("Transaksi tidak ditemukan"));
      tx.setIsExcluded(tx.getIsExcluded() == null ? true : !tx.getIsExcluded());
      bankTransactionRepository.save(tx);
   }

   /**
    * Mengambil transaksi anomali berdasarkan tipe mutasi (CR atau DB).
    * Menyertakan anomalyReason agar frontend bisa menampilkan alasan deteksi.
    */
   public List<DetailTransaksiResponse> getAnomalyTransactions(Long documentId,
         com.example.mutexa_be.entity.enums.MutationType mutationType) {
      List<BankTransaction> transactions = bankTransactionRepository
            .findAllByMutationDocumentIdAndIsAnomalyTrueAndMutationTypeOrderByTransactionDateAscIdAsc(documentId,
                  mutationType);
      return transactions.stream().map(tx -> DetailTransaksiResponse.builder()
            .id(tx.getId())
            .tanggal(tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null)
            .keterangan(tx.getNormalizedDescription() != null ? tx.getNormalizedDescription() : tx.getRawDescription())
            .flag(tx.getMutationType() != null ? tx.getMutationType().name() : "N/A")
            .jumlah(tx.getAmount())
            .saldo(tx.getBalance())
            .isExcluded(tx.getIsExcluded())
            .category(tx.getCategory() != null ? tx.getCategory().name() : "TRANSFER")
            .anomalyReason(tx.getAnomalyReason())
            .build()).collect(Collectors.toList());
   }

   @Transactional
   public void massToggleExclude(Long documentId, String category, Boolean isExcluded) {
      if (category == null)
         return;
      switch (category.toUpperCase()) {
         case "ADMIN":
            bankTransactionRepository.updateIsExcludedByCategory(documentId,
                  com.example.mutexa_be.entity.enums.TransactionCategory.ADMIN, isExcluded);
            break;
         case "TAX":
            bankTransactionRepository.updateIsExcludedByCategory(documentId,
                  com.example.mutexa_be.entity.enums.TransactionCategory.TAX, isExcluded);
            break;
         case "INTEREST":
            bankTransactionRepository.updateIsExcludedByCategory(documentId,
                  com.example.mutexa_be.entity.enums.TransactionCategory.INTEREST, isExcluded);
            break;
         case "ANOMALY_CR":
            bankTransactionRepository.updateIsExcludedByAnomalyAndMutationType(documentId,
                  com.example.mutexa_be.entity.enums.MutationType.CR, isExcluded);
            break;
         case "ANOMALY_DB":
            bankTransactionRepository.updateIsExcludedByAnomalyAndMutationType(documentId,
                  com.example.mutexa_be.entity.enums.MutationType.DB, isExcluded);
            break;
         default:
            throw new IllegalArgumentException("Unknown category for mass exclude: " + category);
      }
   }

   /**
    * Pencarian transaksi berdasarkan keyword (nama afiliasi, no rekening, dsb).
    * Digunakan untuk fitur Pendeteksi Afiliasi / Window Dressing.
    */
   public List<DetailTransaksiResponse> searchTransactionsByKeyword(Long documentId, String keyword) {
      if (keyword == null || keyword.trim().isEmpty())
         return List.of();
      List<BankTransaction> list = bankTransactionRepository.searchByKeyword(documentId, keyword.trim());
      return list.stream().map(tx -> DetailTransaksiResponse.builder()
            .id(tx.getId())
            .tanggal(tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null)
            .keterangan(tx.getNormalizedDescription() != null ? tx.getNormalizedDescription() : tx.getRawDescription())
            .flag(tx.getMutationType() != null ? tx.getMutationType().name() : null)
            .jumlah(tx.getAmount())
            .saldo(tx.getBalance())
            .isExcluded(tx.getIsExcluded() != null ? tx.getIsExcluded() : false)
            .category(tx.getCategory() != null ? tx.getCategory().name() : "TRANSFER")
            .anomalyReason(tx.getAnomalyReason())
            .build()).collect(Collectors.toList());
   }

   /**
    * Mass toggle exclude/include berdasarkan keyword.
    * Semua transaksi yang cocok dengan keyword akan diubah statusnya sekaligus.
    */
   @Transactional
   public void massToggleKeywordExclude(Long documentId, String keyword, Boolean isExcluded) {
      if (keyword == null || keyword.trim().isEmpty())
         return;
      if (Boolean.TRUE.equals(isExcluded)) {
         // Jika Exclude (Sembunyikan): Hajar semua yang cocok
         bankTransactionRepository.updateIsExcludedByKeyword(documentId, keyword.trim(), true);
      } else {
         // Jika Include (Tampilkan Kembali): Pakai mode aman agar Admin/Tax/Anomali
         // tidak bocor
         bankTransactionRepository.updateIsExcludedByKeywordSafeInclude(documentId, keyword.trim());
      }
   }

   /**
    * Menghitung data Pengendapan (settlement) per bulan.
    *
    * Aturan:
    * 1. Saldo baris pertama bulan pertama = Opening Balance (reverse dari tx pertama)
    * 2. Saldo baris pertama bulan ke-2+ = Closing Balance hari terakhir bulan sebelumnya
    * 3. Saldo baris lainnya = Closing Balance tanggal transaksi sebelumnya
    * 4. Hari baris pertama = tanggal transaksi itu sendiri
    * 5. Hari baris lainnya = tanggal sekarang - tanggal sebelumnya
    * 6. Pengendapan = Saldo x Hari
    */
   public PengendapanResponse getPengendapan(Long documentId) {
      List<BankTransaction> allTransactions = bankTransactionRepository
            .findAllByMutationDocumentIdOrderByTransactionDateAscIdAsc(documentId);

      if (allTransactions.isEmpty()) {
         return PengendapanResponse.builder()
               .bulanList(Collections.emptyList())
               .rataRataPengendapan(BigDecimal.ZERO)
               .build();
      }

      // Group transaksi berdasarkan YearMonth, LinkedHashMap agar urut
      Map<YearMonth, List<BankTransaction>> groupedByMonth = allTransactions.stream()
            .collect(Collectors.groupingBy(
                  tx -> YearMonth.from(tx.getTransactionDate()),
                  LinkedHashMap::new,
                  Collectors.toList()));

      List<PengendapanBulanResponse> bulanList = new ArrayList<>();
      BigDecimal prevMonthClosingBalance = null;

      for (Map.Entry<YearMonth, List<BankTransaction>> entry : groupedByMonth.entrySet()) {
         YearMonth ym = entry.getKey();
         List<BankTransaction> monthTransactions = entry.getValue();

         String monthName = ym.getMonth().getDisplayName(TextStyle.FULL, new Locale("id", "ID"));
         String periode = monthName + " " + ym.getYear();

         // Group transaksi per tanggal unik
         Map<LocalDate, List<BankTransaction>> groupedByDate = monthTransactions.stream()
               .collect(Collectors.groupingBy(
                     BankTransaction::getTransactionDate,
                     LinkedHashMap::new,
                     Collectors.toList()));

         List<LocalDate> uniqueDates = new ArrayList<>(groupedByDate.keySet());
         List<PengendapanRowResponse> rows = new ArrayList<>();

         for (int i = 0; i < uniqueDates.size(); i++) {
            LocalDate currentDate = uniqueDates.get(i);
            int dayOfMonth = currentDate.getDayOfMonth();

            BigDecimal saldo;
            int hari;

            if (i == 0) {
               // Baris pertama bulan ini
               hari = dayOfMonth;

               if (prevMonthClosingBalance != null) {
                  saldo = prevMonthClosingBalance;
               } else {
                  // Bulan pertama: reverse dari tx pertama
                  BankTransaction firstTx = groupedByDate.get(currentDate).get(0);
                  saldo = calculateOpeningBalance(firstTx);
               }
            } else {
               LocalDate prevDate = uniqueDates.get(i - 1);
               hari = dayOfMonth - prevDate.getDayOfMonth();

               // Saldo = closing balance tanggal sebelumnya
               List<BankTransaction> prevDayTxs = groupedByDate.get(prevDate);
               BankTransaction lastTxPrevDay = prevDayTxs.get(prevDayTxs.size() - 1);
               saldo = lastTxPrevDay.getBalance() != null ? lastTxPrevDay.getBalance() : BigDecimal.ZERO;
            }

            BigDecimal pengendapan = saldo.multiply(BigDecimal.valueOf(hari));

            rows.add(PengendapanRowResponse.builder()
                  .tanggal(dayOfMonth)
                  .saldo(saldo)
                  .hari(hari)
                  .pengendapan(pengendapan)
                  .build());
         }

         int totalHari = rows.stream().mapToInt(PengendapanRowResponse::getHari).sum();
         BigDecimal totalPengendapan = rows.stream()
               .map(PengendapanRowResponse::getPengendapan)
               .reduce(BigDecimal.ZERO, BigDecimal::add);

         BigDecimal pengendapanPerBulan = totalHari > 0
               ? totalPengendapan.divide(BigDecimal.valueOf(totalHari), 4, RoundingMode.HALF_UP)
               : BigDecimal.ZERO;

         bulanList.add(PengendapanBulanResponse.builder()
               .periode(periode)
               .rows(rows)
               .totalHari(totalHari)
               .totalPengendapan(totalPengendapan)
               .pengendapanPerBulan(pengendapanPerBulan)
               .build());

         // Simpan closing balance hari terakhir bulan ini untuk bulan berikutnya
         LocalDate lastDate = uniqueDates.get(uniqueDates.size() - 1);
         List<BankTransaction> lastDayTxs = groupedByDate.get(lastDate);
         prevMonthClosingBalance = lastDayTxs.get(lastDayTxs.size() - 1).getBalance();
      }

      // Rata-rata Pengendapan dari semua bulan
      BigDecimal rataRata = BigDecimal.ZERO;
      if (!bulanList.isEmpty()) {
         BigDecimal sumPengendapanPerBulan = bulanList.stream()
               .map(PengendapanBulanResponse::getPengendapanPerBulan)
               .reduce(BigDecimal.ZERO, BigDecimal::add);
         rataRata = sumPengendapanPerBulan.divide(
               BigDecimal.valueOf(bulanList.size()), 4, RoundingMode.HALF_UP);
      }

      return PengendapanResponse.builder()
            .bulanList(bulanList)
            .rataRataPengendapan(rataRata)
            .build();
   }

   /**
    * Menghitung Opening Balance dari transaksi pertama.
    * Jika Debit: OB = balance + amount (debit mengurangi saldo)
    * Jika Credit: OB = balance - amount (credit menambah saldo)
    */
   private BigDecimal calculateOpeningBalance(BankTransaction firstTx) {
      BigDecimal balance = firstTx.getBalance() != null ? firstTx.getBalance() : BigDecimal.ZERO;
      BigDecimal amount = firstTx.getAmount() != null ? firstTx.getAmount() : BigDecimal.ZERO;

      if (firstTx.getMutationType() == MutationType.DB) {
         return balance.add(amount);
      } else {
         return balance.subtract(amount);
      }
   }
}
