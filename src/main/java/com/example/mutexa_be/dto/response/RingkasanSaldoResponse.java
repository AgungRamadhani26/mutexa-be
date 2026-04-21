package com.example.mutexa_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RingkasanSaldoResponse {
   private BigDecimal totalCredit;
   private BigDecimal totalDebit;
   private BigDecimal avgCredit;
   private BigDecimal avgDebit;
   private Integer jumlahBulan; // berapa bulan data yang dihitung
   private BigDecimal avgDailyBalance;

   // Fields for Window Dressing (After exclusion)
   private BigDecimal cleanedTotalCredit;
   private BigDecimal cleanedTotalDebit;
   private BigDecimal cleanedAvgCredit;
   private BigDecimal cleanedAvgDebit;
}
