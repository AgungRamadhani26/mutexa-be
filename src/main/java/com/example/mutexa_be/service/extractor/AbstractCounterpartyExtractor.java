package com.example.mutexa_be.service.extractor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract class penyedia utility umum (regex fallback, stopword cleaning,
 * normalizer)
 * agar tidak terjadi duplikasi kode pada setiap extractor bank spesifik.
 * 
 * Prinsip SOLID: Mengurangi duplikasi, memusatkan helper (SRP).
 */
public abstract class AbstractCounterpartyExtractor implements CounterpartyExtractor {

   protected String normalizeText(String raw) {
      return raw.toUpperCase()
            .replaceAll("[\\r\\n]+", " ")
            .replaceAll("\\s{2,}", " ")
            .trim();
   }

   protected String truncate(String s) {
      if (s == null)
         return "UNKNOWN";

      s = s.replaceAll("^(?:PT|CV|TBK)\\.?\\s+", "");
      s = s.replaceAll("\\s+(?:PT|CV|TBK)\\.?$", "");
      s = s.trim();

      if (s.isEmpty())
         return "UNKNOWN";
      if (s.length() > 255)
         return s.substring(0, 252) + "...";
      return s;
   }

   protected String tryGenericPatterns(String text) {
      Matcher mCena = Pattern.compile("CENAIDJA/([A-Z][A-Z0-9 ]+?)(?:\\s+PT)?\\s+\\d").matcher(text);
      if (mCena.find())
         return mCena.group(1).trim();

      Matcher mDari = Pattern.compile("(?:DARI|KEPADA|KE)\\s+([A-Z][A-Z0-9 ]{2,}?)(?:\\s+TRANSFER|\\s+\\d{5,}|$)")
            .matcher(text);
      if (mDari.find() && mDari.group(1).trim().length() > 3)
         return mDari.group(1).trim();

      Matcher mCorp = Pattern.compile("((?:PT|CV)\\.?)\\s+([A-Z][A-Z0-9 ]{2,}?)(?:\\s+\\d|$)").matcher(text);
      if (mCorp.find())
         return mCorp.group(0).replaceAll("\\s+\\d.*", "").trim();

      return null;
   }

   protected String cleanWithStopwords(String text) {
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
            "VIA", "BRIMO", "IBBIZ", "IBIZ", "NBMB", "BRIVA", "PEMBAYARAN", "PAYMENT",
            "PEMBELIAN", "TOKEN", "PLN", "PULSA", "FAST", "ON"
      };

      for (String sw : stopwords) {
         cleaned = cleaned.replaceAll("\\b" + Pattern.quote(sw) + "\\b", " ");
      }
      cleaned = cleaned.replaceAll("\\b[A-Z]\\b", " ");
      cleaned = cleaned.replaceAll("\\s{2,}", " ").trim();

      if (cleaned.length() < 3) {
         String fallback = text.replaceAll("[^A-Z ]", " ").replaceAll("\\s{2,}", " ").trim();
         return (fallback.isEmpty() || fallback.length() < 2) ? "UNKNOWN" : fallback;
      }
      return cleaned;
   }

   protected String fallback(String text) {
      String result = tryGenericPatterns(text);
      if (result != null)
         return truncate(result);
      return truncate(cleanWithStopwords(text));
   }
}
