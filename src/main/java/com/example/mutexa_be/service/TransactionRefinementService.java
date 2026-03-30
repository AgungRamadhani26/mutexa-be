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
      if (normalizedDescription == null) {
         return TransactionCategory.UNCLASSIFIED;
      }

      String lowerDesc = normalizedDescription.toLowerCase();

      // 1. Kategori Admin Fee (BIAYA ADMIN) -> biasanya debit
      if (!isCredit
            && (lowerDesc.contains("biaya admin") || lowerDesc.contains("adm") || lowerDesc.contains("administrasi"))) {
         return TransactionCategory.ADMIN;
      }

      // 2. Kategori Pajak (TAX) -> biasanya debit
      if (!isCredit && (lowerDesc.contains("pajak") || lowerDesc.contains("tax") || lowerDesc.contains("pjk"))) {
         return TransactionCategory.TAX;
      }

      // 3. Kategori Bunga Pinjaman (LOAN_INTEREST) -> biasanya debit (atau pendapatan
      // bunga kalau credit)
      if (lowerDesc.contains("bunga") || lowerDesc.contains("interest")) {
         return isCredit ? TransactionCategory.INCOME : TransactionCategory.INTEREST;
      }

      // 4. Kategori Income (INCOME) -> biasanya kredit (gaji, honor, dll)
      if (isCredit && (lowerDesc.contains("gaji") || lowerDesc.contains("penggajian") || lowerDesc.contains("payroll")
            || lowerDesc.contains("fee") || lowerDesc.contains("honor") || lowerDesc.contains("salary"))) {
         return TransactionCategory.INCOME;
      }

      // Bisa ditambahkan rule-rule lain (Anomali akan dideteksi oleh engine dashboard
      // bukan base category parser)

      return TransactionCategory.UNCLASSIFIED;
   }
}
