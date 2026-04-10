package com.example.mutexa_be.service.parser;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;

import java.util.List;

/**
 * Interface abstraksi untuk semua parser PDF mutasi bank.
 *
 * Prinsip SOLID yang diterapkan:
 * - OCP (Open/Closed): Menambah bank baru cukup buat class baru yang implements interface ini.
 * - DIP (Dependency Inversion): Service lain depend pada interface ini, bukan concrete class.
 * - LSP (Liskov Substitution): Semua implementasi bisa dipertukarkan tanpa mengubah perilaku.
 *
 * Setiap implementasi wajib:
 * 1. getBankName() → mengembalikan kode bank (contoh: "BRI", "BCA")
 * 2. parse()       → mengekstrak transaksi dari file PDF menjadi List entity
 */
public interface PdfParserService {

   /**
    * Mengembalikan kode bank yang ditangani oleh parser ini.
    * Digunakan oleh ParserRouterService untuk auto-registration.
    * Contoh return: "BRI", "MANDIRI", "UOB", "BCA"
    */
   String getBankName();

   /**
    * Mengekstrak tabel mutasi dari file PDF menjadi barisan Entity BankTransaction.
    *
    * @param document Entity file rekaman database untuk direlasikan transaksinya
    * @param filePath Path aktual dari file PDF di dalam disk (misal uploads/123.pdf)
    * @return List transaksi mentah (belum termasuk pengkategorian analitik)
    */
   List<BankTransaction> parse(MutationDocument document, String filePath);

}