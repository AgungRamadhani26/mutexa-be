package com.example.mutexa_be.service;

import com.example.mutexa_be.entity.enums.TransactionCategory;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TransactionRefinementService {

   /**
    * Membersihkan description asli dari spasi ganda, angka-angka referensi
    * yang tidak relevan, karakter khusus, dll sehingga lebih clean.
    */
   public String normalizeDescription(String rawDescription) {
      if (rawDescription == null || rawDescription.isEmpty()) {
         return "-";
      }

      // 1. Ubah newline dan carriage return menjadi spasi
      String normalized = rawDescription.replaceAll("[\\r\\n]+", " ");

      // 2. Ubah multi spasi menjadi 1 spasi
      normalized = normalized.replaceAll("\\s{2,}", " ");

      // 3. (Opsional) Hapus string-string panjang berupa angka 10 digit ke atas yang
      // biasanya cuma referensi transaksi
      normalized = normalized.replaceAll("\\b\\d{10,}\\b", " ");

      // Trim spasi awal / akhir
      return normalized.trim();
   }

   /**
    * Menganalisis description dan jumlah masuk/keluar untuk memandu ke kategori.
    * Menggunakan rule-based matching sesuai dengan plan expected development.
    */
   public TransactionCategory categorizeTransaction(String normalizedDescription, boolean isCredit) {
      // PERUBAHAN ARSITEKTUR:
      // Modul ini tidak lagi melakukan hardcode kategori. Semua pencarian keyword
      // (Bunga, Gaji, dll)
      // telah dipusatkan dan dibuat jauh lebih cerdas di CategorizationService.java.
      // Dengan mereturn UNCLASSIFIED, kita memastikan transaksi akan masuk ke
      // pipeline CategorizationService.
      return TransactionCategory.UNCLASSIFIED;
   }
}
