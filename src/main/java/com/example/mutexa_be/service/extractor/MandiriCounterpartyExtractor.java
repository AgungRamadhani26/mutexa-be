package com.example.mutexa_be.service.extractor;

import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MandiriCounterpartyExtractor extends AbstractCounterpartyExtractor {

   @Override
   public String getBankName() {
      return "MANDIRI";
   }

   @Override
   public String extract(String rawDescription, boolean isCredit) {
      if (rawDescription == null || rawDescription.trim().isEmpty()) return null;
      String text = normalizeText(rawDescription);

      Matcher mMcm = Pattern.compile("MCM\\s+INHOUSETRF\\s+(?:KE|DARI)\\s+([A-Z][A-Z ]+?)\\s+TRANSFER\\s+FEE", Pattern.CASE_INSENSITIVE).matcher(text);
      if (mMcm.find()) return truncate(mMcm.group(1).trim());

      Matcher mCena = Pattern.compile("CENAIDJA/([A-Z][A-Z ]+?)(?:\\s+PT\\s|\\s+\\d|\\s+AHSA|$)").matcher(text);
      if (mCena.find()) return truncate(mCena.group(1).trim());

      Matcher mBxn = Pattern.compile("(?:BNINIDJA|BRINIDJA|BMRIIDJA)/([A-Z][A-Z ]+?)(?:\\s+AHSA|\\s+\\d|$)").matcher(text);
      if (mBxn.find()) return truncate(mBxn.group(1).trim());

      Matcher mSetor = Pattern.compile("SETOR(?:AN)?\\s+(?:TUNAI\\s+)?([A-Z][A-Z ]{2,}?)\\s+\\d{2}-\\d").matcher(text);
      if (mSetor.find()) return truncate(mSetor.group(1).trim());

      Matcher mPinbuk = Pattern.compile("MCM\\s+INHOUSETRF\\s+DARI\\s+([A-Z][A-Z ]+?)(?:\\s+TRANSFER|$)", Pattern.CASE_INSENSITIVE).matcher(text);
      if (mPinbuk.find()) return truncate(mPinbuk.group(1).trim());

      if (text.contains("SETOR") && text.contains("KLIRING")) return "SETOR KLIRING";

      return fallback(text);
   }
}
