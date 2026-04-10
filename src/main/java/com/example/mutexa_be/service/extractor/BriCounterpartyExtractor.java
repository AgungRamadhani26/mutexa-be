package com.example.mutexa_be.service.extractor;

import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BriCounterpartyExtractor extends AbstractCounterpartyExtractor {

   @Override
   public String getBankName() {
      return "BRI";
   }

   @Override
   public String extract(String rawDescription, boolean isCredit) {
      if (rawDescription == null || rawDescription.trim().isEmpty()) return null;
      String text = normalizeText(rawDescription);

      Matcher mBiFast = Pattern.compile("TRANSFER BI-FAST (?:KE|DARI) .+ - ([A-Z][A-Z ]+)$", Pattern.CASE_INSENSITIVE).matcher(text);
      if (mBiFast.find()) return truncate(mBiFast.group(1).trim());

      Matcher mIbiz = Pattern.compile("(?:IBIZ|IBBIZ) (.+?) TO ([A-Z][A-Z ]+?)(?:\\s+\\d|$)").matcher(text);
      if (mIbiz.find()) return truncate(isCredit ? mIbiz.group(1).trim() : mIbiz.group(2).trim());

      Matcher mNbmb = Pattern.compile("NBMB ([A-Z][A-Z ]+?) TO ([A-Z][A-Z ]+?)(?:\\s+\\d|$)").matcher(text);
      if (mNbmb.find()) return truncate(isCredit ? mNbmb.group(1).trim() : mNbmb.group(2).trim());

      Matcher mTransferKe = Pattern.compile("TRANSFER KE ([A-Z][A-Z ]+?)(?:\\s+VIA|\\s+\\d|$)").matcher(text);
      if (mTransferKe.find()) return truncate(mTransferKe.group(1).trim());

      Matcher mBriva = Pattern.compile("PEMBAYARAN BRIVA KE ([A-Z][A-Z ]+?)(?:\\s+-|\\s+\\d|$)").matcher(text);
      if (mBriva.find()) return truncate(mBriva.group(1).trim());

      if (text.contains("PEMBELIAN TOKEN PLN")) return "PLN";
      if (text.contains("PEMBELIAN PULSA")) return "PEMBELIAN PULSA";
      if (text.startsWith("QRIS") || text.startsWith("QRISRNS") || Pattern.matches("^\\d+#\\d+#\\d+.*", text)) return "QRIS";
      if (text.startsWith("BIAYA ADMINISTRASI") || text.startsWith("BIAYA ADM")) return "BIAYA ADMINISTRASI";
      if (text.startsWith("BIAYA BULANAN ATM")) return "BIAYA ATM";
      if (text.contains("_OB_")) return "OVERBOOKING";

      return fallback(text);
   }
}
