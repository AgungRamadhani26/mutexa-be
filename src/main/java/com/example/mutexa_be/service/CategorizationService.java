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
         if (matchesKeyword(textToSearch,
               "adm", "admin", "administrasi", "biaya adm", "biaya admin", "biaya administrasi",
               "adm rek", "admin rekening", "biaya admin bulanan", "admin bulanan",
               "monthly fee", "monthly admin", "admin fee", "account fee",
               "account maintenance", "maintenance fee", "service charge", "biaya layanan",
               // Tambahan spesifik bank lokal (Kartu, SMS, Materai, Penalti Saldo, Cetak)
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
         // Kategori: INCOME (Pendapatan/Omzet Bisnis/Gaji)
         // Terbatas pada kata yang PASTI merupakan penerimaan/pendapatan (arus kas masuk
         // riil)
         else if (matchesKeyword(textToSearch,
               "gaji", "salary", "payroll", "pyrl", "honor", "honorarium",
               "thr", "dividen", "dividend", "insentif", "incentive",
               "tunjangan", "remunerasi", "pensiun", "upah", "uang saku",
               "komisi", "commission", "sales", "penjualan", "pendapatan",
               "revenue", "omset", "omzet", "penerimaan", "proyek", "subsidi",
               "modal", "setor", "setoran", "incoming", "kredit masuk",
               "setoran tunai", "settlement merchant", "pencairan dana")) {
            categorizedAs = TransactionCategory.INCOME;
         }
         // Kategori: TRANSFER (Metode Pembayaran, Kanal Transaksi, Perpindahan Dana
         // Umum)
         // Memisahkan transaksi general seperti QRIS, EDC, Pelunasan yang bisa jadi
         // Debit(Keluar) / Credit(Masuk)
         else if (matchesKeyword(textToSearch,
               "trf", "transfer", "trsf", "pemindahan", "kiriman", "inkaso", "rtgs",
               "skn", "bifast", "bi-fast",

               "qris", "edc", "pembayaran", "bayar",
               "payment", "invoice", "inv", "tagihan", "pelunasan", "termin", "dp",
               "down payment", "virtual account", "va", "topup", "top up",
               "ewallet", "e-wallet", "ovo", "gopay", "shopeepay", "dana", "linkaja",
               "tarikan", "tarik tunai", "atm", "m-banking", "internet banking", "merchant",
               "mcm", "cms", "aft", "inhouse", "pindah buku", "overbooking", "pb", "bilyet")) {
            categorizedAs = TransactionCategory.TRANSFER;
         }

         // Catatan Arsitektur:
         // Anomaly dipisahkan dari Kategori agar 'Anomaly' menjadi flag/atribut tambahan
         // (bukan mutually exclusive).
         // Proses Anomaly (misal check pola window dressing, z-score nominal)
         // akan dikerjakan di 'AnomalyDetectionService' tersendiri setelah proses ini
         // selesai.

         // Set kategori baru jika ditemukan. Jika tidak, jadikan TRANSFER sebagai
         // default (fallback).
         if (categorizedAs != TransactionCategory.UNCLASSIFIED) {
            tx.setCategory(categorizedAs);
         } else {
            tx.setCategory(TransactionCategory.TRANSFER);
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
