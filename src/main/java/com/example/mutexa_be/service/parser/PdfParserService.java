package com.example.mutexa_be.service.parser;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;

import java.util.List;

/**
 * Interface dasar untuk semua jenis parser PDF bank.
 * Dengan memakai ini, kita bisa memastikan baik BRIParser maupun BCAParser
 * nantinya wajib memiliki fungsi parse() yang mengembalikan hasil List
 * transaksi yang siap disimpan.
 */
public interface PdfParserService {

   /**
    * Mengekstrak tabel mutasi dari file PDF menjadi barisan Entity BankTransaction
    *
    * @param document Entity file rekaman database untuk direlasikan transaksinya
    * @param filePath Path aktual dari file PDF di dalam disk (misal
    *                 uploads/123.pdf)
    * @return List transaksi mentah (belum termasuk pengkategorian analitik)
    */
   List<BankTransaction> parse(MutationDocument document, String filePath);

}