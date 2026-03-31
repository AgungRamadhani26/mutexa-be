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

@Service
@RequiredArgsConstructor
public class DashboardService {

   private final BankTransactionRepository bankTransactionRepository;

   public List<SummaryPerbulanResponse> getSummaryPerbulan() {
      List<Object[]> rawSummaries = bankTransactionRepository.getMonthlySummary();

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

   public List<DetailTransaksiResponse> getDetailSemuaTransaksi() {
      List<BankTransaction> transactions = bankTransactionRepository.findAllByOrderByTransactionDateAscIdAsc();
      return transactions.stream().map(tx -> DetailTransaksiResponse.builder()
            .tanggal(tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null)
            .keterangan(tx.getNormalizedDescription() != null ? tx.getNormalizedDescription() : tx.getRawDescription())
            .flag(tx.getMutationType() != null ? tx.getMutationType().name() : "N/A")
            .jumlah(tx.getAmount())
            .build()).collect(Collectors.toList());
   }
}
