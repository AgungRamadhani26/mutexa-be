package com.example.mutexa_be.service;

import com.example.mutexa_be.dto.request.UploadDocumentRequest;
import com.example.mutexa_be.entity.BankAccount;
import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.entity.enums.DocumentStatus;
import com.example.mutexa_be.entity.enums.DocumentType;
import com.example.mutexa_be.repository.BankAccountRepository;
import com.example.mutexa_be.repository.BankTransactionRepository;
import com.example.mutexa_be.repository.MutationDocumentRepository;
import com.example.mutexa_be.service.parser.bca.BcaImageParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.example.mutexa_be.dto.response.AccountWithDocumentsResponse;
import com.example.mutexa_be.dto.response.DocumentListResponse;

/**
 * Service Utama (Orchestrator) untuk mengelola proses upload dan pemrosesan dokumen mutasi.
 *
 * Prinsip SOLID yang diterapkan:
 * - SRP: Class ini HANYA mengatur alur (orchestration), bukan mengerjakan sendiri.
 *        Parsing → ParserRouterService, File I/O → FileStorageService,
 *        Kategorisasi → CategorizationService, Anomali → AnomalyDetectionService.
 * - OCP: Menambah bank baru TIDAK perlu mengubah class ini sama sekali.
 * - DIP: Depend pada abstraksi (interface) bukan concrete implementation.
 *
 * Alur utama uploadAndRegisterDocument():
 * 1. FileStorageService.saveFile() → simpan file ke disk
 * 2. FileStorageService.detectType() → PDF digital atau scan?
 * 3. ParserRouterService.routeAndParse() → rutekan ke parser bank yang sesuai
 * 4. Filter duplikasi via hash → hindari data ganda
 * 5. CategorizationService.enrichUnclassified() → klasifikasi tipe transaksi
 * 6. AnomalyDetectionService.detectAnomalies() → deteksi pola anomali
 * 7. bankTransactionRepository.saveAll() → simpan ke database
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

   // --- DEPENDENCY INJECTION (semua via constructor injection oleh Lombok @RequiredArgsConstructor) ---
   private final MutationDocumentRepository mutationDocumentRepository;
   private final BankAccountRepository bankAccountRepository;
   private final BankTransactionRepository bankTransactionRepository;

   // Service-service yang menerapkan prinsip SRP (masing-masing punya 1 tanggung jawab)
   private final FileStorageService fileStorageService;           // SRP: File I/O + deteksi tipe
   private final ParserRouterService parserRouterService;         // SRP: Routing parser bank
   private final BcaImageParserService bcaImageParserService;     // Parser khusus OCR BCA
   private final CategorizationService categorizationService;     // SRP: Klasifikasi transaksi
   private final AnomalyDetectionService anomalyDetectionService; // SRP: Deteksi anomali

   // ==========================================
   // QUERY METHODS (Baca Data)
   // ==========================================

   /**
    * Mengambil daftar semua rekening bank beserta jumlah dokumen masing-masing.
    * Digunakan oleh Level 1 Dashboard (tampilan daftar rekening).
    *
    * @return List rekening dengan hitungan dokumen
    */
   public List<AccountWithDocumentsResponse> getAccountsWithDocumentCount() {
      return bankAccountRepository.getAccountsWithDocumentCount();
   }

   /**
    * Mengambil daftar dokumen mutasi milik satu rekening tertentu.
    * Digunakan oleh Level 2 Dashboard (tampilan daftar dokumen per rekening).
    *
    * @param accountId ID rekening bank
    * @return List dokumen yang disortir berdasarkan waktu upload terbaru
    */
   public List<DocumentListResponse> getDocumentsByAccountId(Long accountId) {
      List<MutationDocument> docs = mutationDocumentRepository.findAllByBankAccountIdOrderByCreatedAtDesc(accountId);
      return docs.stream().map(d -> DocumentListResponse.builder()
              .id(d.getId())
              .fileName(d.getFileName())
              .fileType(d.getFileType() != null ? d.getFileType().name() : null)
              .status(d.getStatus() != null ? d.getStatus().name() : null)
              .errorMessage(d.getErrorMessage())
              .periodStart(d.getPeriodStart())
              .periodEnd(d.getPeriodEnd())
              .createdAt(d.getCreatedAt())
              .build()
      ).collect(Collectors.toList());
   }

   // ==========================================
   // PROSES UPLOAD DOKUMEN (Command/Write)
   // ==========================================

   /**
    * Mengkoordinasikan seluruh proses upload dan pemrosesan dokumen mutasi.
    * Method ini adalah orchestrator yang mendelegasikan pekerjaan ke service spesifik.
    *
    * @param request DTO berisi file, nomor rekening, nama bank, dll
    * @return Entity MutationDocument yang sudah tersimpan (status SUCCESS/FAILED)
    */
   public MutationDocument uploadAndRegisterDocument(UploadDocumentRequest request) {
      MultipartFile file = request.getFile();

      if (file == null || file.isEmpty()) {
         throw new IllegalArgumentException("File upload tidak boleh kosong");
      }

      // 1. Cari atau buat entitas Rekening Bank (Upsert)
      BankAccount account = findOrCreateAccount(request);

      try {
         // 2. Simpan file ke disk via FileStorageService (SRP: file I/O dipisahkan)
         Path filePath = fileStorageService.saveFile(file);

         // 3. Deteksi tipe dokumen via FileStorageService (PDF digital vs scan)
         DocumentType detectedType = fileStorageService.detectType(
               filePath.toFile(), file.getContentType(), file.getOriginalFilename());

         // 4. Catat metadata dokumen ke database dengan status awal PARSING
         MutationDocument document = MutationDocument.builder()
               .bankAccount(account)
               .fileName(file.getOriginalFilename())
               .fileType(detectedType)
               .status(DocumentStatus.PARSING)
               .filePath(filePath.toString())
               .periodStart(LocalDate.now())
               .periodEnd(LocalDate.now())
               .build();
         document = mutationDocumentRepository.save(document);

         // 5. Proses parsing sesuai tipe dokumen
         if (detectedType == DocumentType.PDF_DIGITAL) {
            processPdfDigital(document, request.getBankName().toUpperCase(), filePath.toString());
         } else if (detectedType == DocumentType.IMAGE_SCAN) {
            processImageScan(document, request.getBankName(), filePath.toString());
         }

         return document;

      } catch (IOException e) {
         log.error("Gagal saat menyimpan file: {}", e.getMessage(), e);
         throw new RuntimeException("Gagal menyimpan file secara fisik ke storage");
      }
   }

   // ==========================================
   // PRIVATE HELPER METHODS
   // ==========================================

   /**
    * Mencari rekening bank berdasarkan nomor rekening.
    * Jika tidak ditemukan, buat entitas baru (Upsert pattern).
    */
   private BankAccount findOrCreateAccount(UploadDocumentRequest request) {
      return bankAccountRepository.findByAccountNumber(request.getAccountNumber())
            .orElseGet(() -> {
               log.info("Rekening baru terdeteksi, menyimpan rekening: {}", request.getAccountNumber());
               return bankAccountRepository.save(BankAccount.builder()
                     .accountNumber(request.getAccountNumber())
                     .accountName(request.getAccountName())
                     .bankName(request.getBankName().toUpperCase())
                     .build());
            });
   }

   /**
    * Memproses dokumen PDF digital.
    * Alur: ParserRouter → filter duplikasi → kategorisasi → anomali → simpan DB.
    */
   private void processPdfDigital(MutationDocument document, String bankName, String filePath) {
      try {
         log.info("Memulai parsing PDF {}...", bankName);

         // Delegasikan parsing ke ParserRouterService (OCP: tidak perlu if/else di sini)
         List<BankTransaction> extractedTxs = parserRouterService.routeAndParse(bankName, document, filePath);

         // Filter duplikasi dan hitung periode dokumen
         List<BankTransaction> txToSave = filterDuplicatesAndUpdatePeriod(extractedTxs, document);

         // Pipeline pemrosesan transaksi (masing-masing service punya SRP tersendiri)
         categorizationService.enrichUnclassified(txToSave);        // Step 1: Klasifikasi
         anomalyDetectionService.detectAnomalies(txToSave);          // Step 2: Deteksi anomali
         bankTransactionRepository.saveAll(txToSave);                // Step 3: Simpan

         document.setStatus(DocumentStatus.SUCCESS);
         mutationDocumentRepository.save(document);

         log.info("Parsing Selesai. Total transaksi disimpan: {}", txToSave.size());

      } catch (UnsupportedOperationException e) {
         // Bank belum ada parser-nya
         log.warn(e.getMessage());
         document.setStatus(DocumentStatus.FAILED);
         document.setErrorMessage(e.getMessage());
         mutationDocumentRepository.save(document);
      } catch (Exception e) {
         log.error("Gagal saat parsing PDF {}: {}", bankName, e.getMessage());
         document.setStatus(DocumentStatus.FAILED);
         document.setErrorMessage(e.getMessage());
         mutationDocumentRepository.save(document);
      }
   }

   /**
    * Memproses dokumen gambar/scan menggunakan OCR.
    * Saat ini baru mendukung BCA Image Parser.
    */
   private void processImageScan(MutationDocument document, String bankName, String filePath) {
      if (bankName.equalsIgnoreCase("BCA")) {
         try {
            log.info("Merutekan ke Parser BCA (Tesseract OCR)...");
            List<BankTransaction> extractedOcrTxs = bcaImageParserService.parseAndSave(document, filePath);

            if (!extractedOcrTxs.isEmpty()) {
               document.setStatus(DocumentStatus.SUCCESS);
            } else {
               document.setStatus(DocumentStatus.FAILED);
               document.setErrorMessage("Gagal menemukan transaksi melalui OCR BCA.");
            }
         } catch (Exception e) {
            log.error("Proses OCR BCA Gagal: {}", e.getMessage(), e);
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage("Proses OCR Terhenti: " + e.getMessage());
         }
      } else {
         log.warn("Bank {} belum didukung untuk proses OCR Image Scan.", bankName);
         document.setStatus(DocumentStatus.FAILED);
         document.setErrorMessage("Parser OCR untuk bank " + bankName + " belum tersedia.");
      }
      mutationDocumentRepository.save(document);
   }

   /**
    * Memfilter transaksi duplikat berdasarkan hash dan menghitung periode dokumen.
    * Transaksi yang hash-nya sudah ada di database akan dilewati.
    *
    * @return List transaksi yang lolos filter (tidak duplikat)
    */
   private List<BankTransaction> filterDuplicatesAndUpdatePeriod(
         List<BankTransaction> extractedTxs, MutationDocument document) {

      List<BankTransaction> txToSave = new ArrayList<>();
      int duplicateCount = 0;
      LocalDate minDate = null;
      LocalDate maxDate = null;

      for (BankTransaction tx : extractedTxs) {
         // Hitung periode (tanggal paling awal & paling akhir)
         if (tx.getTransactionDate() != null) {
            if (minDate == null || tx.getTransactionDate().isBefore(minDate))
               minDate = tx.getTransactionDate();
            if (maxDate == null || tx.getTransactionDate().isAfter(maxDate))
               maxDate = tx.getTransactionDate();
         }

         // Cek duplikasi berdasarkan hash unik
         if (bankTransactionRepository.existsByDuplicateHash(tx.getDuplicateHash())) {
            duplicateCount++;
         } else {
            txToSave.add(tx);
         }
      }

      // Update periode dokumen berdasarkan range tanggal transaksi
      if (minDate != null) document.setPeriodStart(minDate);
      if (maxDate != null) document.setPeriodEnd(maxDate);

      log.info("Filter duplikasi: {} disimpan, {} diabaikan", txToSave.size(), duplicateCount);
      return txToSave;
   }
}