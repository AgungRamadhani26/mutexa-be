package com.example.mutexa_be.service;

import com.example.mutexa_be.entity.enums.TransactionCategory;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
      String normalized = rawDescription.replaceAll("[\\r\\n]+", " ");
      normalized = normalized.replaceAll("\\s{2,}", " ");
      normalized = normalized.replaceAll("\\b\\d{10,}\\b", " ");
      return normalized.trim();
   }

   // ================================================================
   // ROUTER: Pilih extractor berdasarkan nama bank
   // ================================================================

   /**
    * Overload BARU: Menerima bankName untuk routing ke extractor bank-specific.
    * Ini adalah entry point utama yang dipanggil dari masing-masing parser.
    */
   public String extractCounterpartyName(String bankName, String rawDescription) {
      if (rawDescription == null || rawDescription.trim().isEmpty()) return null;
      
      if (bankName == null) return extractCounterpartyName(rawDescription);

      switch (bankName.toUpperCase()) {
         case "BRI":     return extractBri(rawDescription);
         case "MANDIRI": return extractMandiri(rawDescription);
         case "UOB":     return extractUob(rawDescription);
         case "BCA":     return extractBca(rawDescription);
         default:        return extractCounterpartyName(rawDescription); // generic fallback
      }
   }

   /**
    * Method lama (backward compatible) — dipakai sebagai generic fallback
    * untuk bank yang belum punya extractor khusus (BNI, CIMB, OCBC dll).
    */
   public String extractCounterpartyName(String rawDescription) {
      if (rawDescription == null || rawDescription.trim().isEmpty()) return null;
      String text = normalizeText(rawDescription);

      // Generic patterns (PT, CV, CENAIDJA, DARI/KE)
      String result = tryGenericPatterns(text);
      if (result != null) return truncate(result);

      // Stopword cleaning fallback
      return truncate(cleanWithStopwords(text));
   }

   // ================================================================
   // EXTRACTOR PER BANK
   // ================================================================

   /**
    * BRI — Format utama:
    * 1. "Transfer BI-Fast ke BANK ... - NOREK - NAMA"  → nama setelah dash terakhir
    * 2. "Transfer BI-Fast dari BANK ... - NAMA"        → nama setelah dash terakhir
    * 3. "IBIZ PENGIRIM TO PENERIMA"                    → nama setelah "TO"
    * 4. "NBMB PENGIRIM TO PENERIMA"                    → pengirim (sebelum TO)
    * 5. "Pembayaran BRIVA ke MERCHANT - NOREF"         → merchant name
    * 6. "Pembelian Token PLN ..."                      → PLN
    * 7. "Transfer Ke NAMA via BRImo"                   → nama setelah "Ke"
    * 8. QRIS/QRISRNS → system
    * 9. Biaya Administrasi / Biaya Bulanan ATM → system
    */
   private String extractBri(String rawDescription) {
      String text = normalizeText(rawDescription);

      // 1. Transfer BI-Fast ke/dari — nama ada setelah dash terakhir
      //    "Transfer BI-Fast ke BANK CENTRAL ASIA - 8465752994 - Khintesa Nur Wibowo Mh"
      Matcher mBiFast = Pattern.compile("TRANSFER BI-FAST (?:KE|DARI) .+ - ([A-Z][A-Z ]+)$", Pattern.CASE_INSENSITIVE)
            .matcher(text);
      if (mBiFast.find()) {
         return truncate(mBiFast.group(1).trim());
      }

      // 2. IBIZ ... TO NAMA — nama ada setelah "TO"
      //    "IBIZ AMBE MAJU BERS TO NIA YUSNIA"
      Matcher mIbiz = Pattern.compile("(?:IBIZ|IBBIZ) .+? TO ([A-Z][A-Z ]+?)(?:\\s+\\d|$)")
            .matcher(text);
      if (mIbiz.find()) {
         return truncate(mIbiz.group(1).trim());
      }

      // 3. NBMB PENGIRIM TO PENERIMA — pengirim ada SEBELUM "TO"
      //    "NBMB RURIN PUTRI RI TO AMBE MAJU BERSAMA"
      Matcher mNbmb = Pattern.compile("NBMB ([A-Z][A-Z ]+?) TO ")
            .matcher(text);
      if (mNbmb.find()) {
         return truncate(mNbmb.group(1).trim());
      }

      // 4. Transfer Ke NAMA via BRImo
      //    "Transfer Ke Anita Trisna Lati via BRImo"
      Matcher mTransferKe = Pattern.compile("TRANSFER KE ([A-Z][A-Z ]+?)(?:\\s+VIA|\\s+\\d|$)")
            .matcher(text);
      if (mTransferKe.find()) {
         return truncate(mTransferKe.group(1).trim());
      }

      // 5. Pembayaran BRIVA ke MERCHANT
      //    "Pembayaran BRIVA ke SHOPEE - 112010102128384327"
      Matcher mBriva = Pattern.compile("PEMBAYARAN BRIVA KE ([A-Z][A-Z ]+?)(?:\\s+-|\\s+\\d|$)")
            .matcher(text);
      if (mBriva.find()) {
         return truncate(mBriva.group(1).trim());
      }

      // 6. Pembelian Token PLN / Pulsa
      if (text.contains("PEMBELIAN TOKEN PLN")) return "PLN";
      if (text.contains("PEMBELIAN PULSA")) return "PEMBELIAN PULSA";

      // 7. QRIS / QRISRNS → system transaction
      if (text.startsWith("QRIS") || text.startsWith("QRISRNS")) return "QRIS";
      // Kode QRIS di tengah: "456042#789530788469#936000..."
      if (Pattern.matches("^\\d+#\\d+#\\d+.*", text)) return "QRIS";

      // 8. Biaya system
      if (text.startsWith("BIAYA ADMINISTRASI") || text.startsWith("BIAYA ADM")) return "BIAYA ADMINISTRASI";
      if (text.startsWith("BIAYA BULANAN ATM")) return "BIAYA ATM";

      // 9. OB (overbooking/internal)
      //    "200010105_OB_2107794108862358544"
      if (text.contains("_OB_")) return "OVERBOOKING";

      // Fallback: gunakan generic
      return truncate(cleanWithStopwords(text));
   }

   /**
    * MANDIRI — Format utama:
    * 1. "MCM InhouseTrf KE NAMA Transfer Fee"    → nama setelah KE, sebelum "Transfer Fee"
    * 2. "MCM InhouseTrf DARI NAMA Transfer Fee"   → nama setelah DARI
    * 3. "CENAIDJA/NAMA ..."                       → nama setelah /
    * 4. "BNINIDJA/NAMA" atau "BRINIDJA/NAMA"      → nama setelah /
    * 5. "Setor tunai NAMA 01-NNNNN"               → nama setelah "setor tunai"
    * 6. "PINBUK ... MCM InhouseTrf DARI NAMA"     → nama setelah DARI
    */
   private String extractMandiri(String rawDescription) {
      String text = normalizeText(rawDescription);

      // 1 & 2. MCM InhouseTrf KE/DARI NAMA ... Transfer Fee
      //    "MCM InhouseTrf KE JUI SHIN INDONESIA Transfer Fee"
      Matcher mMcm = Pattern.compile("MCM\\s+INHOUSETRF\\s+(?:KE|DARI)\\s+([A-Z][A-Z ]+?)\\s+TRANSFER\\s+FEE", Pattern.CASE_INSENSITIVE)
            .matcher(text);
      if (mMcm.find()) {
         return truncate(mMcm.group(1).trim());
      }

      // 3. CENAIDJA/NAMA — nama setelah /
      //    "CENAIDJA/SUN POWER CERAMICS PT AHSA-SUN POWER CERAM99102"
      Matcher mCena = Pattern.compile("CENAIDJA/([A-Z][A-Z ]+?)(?:\\s+PT\\s|\\s+\\d|\\s+AHSA|$)")
            .matcher(text);
      if (mCena.find()) {
         return truncate(mCena.group(1).trim());
      }

      // 4. BNINIDJA/NAMA or BRINIDJA/NAMA — nama setelah /
      //    "BNINIDJA/EUIS MARIAM" or "BRINIDJA/ANUGRAH BANGUN CAHAY"
      Matcher mBxn = Pattern.compile("(?:BNINIDJA|BRINIDJA|BMRIIDJA)/([A-Z][A-Z ]+?)(?:\\s+AHSA|\\s+\\d|$)")
            .matcher(text);
      if (mBxn.find()) {
         return truncate(mBxn.group(1).trim());
      }

      // 5. Setor tunai NAMA 01-NNNNN
      //    "Setor tunai AHSA JAYA METALINDO 01-0718203"
      Matcher mSetor = Pattern.compile("SETOR(?:AN)?\\s+(?:TUNAI\\s+)?([A-Z][A-Z ]{2,}?)\\s+\\d{2}-\\d")
            .matcher(text);
      if (mSetor.find()) {
         return truncate(mSetor.group(1).trim());
      }

      // 6. PINBUK ... DARI — sama seperti MCM DARI
      Matcher mPinbuk = Pattern.compile("MCM\\s+INHOUSETRF\\s+DARI\\s+([A-Z][A-Z ]+?)(?:\\s+TRANSFER|$)", Pattern.CASE_INSENSITIVE)
            .matcher(text);
      if (mPinbuk.find()) {
         return truncate(mPinbuk.group(1).trim());
      }

      // 7. Setor Kliring — system
      if (text.toUpperCase().contains("SETOR") && text.toUpperCase().contains("KLIRING")) {
         return "SETOR KLIRING";
      }

      // Fallback
      return truncate(cleanWithStopwords(text));
   }

   /**
    * UOB — Format utama:
    * 1. "Misc Credit TRANSFER DANA ... PT. NAMA"  → nama PT di akhir
    * 2. "Misc Debit PENEMPATAN DEPOSITO"           → system label
    * 3. "Interest Credit"                           → system
    * 4. "OD Int Charge"                             → system
    * 5. "Withholding Tax Dr"                        → system
    * 6. "Cash BN" / "Cash 000000NNNN"               → system
    */
   private String extractUob(String rawDescription) {
      String text = normalizeText(rawDescription);

      // 1. "PT. NAMA PERUSAHAAN" di mana saja dalam teks
      Matcher mPt = Pattern.compile("(?:PT\\.?|CV\\.?)\\s+([A-Z][A-Z ]{2,})")
            .matcher(text);
      if (mPt.find()) {
         String ptName = mPt.group(0).replaceAll("\\s+\\d.*", "").trim();
         return truncate(ptName);
      }

      // 2. Misc Credit TRANSFER DANA — biasanya ada nama penerima/pengirim
      //    Cek apakah ada nama setelah kode referensi "RIC..."
      Matcher mMisc = Pattern.compile("(?:MISC CREDIT|MISC DEBIT)\\s+TRANSFER DANA.+?([A-Z]{2,}(?:\\s+[A-Z]{2,})+)\\s*$")
            .matcher(text);
      if (mMisc.find()) {
         return truncate(mMisc.group(1).trim());
      }

      // 3. System labels — kembalikan sebagai counterparty
      if (text.equals("INTEREST CREDIT")) return "INTEREST CREDIT";
      if (text.equals("OD INT CHARGE") || text.startsWith("OD INT CHARGE")) return "OD INT CHARGE";
      if (text.startsWith("WITHHOLDING TAX")) return "WITHHOLDING TAX";
      if (text.equals("MISC DEBIT PENEMPATAN DEPOSITO") || text.contains("PENEMPATAN DEPOSITO")) return "PENEMPATAN DEPOSITO";
      if (text.startsWith("CASH")) return "CASH";
      if (text.startsWith("MISC CREDIT")) return "MISC CREDIT";
      if (text.startsWith("MISC DEBIT")) return "MISC DEBIT";

      // Fallback
      return truncate(cleanWithStopwords(text));
   }

   /**
    * BCA — Format utama:
    * 1. "TRSF E-BANKING DB 0101/FTSCY/WS95271 55,000,000.00 NAMA"  → nama di akhir
    * 2. "CENAIDJA/NAMA PT 1234567890"                               → nama setelah /
    * 3. "SETOR TUNAI NAMA NN-NNNNN"                                  → nama setelah setor tunai
    * 4. "DARI/KEPADA NAMA"                                           → nama setelah keyword
    * 5. System: INTEREST CREDIT, OD INT CHARGE, BIAYA ADM, dsb.
    */
   private String extractBca(String rawDescription) {
      String text = normalizeText(rawDescription);

      // 1. CENAIDJA/NAMA PT... 
      Matcher mCena = Pattern.compile("CENAIDJA/([A-Z][A-Z0-9 ]+?)(?:\\s+PT)?\\s+\\d")
            .matcher(text);
      if (mCena.find()) {
         return truncate(mCena.group(1).trim());
      }

      // 2. TRSF E-BANKING — nama ada SETELAH nominal (angka dengan koma)
      //    "TRSF E-BANKING DB 0101/FTSCY/WS95271 55,000,000.00 MARSHA PRISCILLIA"
      Matcher mTrsf = Pattern.compile("TRSF .+?\\d+[,.]\\d{2,}\\s+([A-Z][A-Z ]{2,})$")
            .matcher(text);
      if (mTrsf.find()) {
         return truncate(mTrsf.group(1).trim());
      }

      // 3. Setor tunai NAMA NN-NNNNNNN
      Matcher mSetor = Pattern.compile("SETOR(?:AN)?\\s+(?:TUNAI|SALES)?\\s*([A-Z][A-Z ]{2,}?)\\s+\\d{2}-\\d")
            .matcher(text);
      if (mSetor.find()) {
         return truncate(mSetor.group(1).trim());
      }

      // 4. DARI/KEPADA/KE NAMA
      Matcher mDari = Pattern.compile("(?:DARI|KEPADA|KE)\\s+([A-Z][A-Z0-9 ]{2,}?)(?:\\s+TRANSFER|\\s+\\d{10,}|$)")
            .matcher(text);
      if (mDari.find()) {
         String name = mDari.group(1).trim();
         if (name.length() > 3) return truncate(name);
      }

      // 5. PT./CV.
      Matcher mCorp = Pattern.compile("((?:PT|CV)\\.?)\\s+([A-Z][A-Z0-9 ]{2,}?)(?:\\s+\\d|$)")
            .matcher(text);
      if (mCorp.find()) {
         return truncate(mCorp.group(0).replaceAll("\\s+\\d.*", "").trim());
      }

      // 6. System labels
      if (text.equals("INTEREST CREDIT")) return "INTEREST CREDIT";
      if (text.contains("OD INT CHARGE")) return "OD INT CHARGE";
      if (text.startsWith("BIAYA ADM")) return "BIAYA ADMINISTRASI";

      // Fallback: stopword cleaning (original logic)
      return truncate(cleanWithStopwords(text));
   }

   // ================================================================
   // HELPER METHODS
   // ================================================================

   /** Normalisasi teks: uppercase, hapus newline, rapikan spasi */
   private String normalizeText(String raw) {
      return raw.toUpperCase()
            .replaceAll("[\\r\\n]+", " ")
            .replaceAll("\\s{2,}", " ")
            .trim();
   }

   /** Cek pola generic yang umum di semua bank (PT, CV, CENAIDJA, DARI/KE) */
   private String tryGenericPatterns(String text) {
      // CENAIDJA
      Matcher mCena = Pattern.compile("CENAIDJA/([A-Z][A-Z0-9 ]+?)(?:\\s+PT)?\\s+\\d")
            .matcher(text);
      if (mCena.find()) return mCena.group(1).trim();

      // DARI/KEPADA/KE
      Matcher mDari = Pattern.compile("(?:DARI|KEPADA|KE)\\s+([A-Z][A-Z0-9 ]{2,}?)(?:\\s+TRANSFER|\\s+\\d{10,}|$)")
            .matcher(text);
      if (mDari.find() && mDari.group(1).trim().length() > 3) return mDari.group(1).trim();

      // PT/CV
      Matcher mCorp = Pattern.compile("((?:PT|CV)\\.?)\\s+([A-Z][A-Z0-9 ]{2,}?)(?:\\s+\\d|$)")
            .matcher(text);
      if (mCorp.find()) return mCorp.group(0).replaceAll("\\s+\\d.*", "").trim();

      return null;
   }

   /** Cleaning dengan stopwords — dipakai sebagai fallback terakhir */
   private String cleanWithStopwords(String text) {
      // Hapus semua non-alpha jadi spasi
      String cleaned = text.replaceAll("[^A-Z ]", " ");

      String[] stopwords = {
         "TRANSFER DANA", "TRANSFER FEE", "MISC CREDIT", "MISC DEBIT",
         "SETOR TUNAI", "SETORAN SALES", "SETORAN", "STORAN",
         "INHOUSETRF", "INHOUSETRFR", "MCM", "PINBUK", "KLIRING", "RTGS",
         "LLG", "SKN", "QRIS", "QRISRNS", "BANKING", "MOBILE", "INTERNET",
         "ATM", "EDC", "OVERBOOKING", "TRSF", "TRFR", "TRF", "DARI", "KEPADA",
         "REMARK", "TANGGAL", "BIAYA ADM", "BIAYA ADMIN", "BIAYA ADMINISTRASI",
         "BUNGA", "PAJAK BUNGA", "CENAIDJA", "WSID", "SALES", "TUNAI",
         "DEBIT", "CREDIT", "DB", "CR", "INTEREST", "FTSCY", "FTS", "WS",
         "CHG", "TRFK", "ADJ", "MUTASI", "REK", "REKENING", "SALDO", "TRANSFER",
         "VIA", "BRIMO", "IBBIZ", "IBIZ", "NBMB", "BRIVA", "PEMBAYARAN",
         "PEMBELIAN", "TOKEN", "PLN", "PULSA", "FAST"
      };

      for (String sw : stopwords) {
         cleaned = cleaned.replaceAll("\\b" + Pattern.quote(sw) + "\\b", " ");
      }

      // Hapus huruf tunggal
      cleaned = cleaned.replaceAll("\\b[A-Z]\\b", " ");
      cleaned = cleaned.replaceAll("\\s{2,}", " ").trim();

      // Fallback jika terlalu pendek
      if (cleaned.length() < 3) {
         String fallback = text.replaceAll("[^A-Z ]", " ").replaceAll("\\s{2,}", " ").trim();
         return (fallback.isEmpty() || fallback.length() < 2) ? "UNKNOWN" : fallback;
      }

      return cleaned;
   }

   /** Potong string ke max 255 chars (batas kolom DB) */
   private String truncate(String s) {
      if (s == null) return "UNKNOWN";
      if (s.length() > 255) return s.substring(0, 252) + "...";
      return s;
   }

   /**
    * Menganalisis description dan jumlah masuk/keluar untuk memandu ke kategori.
    */
   public TransactionCategory categorizeTransaction(String normalizedDescription, boolean isCredit) {
      return TransactionCategory.UNCLASSIFIED;
   }
}
