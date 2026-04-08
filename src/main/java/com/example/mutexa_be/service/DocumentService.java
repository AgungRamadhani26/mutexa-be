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
import com.example.mutexa_be.service.parser.bri.BriPdfParserService;
import com.example.mutexa_be.service.parser.mandiri.MandiriPdfParserService;
import com.example.mutexa_be.service.parser.uob.UobPdfParserService;
import com.example.mutexa_be.service.parser.bca.BcaPdfParserService;
import com.example.mutexa_be.service.parser.bca.BcaImageParserService;
import com.example.mutexa_be.service.CategorizationService;
import com.example.mutexa_be.service.AnomalyDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.example.mutexa_be.dto.response.AccountWithDocumentsResponse;
import com.example.mutexa_be.dto.response.DocumentListResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

   private final MutationDocumentRepository mutationDocumentRepository;
   private final BankAccountRepository bankAccountRepository;
   private final BankTransactionRepository bankTransactionRepository;
   private final BriPdfParserService briPdfParserService;
   private final MandiriPdfParserService mandiriPdfParserService;
   private final UobPdfParserService uobPdfParserService;
   private final BcaPdfParserService bcaPdfParserService;
   private final BcaImageParserService bcaImageParserService;
   private final CategorizationService categorizationService;
   private final AnomalyDetectionService anomalyDetectionService;

   // Lokasi folder sementara tempat menyimpan file PDF/Gambar user supaya tidak membebani RAM
   private final String UPLOAD_DIR = "uploads/";

   public List<AccountWithDocumentsResponse> getAccountsWithDocumentCount() {
       return bankAccountRepository.getAccountsWithDocumentCount();
   }

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

   public MutationDocument uploadAndRegisterDocument(UploadDocumentRequest request) {
      MultipartFile file = request.getFile();

      if (file == null || file.isEmpty()) {
         throw new IllegalArgumentException("File upload tidak boleh kosong");
      }

      // 1. Cari atau buat entitas Rekening Bank (Upsert)
      BankAccount account = bankAccountRepository.findByAccountNumber(request.getAccountNumber())
            .orElseGet(() -> {
               log.info("Rekening baru terdeteksi, menyimpan rekening: {}", request.getAccountNumber());
               return bankAccountRepository.save(BankAccount.builder()
                     .accountNumber(request.getAccountNumber())
                     .accountName(request.getAccountName())
                     .bankName(request.getBankName().toUpperCase())
                     .build());
            });

      try {
         // 2. Simpan fisik file ke storage lokal folder `uploads/`
         Path uploadPath = Paths.get(UPLOAD_DIR);
         if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
         }

         String uniqueFileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
         Path filePath = uploadPath.resolve(uniqueFileName);
         Files.copy(file.getInputStream(), filePath);

         // 3. Deteksi Tipe File (Pdf Digital vs Image/Scan dll)
         DocumentType detectedType = detectDocumentType(filePath.toFile(), file.getContentType(),
               file.getOriginalFilename());

         // 4. Rekam ke Database dengan status awal PARSING
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

         // 5. PANGGIL PARSER SESUAI TIPE FILE & BANK SEKARANG JUGA!
         if (detectedType == DocumentType.PDF_DIGITAL) {
            String bankName = request.getBankName().toUpperCase();
            try {
               log.info("Memulai parsing PDF {}...", bankName);
               List<BankTransaction> extractedTxs = new ArrayList<>();

               if (bankName.equals("BRI")) {
                  extractedTxs = briPdfParserService.parse(document, filePath.toString());
               } else if (bankName.equals("MANDIRI")) {
                  extractedTxs = mandiriPdfParserService.parse(document, filePath.toString());
               } else if (bankName.equals("UOB")) {
                  extractedTxs = uobPdfParserService.parse(document, filePath.toString());
               } else if (bankName.equals("BCA")) {
                  extractedTxs = bcaPdfParserService.parse(document, filePath.toString());
               } else {
                  log.warn("Bank {} belum ada Regex Parser PDF-nya. Ditandai FAILED.", bankName);
                  document.setStatus(DocumentStatus.FAILED);
                  document.setErrorMessage("Parser PDF untuk bank " + bankName + " belum tersedia.");
                  mutationDocumentRepository.save(document);
                  return document;
               }

               // Proses filter duplikasi & Update Document Period
               List<BankTransaction> txToSave = new ArrayList<>();
               int duplicateCount = 0;
               LocalDate minDate = null;
               LocalDate maxDate = null;

               for (BankTransaction tx : extractedTxs) {
                  if (tx.getTransactionDate() != null) {
                     if (minDate == null || tx.getTransactionDate().isBefore(minDate))
                        minDate = tx.getTransactionDate();
                     if (maxDate == null || tx.getTransactionDate().isAfter(maxDate))
                        maxDate = tx.getTransactionDate();
                  }

                  String hash = tx.getDuplicateHash();
                  if (bankTransactionRepository.existsByDuplicateHash(hash)) {
                     duplicateCount++;
                  } else {
                     txToSave.add(tx);
                  }
               }

               // 1. Kategorisasi Tipe Transaksi (Income, Transfer, Tax, etc)
               categorizationService.enrichUnclassified(txToSave);

               // 2. Deteksi Anomali pada rangkaian transaksi (Window Dressing, Retur, Nominal Aneh)
               anomalyDetectionService.detectAnomalies(txToSave);

               // 3. Simpan ke database
               bankTransactionRepository.saveAll(txToSave);

               // Update document properties
               if (minDate != null)
                  document.setPeriodStart(minDate);
               if (maxDate != null)
                  document.setPeriodEnd(maxDate);

               document.setStatus(DocumentStatus.SUCCESS);
               mutationDocumentRepository.save(document);

               log.info("Parsing Selesai. Disimpan: {}, Duplikat diabaikan: {}", txToSave.size(), duplicateCount);
            } catch (Exception e) {
               log.error("Gagal saat mencoba mem-parse PDF {}: {}", bankName, e.getMessage());
               document.setStatus(DocumentStatus.FAILED);
               document.setErrorMessage(e.getMessage());
               mutationDocumentRepository.save(document);
            }
         } else if (detectedType == DocumentType.IMAGE_SCAN) {
            // JIKA FILE ADALAH IMAGE_SCAN -> Mulai proses dengan Tesseract OCR
            log.info("Tipe Dokumen IMAGE_SCAN terdeteksi. Mengecek dukungan OCR Bank...");

            if (request.getBankName().equalsIgnoreCase("BCA")) {
               try {
                  log.info("Merutekan PDF Scanner ke Parser BCA (Tesseract OCR)...");
                  List<BankTransaction> extractedOcrTxs = bcaImageParserService.parseAndSave(document,
                        filePath.toString());

                  if (!extractedOcrTxs.isEmpty()) {
                     document.setStatus(DocumentStatus.SUCCESS);
                     mutationDocumentRepository.save(document);
                  } else {
                     document.setStatus(DocumentStatus.FAILED);
                     document.setErrorMessage("Gagal menemukan transaksi melalui pemindaian OCR (PaddleOCR) BCA.");
                     mutationDocumentRepository.save(document);
                  }
               } catch (Exception e) {
                  log.error("Proses Tesseract OCR BCA Gagal Berjalan: {}", e.getMessage(), e);
                  document.setStatus(DocumentStatus.FAILED);
                  document.setErrorMessage("Proses Tesseract OCR Terhenti: " + e.getMessage());
                  mutationDocumentRepository.save(document);
               }
            } else {
               log.warn("Bank {} belum didukung untuk proses OCR Image Scan. Ditandai FAILED.", request.getBankName());
               document.setStatus(DocumentStatus.FAILED);
               document.setErrorMessage("Parser OCR Scanner untuk bank " + request.getBankName() + " belum tersedia.");
               mutationDocumentRepository.save(document);
            }
         }

         return document;

      } catch (IOException e) {
         log.error("Gagal saat menyimpan file: {}", e.getMessage(), e);
         throw new RuntimeException("Gagal menyimpan file secara fisik ke storage");
      }
   }

   /**
    * Memeriksa apakah ini PDF murni (ada teksnya) atau hanya hasil scan / gambar.
    */
   private DocumentType detectDocumentType(File savedFile, String contentType, String originalFilename) {
      if (contentType != null && (contentType.startsWith("image/") || originalFilename.toLowerCase().endsWith(".jpg")
            || originalFilename.toLowerCase().endsWith(".png"))) {
         return DocumentType.IMAGE_SCAN;
      }

      if (originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf")) {
         try (PDDocument document = Loader.loadPDF(savedFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String text = stripper.getText(document);

            if (text == null || text.trim().isEmpty()) {
               return DocumentType.IMAGE_SCAN;
            } else {
               return DocumentType.PDF_DIGITAL;
            }
         } catch (Exception e) {
            log.warn("Tidak dapat mengekstrak teks awal PDF ini, dianggap sebagai IMAGE_SCAN", e);
            return DocumentType.IMAGE_SCAN;
         }
      }

      return DocumentType.IMAGE_SCAN;
   }
}