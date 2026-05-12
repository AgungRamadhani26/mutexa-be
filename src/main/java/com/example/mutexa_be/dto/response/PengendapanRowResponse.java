package com.example.mutexa_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Satu baris data pengendapan: tanggal transaksi, saldo mengendap, jumlah hari, dan total pengendapan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PengendapanRowResponse {
   private Integer tanggal;         // Tanggal dalam bulan (1-31)
   private BigDecimal saldo;        // Saldo yang mengendap
   private Integer hari;            // Jumlah hari mengendap
   private BigDecimal pengendapan;  // saldo × hari
}
