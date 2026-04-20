package com.example.mutexa_be.service;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.enums.TransactionCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CategorizationService {

   public void enrichUnclassified(List<BankTransaction> transactions) {
      log.info("Memulai proses klasifikasi transaksi menggunakan aturan Keyword (Pattern Matching)...");

      if (transactions.isEmpty()) {
         return;
      }

      // 2. Lakukan iterasi ke masing-masing transaksi dan klasifikasi berdasarkan deskripsi
      for (BankTransaction tx : transactions) {
         // Hanya proses jika belum dikategorikan atau kategori default (TRANSFER) 
         // untuk memastikan record lama yang mungkin masih UNCLASSIFIED terupdate.

         String desc = tx.getNormalizedDescription() != null ? tx.getNormalizedDescription().toLowerCase() : "";
         String rawDesc = tx.getRawDescription() != null ? tx.getRawDescription().toLowerCase() : "";

         // Gabungkan deskripsi untuk pencegahan jika normalized string kosong
         String textToSearch = desc + " " + rawDesc;

         TransactionCategory categorizedAs = TransactionCategory.TRANSFER; // Default Fallback

         // Kategori: ADMIN
         if (matchesKeyword(textToSearch,
               "adm", "admin", "administrasi", "biaya adm", "biaya admin", "biaya administrasi",
               "adm rek", "admin rekening", "biaya admin bulanan", "admin bulanan",
               "monthly fee", "monthly admin", "admin fee", "account fee",
               "account maintenance", "maintenance fee", "service charge", "biaya layanan",
               "biaya kartu", "annual fee", "biaya materai", "biaya sms", "sms banking",
               "biaya notifikasi", "biaya saldo minimum", "fall below fee", "potongan bulanan",
               "biaya pengelolaan", "provisi")) {
            categorizedAs = TransactionCategory.ADMIN;
         }
         // Kategori: TAX (Pajak)
         else if (matchesKeyword(textToSearch,
               "pajak", "tax", "pph", "ppn", "pjk", "pajak bunga",
               "pajak penghasilan", "pajak bagi hasil", "potongan pajak",
               "pajak deposito", "withholding tax", "wht", "tx", "pajak hadiah")) {
            categorizedAs = TransactionCategory.TAX;
         }
         // Kategori: INTEREST (Bunga Bank)
         else if (matchesKeyword(textToSearch,
               "bunga", "interest", "bagi hasil", "nisbah",
               "bunga tabungan", "bunga deposito", "jasa giro", "int",
               "credit interest", "kredit bunga", "bunga harian",
               "tambahan bunga", "pendapatan bunga", "interest income")) {
            categorizedAs = TransactionCategory.INTEREST;
         }
         
         // Set Kategori (Jika tidak match ketiganya di atas, maka otomatis categorizedAs adalah TRANSFER)
         tx.setCategory(categorizedAs);
      }

      log.info("Proses klasifikasi keyword selesai. Total transaksi yang diproses: {}", transactions.size());
   }

   /**
    * Mengecek apakah teks mengandung salah satu dari keyword yang diberikan secara presisi kata.
    */
   private boolean matchesKeyword(String text, String... keywords) {
      for (String keyword : keywords) {
         if (text.contains(keyword.toLowerCase())) {
            return true;
         }
      }
      return false;
   }
}
