package com.example.mutexa_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Summary pengendapan per bulan: berisi daftar baris dan total.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PengendapanBulanResponse {
   private String periode;                       // "Desember 2025"
   private List<PengendapanRowResponse> rows;    // Baris-baris pengendapan
   private Integer totalHari;                    // SUM(hari)
   private BigDecimal totalPengendapan;          // SUM(pengendapan)
   private BigDecimal pengendapanPerBulan;       // totalPengendapan / totalHari
}
