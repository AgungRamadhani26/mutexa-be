package com.example.mutexa_be.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetailTransaksiResponse {
   private Long id;           // ID transaksi untuk toggle exclude
   private String tanggal;
   private String keterangan;
   private String flag; // CR / DB
   private BigDecimal jumlah;
   private Boolean isExcluded; // Status exclusion
}
