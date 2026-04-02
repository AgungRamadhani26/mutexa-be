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
    * Mengekstrak nama pengirim/penerima (counterparty) dari deskripsi transaksi bank.
    *
    * STRATEGI 3 LAPIS:
    * Lapis 1: Deteksi pola spesifik bank (CENAIDJA/, Setor tunai, DARI/KE, PT./CV.)
    * Lapis 2: Hapus SEMUA kata operasional bank (stopwords) dari kalimat
    * Lapis 3: Bersihkan sisa angka, tanggal, kode referensi → yang tersisa = nama entitas
    */
   public String extractCounterpartyName(String rawDescription) {
      if (rawDescription == null || rawDescription.trim().isEmpty()) return null;

      // Normalisasi dasar: uppercase, hapus newline, rapikan spasi
      String text = rawDescription.toUpperCase()
            .replaceAll("[\\r\\n]+", " ")
            .replaceAll("\\s{2,}", " ")
            .trim();

      // ============================================================
      // LAPIS 1: Deteksi Pola Spesifik Bank (paling akurat, dicek duluan)
      // ============================================================

      // 1a. Pola CENAIDJA/NAMA PERUSAHAAN PT (khas UOB/transfer antar bank)
      java.util.regex.Matcher mCena = java.util.regex.Pattern
            .compile("CENAIDJA/([A-Z][A-Z0-9 ]+?)(?:\\s+PT)?\\s+\\d")
            .matcher(text);
      if (mCena.find()) {
         // Kembalikan hanya nama murni tanpa prefix PT agar konsisten dengan pola lain
         return mCena.group(1).trim();
      }

      // 1b. Pola "Setor tunai NAMA ENTITAS NN-NNNNNNN" (khas setoran bank)
      java.util.regex.Matcher mSetor = java.util.regex.Pattern
            .compile("SETOR(?:AN)?\\s+(?:TUNAI|SALES)?\\s*(?:\\d{2}/\\d{2}/\\d{2,4})?\\s*(?:SETOR TUNAI)?\\s*([A-Z][A-Z ]{2,}?)\\s+\\d{2}-\\d")
            .matcher(text);
      if (mSetor.find()) return mSetor.group(1).trim();

      // 1c. Pola "DARI NAMA ENTITAS" (khas transfer internal/PINBUK)
      java.util.regex.Matcher mDari = java.util.regex.Pattern
            .compile("(?:DARI|KEPADA|KE)\\s+([A-Z][A-Z0-9 ]{2,}?)(?:\\s+TRANSFER|\\s+\\d{10,}|$)")
            .matcher(text);
      if (mDari.find()) {
         String name = mDari.group(1).trim();
         if (name.length() > 3) return name;
      }

      // 1d. Pola PT./CV. diikuti nama (khas perusahaan Indonesia)
      java.util.regex.Matcher mCorp = java.util.regex.Pattern
            .compile("((?:PT|CV)\\.?)\\s+([A-Z][A-Z0-9 ]{2,}?)(?:\\s+\\d|$)")
            .matcher(text);
      if (mCorp.find()) return mCorp.group(0).replaceAll("\\s+\\d.*", "").trim();

      // ============================================================
      // LAPIS 2: Hapus semua STOPWORDS operasional bank Indonesia
      // ============================================================
      String cleaned = text;

      // Daftar kata-kata operasional bank yang BUKAN nama entitas
      // (diurutkan dari frase panjang ke pendek agar tidak terpotong salah)
      String[] stopwords = {
         // Tipe transaksi
         "TRANSFER DANA", "TRANSFER FEE", "MISC CREDIT", "MISC DEBIT",
         "SETOR TUNAI", "SETORAN SALES", "SETORAN", "STORAN",
         "INHOUSETRF", "INHOUSETRFR",
         // Instrumen/channel
         "MCM", "PINBUK", "KLIRING", "RTGS", "LLG", "SKN",
         "QRIS", "QRISRNS", "E-BANKING", "M-BANKING", "MOBILE BANKING",
         "INTERNET BANKING", "ATM", "EDC", "OVERBOOKING",
         "TRSF", "TRFR", "TRF",
         // Kata penghubung/label
         "DARI", "KEPADA", "KE", "REMARK:", "REMARK",
         "TANGGAL :", "TANGGAL:", "TANGGAL",
         "BIAYA ADM", "BIAYA ADMIN", "BIAYA ADMINISTRASI",
         "BUNGA", "PAJAK BUNGA",
         // Kode bank/sistem
         "CENAIDJA", "WSID",
         // Kata umum
         "SALES", "TUNAI", "DB", "CR"
      };

      for (String sw : stopwords) {
         cleaned = cleaned.replace(sw, " ");
      }

      // ============================================================
      // LAPIS 3: Hapus semua angka, tanggal, dan kode referensi
      // ============================================================

      // Hapus tanggal format DD/MM/YYYY atau DD/MM/YY
      cleaned = cleaned.replaceAll("\\b\\d{2}/\\d{2}/\\d{2,4}\\b", " ");

      // Hapus kode alfanumerik panjang (>= 8 karakter campuran huruf+angka, misal: 20251218CENAIDJA010O0)
      cleaned = cleaned.replaceAll("\\b[A-Z0-9]{8,}\\b", " ");

      // Hapus format nomor rekening (NN-NNNNNNN)
      cleaned = cleaned.replaceAll("\\b\\d{2}-\\d{5,}\\b", " ");

      // Hapus angka murni berapapun panjangnya
      cleaned = cleaned.replaceAll("\\b\\d+\\b", " ");

      // Hapus karakter non-huruf (kecuali spasi dan titik untuk PT.)
      cleaned = cleaned.replaceAll("[^A-Z. ]", " ");

      // Hapus titik yang berdiri sendiri
      cleaned = cleaned.replaceAll("(?<![A-Z])\\.", " ");

      // Rapikan spasi berlebih
      cleaned = cleaned.replaceAll("\\s{2,}", " ").trim();

      // Jika hasil akhir terlalu pendek (< 3 huruf), kembalikan null agar COALESCE jatuh ke deskripsi asli
      if (cleaned.length() < 3) return null;

      return cleaned;
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
