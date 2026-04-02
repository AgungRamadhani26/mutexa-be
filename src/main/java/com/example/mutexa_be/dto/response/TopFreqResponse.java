package com.example.mutexa_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) untuk menangani respons frekuensi kejadian mutasi.
 * Dirancang secara spesifik untuk memuat nama mutasi beserta jumlah kemunculannya.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopFreqResponse {
    // Menyimpan nama/keterangan dari mutasinya (contoh: "SETORAN TUNAI")
    private String keterangan;
    // Menyimpan berapa kali kemunculan mutasi tersebut (contoh: 45)
    private Long frekuensi;
}
