package com.example.mutexa_be.service;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.service.parser.PdfParserService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service untuk merutekan proses parsing ke parser bank yang sesuai.
 *
 * Prinsip SOLID yang diterapkan:
 * - SRP: Class ini HANYA bertugas merutekan, tidak melakukan parsing sendiri.
 * - OCP: Menambah bank baru cukup buat class parser baru yang implements PdfParserService.
 *        Parser baru otomatis ter-register karena Spring inject semua implementasi via List.
 * - DIP: Depend pada abstraksi PdfParserService, bukan concrete class.
 *
 * Cara kerja:
 * 1. Spring inject SEMUA bean yang implements PdfParserService ke dalam List.
 * 2. @PostConstruct membangun Map<bankName, parser> secara otomatis.
 * 3. Method routeAndParse() tinggal lookup dari Map — tidak ada if/else chain.
 */
@Slf4j
@Service
public class ParserRouterService {

   // Map<BankName, ParserImplementation> yang dibangun otomatis saat startup
   private final Map<String, PdfParserService> parserMap = new HashMap<>();

   // Spring inject semua bean yang implements PdfParserService ke List ini
   private final List<PdfParserService> allParsers;

   public ParserRouterService(List<PdfParserService> allParsers) {
      this.allParsers = allParsers;
   }

   /**
    * Dipanggil otomatis setelah bean dibuat.
    * Membangun lookup Map dari getBankName() masing-masing parser.
    * Contoh isi map: {"BRI" -> BriPdfParserService, "BCA" -> BcaPdfParserService, ...}
    */
   @PostConstruct
   public void init() {
      for (PdfParserService parser : allParsers) {
         String bankName = parser.getBankName();
         parserMap.put(bankName, parser);
         log.info("Parser terdaftar untuk bank: {}", bankName);
      }
      log.info("Total parser terdaftar: {}", parserMap.size());
   }

   /**
    * Merutekan proses parsing ke parser bank yang sesuai.
    *
    * @param bankName  Kode bank (contoh: "BRI", "BCA", "MANDIRI", "UOB")
    * @param document  Entity MutationDocument untuk direlasikan
    * @param filePath  Path file PDF di disk
    * @return List transaksi hasil parsing
    * @throws UnsupportedOperationException jika bank belum ada parser-nya
    */
   public List<BankTransaction> routeAndParse(String bankName, MutationDocument document, String filePath) {
      PdfParserService parser = parserMap.get(bankName);

      if (parser == null) {
         throw new UnsupportedOperationException(
               "Parser PDF untuk bank " + bankName + " belum tersedia. "
               + "Bank yang didukung: " + parserMap.keySet());
      }

      log.info("Merutekan parsing ke {} parser...", bankName);
      return parser.parse(document, filePath);
   }

   /**
    * Mengecek apakah bank tertentu sudah memiliki parser yang terdaftar.
    *
    * @param bankName Kode bank untuk dicek
    * @return true jika parser tersedia
    */
   public boolean isSupported(String bankName) {
      return parserMap.containsKey(bankName);
   }
}
