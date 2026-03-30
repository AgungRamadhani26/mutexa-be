package com.example.mutexa_be.service;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.enums.TransactionCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CategorizationService {

   public void enrichUnclassified(List<BankTransaction> transactions) {
      log.info("Memulai proses klasifikasi transaksi menggunakan aturan Keyword (Pattern Matching)...");

      // 1. Kumpulkan semua transaksi yang UNCLASSIFIED
      List<BankTransaction> unclassifiedTxs = transactions.stream()
            .filter(tx -> tx.getCategory() == TransactionCategory.UNCLASSIFIED)
            .collect(Collectors.toList());

      if (unclassifiedTxs.isEmpty()) {
         return;
      }

      // 2. Lakukan iterasi ke masing-masing transaksi dan klasifikasi berdasarkan
      // deskripsi
      for (BankTransaction tx : unclassifiedTxs) {
         String desc = tx.getNormalizedDescription() != null ? tx.getNormalizedDescription().toLowerCase() : "";
         String rawDesc = tx.getRawDescription() != null ? tx.getRawDescription().toLowerCase() : "";

         // Gabungkan deskripsi untuk pencegahan jika normalized string kosong
         String textToSearch = desc + " " + rawDesc;

         TransactionCategory categorizedAs = TransactionCategory.UNCLASSIFIED;

         // Kategori: ADMIN
         if (matchesKeyword(textToSearch, "adm", "admin", "administrasi", "biaya admin", "adm inc", "fee", "provisi")) {
            categorizedAs = TransactionCategory.ADMIN;
         }
         // Kategori: TAX (Pajak)
         else if (matchesKeyword(textToSearch, "pajak", "tax", "pph")) {
            categorizedAs = TransactionCategory.TAX;
         }
         // Kategori: INTEREST (Bunga Bank)
         else if (matchesKeyword(textToSearch, "bunga", "interest", "bagi hasil", "nisbah")) {
            categorizedAs = TransactionCategory.INTEREST;
         }
         // Kategori: INCOME (Pendapatan/Gaji) - Biasanya dibatasi untuk tipe CR, tapi
         // dicheck via keyword dulu
         else if (matchesKeyword(textToSearch, "gaji", "salary", "payroll", "honor", "thr", "dividen")) {
            categorizedAs = TransactionCategory.INCOME;
         }
         // Kategori: TRANSFER
         else if (matchesKeyword(textToSearch, "trf", "transfer", "trsf", "pemindahan", "kiriman", "inkaso", "rtgs",
               "skn", "bifast", "bi-fast")) {
            categorizedAs = TransactionCategory.TRANSFER;
         }
         // Kategori: ANOMALY (Koreksi)
         else if (matchesKeyword(textToSearch, "koreksi", "reversal", "retur", "batal", "cancel")) {
            categorizedAs = TransactionCategory.ANOMALY;
         }

         // Set kategori baru jika ditemukan. Jika tidak, akan tetap UNCLASSIFIED.
         if (categorizedAs != TransactionCategory.UNCLASSIFIED) {
            tx.setCategory(categorizedAs);
         }
      }

      log.info("Proses klasifikasi keyword selesai. Total transaksi yang diproses: {}", unclassifiedTxs.size());
   }

   /**
    * Mengecek apakah teks mengandung salah satu dari keyword yang diberikan secara
    * presisi kata.
    */
   private boolean matchesKeyword(String text, String... keywords) {
      for (String keyword : keywords) {
         // Menggunakan boundaries (\b) untuk memastikan pengecekan kata yang akurat
         // (tidak match bagian dalam kata lain)
         // Namun karena format bank kadang menempel (misal BIAYAADM), kita pakai
         // pencarian substring biasa (contains)
         // saja sesuai permintaan (e.g. adm, admin, administrasi -> contains "adm")
         if (text.contains(keyword.toLowerCase())) {
            return true;
         }
      }
      return false;
   }
}
