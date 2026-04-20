package com.example.mutexa_be.service;

import com.example.mutexa_be.entity.enums.TransactionCategory;
import com.example.mutexa_be.service.extractor.CounterpartyExtractor;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service untuk memoles atau membersihkan teks / deskripsi raw dari mutasi,
 * mendelegasikan proses ekstraksi kepada class spesifik bank (SRP, OCP).
 */
@Slf4j
@Service
public class TransactionRefinementService {

   private final Map<String, CounterpartyExtractor> extractorMap = new HashMap<>();
   private final List<CounterpartyExtractor> allExtractors;
   private CounterpartyExtractor genericExtractor;

   public TransactionRefinementService(List<CounterpartyExtractor> allExtractors) {
      this.allExtractors = allExtractors;
   }

   @PostConstruct
   public void init() {
      for (CounterpartyExtractor extractor : allExtractors) {
         String bankName = extractor.getBankName().toUpperCase();
         extractorMap.put(bankName, extractor);
         if ("GENERIC".equals(bankName)) {
            this.genericExtractor = extractor;
         }
      }
   }

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
      return normalized.trim();
   }

   /**
    * Menerima bankName untuk routing ke extractor bank-specific.
    * Ini adalah entry point utama yang dipanggil dari masing-masing parser.
    */
   public String extractCounterpartyName(String bankName, String rawDescription, boolean isCredit) {
      if (rawDescription == null || rawDescription.trim().isEmpty())
         return null;

      CounterpartyExtractor extractor = bankName != null ? extractorMap.get(bankName.toUpperCase()) : null;
      if (extractor == null) {
         extractor = genericExtractor; // fallback to generic
      }

      return extractor != null ? extractor.extract(rawDescription, isCredit) : "-";
   }

   /**
    * Method lama (backward compatible) — dipakai sebagai generic fallback.
    */
   public String extractCounterpartyName(String rawDescription, boolean isCredit) {
      return extractCounterpartyName("GENERIC", rawDescription, isCredit);
   }

   /**
    * Menganalisis description dan jumlah masuk/keluar untuk memandu ke kategori.
    */
   public TransactionCategory categorizeTransaction(String normalizedDescription, boolean isCredit) {
      return TransactionCategory.UNCLASSIFIED;
   }
}
