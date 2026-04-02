package com.example.mutexa_be.service;

import com.example.mutexa_be.dto.response.DetailTransaksiResponse;
import com.example.mutexa_be.dto.response.SummaryPerbulanResponse;
import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.repository.BankTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import com.example.mutexa_be.dto.response.TopFreqResponse;
import java.util.stream.Collectors;

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

         String monthName = Month.of(month).getDisplayName(TextStyle.FULL, new Locale("id", "ID"));
         String periode = monthName + " " + year;

         return SummaryPerbulanResponse.builder()
               .periode(periode)
               .totalCredit(totalCredit)
               .totalDebit(totalDebit)
               .freqCredit(freqCredit)
               .freqDebit(freqDebit)
               .saldoAkhir(saldoAkhir)
               .build();
      }).collect(Collectors.toList());
   }

   public List<DetailTransaksiResponse> getDetailSemuaTransaksi(Long documentId) {
      List<BankTransaction> transactions = bankTransactionRepository.findAllByMutationDocumentIdOrderByTransactionDateAscIdAsc(documentId);
      return transactions.stream().map(tx -> DetailTransaksiResponse.builder()
            .tanggal(tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null)
            .keterangan(tx.getNormalizedDescription() != null ? tx.getNormalizedDescription() : tx.getRawDescription())
            .flag(tx.getMutationType() != null ? tx.getMutationType().name() : "N/A")
            .jumlah(tx.getAmount())
            .build()).collect(Collectors.toList());
   }

   public List<DetailTransaksiResponse> getTop10CreditAmount(Long documentId) {
      List<BankTransaction> transactions = bankTransactionRepository.findTop10ByMutationDocumentIdAndMutationTypeOrderByAmountDesc(
            documentId, com.example.mutexa_be.entity.enums.MutationType.CR);
      return transactions.stream().map(tx -> DetailTransaksiResponse.builder()
            .tanggal(tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null)
            .keterangan(tx.getNormalizedDescription() != null ? tx.getNormalizedDescription() : tx.getRawDescription())
            .flag(tx.getMutationType() != null ? tx.getMutationType().name() : "N/A")
            .jumlah(tx.getAmount())
            .build()).collect(Collectors.toList());
   }

   public List<DetailTransaksiResponse> getTop10DebitAmount(Long documentId) {
      List<BankTransaction> transactions = bankTransactionRepository.findTop10ByMutationDocumentIdAndMutationTypeOrderByAmountDesc(
            documentId, com.example.mutexa_be.entity.enums.MutationType.DB);
      return transactions.stream().map(tx -> DetailTransaksiResponse.builder()
            .tanggal(tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null)
            .keterangan(tx.getNormalizedDescription() != null ? tx.getNormalizedDescription() : tx.getRawDescription())
            .flag(tx.getMutationType() != null ? tx.getMutationType().name() : "N/A")
            .jumlah(tx.getAmount())
            .build()).collect(Collectors.toList());
   }

   // Fungsi untuk mengambil List hasil konversi Object[] menjadi DTO TopFreqResponse (Top 10 Credit Frequency)
   public List<TopFreqResponse> getTop10CreditFreq(Long documentId) {
      List<Object[]> rawFreqData = bankTransactionRepository.findTop10CreditFreqByDocumentId(documentId);
      
      // Mengubah setiap baris data mentah ke wujud Response Objek yang gampang dibaca Angular
      return rawFreqData.stream().map(row -> {
         String keterangan = row[0] != null ? row[0].toString() : "TANPA KETERANGAN"; // Kolom pertama: keterangan
         Long frekuensi = row[1] != null ? ((Number) row[1]).longValue() : 0L;        // Kolom kedua: frekuensi COUNT()
         return TopFreqResponse.builder()
                 .keterangan(keterangan)
                 .frekuensi(frekuensi)
                 .build();
      }).collect(Collectors.toList());
   }
}
