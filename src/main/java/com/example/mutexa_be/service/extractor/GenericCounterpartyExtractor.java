package com.example.mutexa_be.service.extractor;

import org.springframework.stereotype.Service;

@Service
public class GenericCounterpartyExtractor extends AbstractCounterpartyExtractor {

   @Override
   public String getBankName() {
      return "GENERIC";
   }

   @Override
   public String extract(String rawDescription, boolean isCredit) {
      if (rawDescription == null || rawDescription.trim().isEmpty()) return null;
      String text = normalizeText(rawDescription);
      return fallback(text);
   }
}
