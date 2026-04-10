package com.example.mutexa_be.service.extractor;

import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UobCounterpartyExtractor extends AbstractCounterpartyExtractor {

   @Override
   public String getBankName() {
      return "UOB";
   }

   @Override
   public String extract(String rawDescription, boolean isCredit) {
      if (rawDescription == null || rawDescription.trim().isEmpty()) return null;
      String text = normalizeText(rawDescription);

      Matcher mPt = Pattern.compile("(?:PT\\.?|CV\\.?)\\s+([A-Z][A-Z ]{2,})").matcher(text);
      if (mPt.find()) return truncate(mPt.group(0).replaceAll("\\s+\\d.*", "").trim());

      Matcher mMisc = Pattern.compile("(?:MISC CREDIT|MISC DEBIT)\\s+TRANSFER DANA.+?([A-Z]{2,}(?:\\s+[A-Z]{2,})+)\\s*$").matcher(text);
      if (mMisc.find()) return truncate(mMisc.group(1).trim());

      if (text.equals("INTEREST CREDIT")) return "INTEREST CREDIT";
      if (text.equals("OD INT CHARGE") || text.startsWith("OD INT CHARGE")) return "OD INT CHARGE";
      if (text.startsWith("WITHHOLDING TAX")) return "WITHHOLDING TAX";
      if (text.contains("PENEMPATAN DEPOSITO")) return "PENEMPATAN DEPOSITO";
      if (text.startsWith("CASH")) return "CASH";
      if (text.startsWith("MISC CREDIT")) return "MISC CREDIT";
      if (text.startsWith("MISC DEBIT")) return "MISC DEBIT";

      return fallback(text);
   }
}
