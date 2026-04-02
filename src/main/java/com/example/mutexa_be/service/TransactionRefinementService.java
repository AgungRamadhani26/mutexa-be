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
    * Mengekstrak secara agresif nama pengirim atau penerima (counterparty) dari teks mutasi.
    * Fungsi ini difokuskan mencari nama subjek agar SQL bisa melakukan GROUP BY dengan sangat presisi.
    */
   public String extractCounterpartyName(String rawDescription) {
      if (rawDescription == null || rawDescription.trim().isEmpty()) return "UNKNOWN";

      String text = rawDescription.toUpperCase().replaceAll("[\\r\\n]+", " ");
      
      // 1. Deteksi nama Perusahaan secara kuat
      java.util.regex.Pattern pCorp = java.util.regex.Pattern.compile("(PT\\.?|CV\\.?)\\s+([A-Z0-9 ]{3,30})");
      java.util.regex.Matcher mCorp = pCorp.matcher(text);
      if (mCorp.find()) {
         return mCorp.group(0).trim();
      }

      // 2. Deteksi teks setelah DARI atau KE yang bersih
      java.util.regex.Pattern pDirection = java.util.regex.Pattern.compile("(?:DARI|KE)\\s+([A-Z0-9\\.\\- ]+?)(?:\\s+[0-9]{10,}|TRANSFER FEE|$)");
      java.util.regex.Matcher mDirection = pDirection.matcher(text);
      if (mDirection.find()) {
         String name = mDirection.group(1).trim();
         if (name.length() > 3) return name;
      }

      // 3. Mode fallback: Pangkas habis-habisan semua kata operasional bank dan nomor resi
      String cleaned = text
            .replaceAll("TRANSFER DANA", "")
            .replaceAll("MISC CREDIT", "")
            .replaceAll("MISC DEBIT", "")
            .replaceAll("INHOUSETRF", "")
            .replaceAll("MCM", "")
            .replaceAll("PINBUK", "")
            .replaceAll("TRANSFER FEE", "")
            .replaceAll("REMARK:", "")
            .replaceAll("TANGGAL :", "")
            .replaceAll("WSID[A-Z0-9]*", "")
            .replaceAll("\\b[A-Z0-9]{12,}\\b", ""); // Hapus kode pelacakan bank 12+ huruf/angka
            
      cleaned = cleaned.replaceAll("[^A-Z0-9\\.\\- ]", " ").replaceAll("\\s{2,}", " ").trim();
      return cleaned.isEmpty() ? "UNKNOWN" : (cleaned.length() > 30 ? cleaned.substring(0, 30) : cleaned);
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
