package com.example.mutexa_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Wrapper response pengendapan: semua bulan + rata-rata keseluruhan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PengendapanResponse {
   private List<PengendapanBulanResponse> bulanList;   // Data per bulan
   private BigDecimal rataRataPengendapan;              // Rata-rata Pengendapan/Bulan dari semua bulan
}
