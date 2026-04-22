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
public class SummaryPerbulanResponse {
   private String periode; // e.g. "Januari 2026"
   private BigDecimal saldoAkhir;
   private BigDecimal totalCredit;
   private BigDecimal totalDebit;
   private BigDecimal cleanedTotalCredit;
   private BigDecimal cleanedTotalDebit;
   private Long freqCredit;
   private Long freqDebit;
   private Long cleanedFreqCredit;
   private Long cleanedFreqDebit;
}
