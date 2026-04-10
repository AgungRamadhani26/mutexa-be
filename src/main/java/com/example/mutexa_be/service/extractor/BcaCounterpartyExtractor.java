package com.example.mutexa_be.service.extractor;

import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BcaCounterpartyExtractor extends AbstractCounterpartyExtractor {

   @Override
   public String getBankName() {
      return "BCA";
   }

   @Override
   public String extract(String rawDescription, boolean isCredit) {
      if (rawDescription == null || rawDescription.trim().isEmpty()) return null;
      String text = normalizeText(rawDescription);

      Matcher mCena = Pattern.compile("CENAIDJA/([A-Z][A-Z0-9 ]+?)(?:\\s+PT)?\\s+\\d").matcher(text);
      if (mCena.find()) return truncate(mCena.group(1).trim());

      Matcher mTrsf = Pattern.compile("TRSF .+?\\d+[,.]\\d{2,}\\s+([A-Z][A-Z ]{2,})$").matcher(text);
      if (mTrsf.find()) return truncate(mTrsf.group(1).trim());

      Matcher mSetor = Pattern.compile("SETOR(?:AN)?\\s+(?:TUNAI|SALES)?\\s*([A-Z][A-Z ]{2,}?)\\s+\\d{2}-\\d").matcher(text);
      if (mSetor.find()) return truncate(mSetor.group(1).trim());

      Matcher mDari = Pattern.compile("(?:DARI|KEPADA|KE)\\s+([A-Z][A-Z0-9 ]{2,}?)(?:\\s+TRANSFER|\\s+\\d{10,}|$)").matcher(text);
      if (mDari.find()) {
         String name = mDari.group(1).trim();
         if (name.length() > 3) return truncate(name);
      }

      Matcher mCorp = Pattern.compile("((?:PT|CV)\\.?)\\s+([A-Z][A-Z0-9 ]{2,}?)(?:\\s+\\d|$)").matcher(text);
      if (mCorp.find()) return truncate(mCorp.group(0).replaceAll("\\s+\\d.*", "").trim());

      if (text.equals("INTEREST CREDIT")) return "INTEREST CREDIT";
      if (text.contains("OD INT CHARGE")) return "OD INT CHARGE";
      if (text.startsWith("BIAYA ADM")) return "BIAYA ADMINISTRASI";

      return fallback(text);
   }
}
