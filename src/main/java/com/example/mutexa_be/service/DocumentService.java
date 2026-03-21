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
import com.example.mutexa_be.service.parser.BriPdfParserService;
import com.example.mutexa_be.service.parser.BcaImageParserService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

   private final MutationDocumentRepository mutationDocumentRepository;
   private final BankAccountRepository bankAccountRepository;
   private final BankTransactionRepository bankTransactionRepository;
   private final BriPdfParserService briPdfParserService;
   private final BcaImageParserService bcaImageParserService;

   // Lokasi folder sementara tempat menyimpan file PDF/Gambar user supaya tidak
   // membebani RAM
   private final String UPLOAD_DIR = "uploads/";

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
            Files.createDirectories(uploadPath); // Create folder kalau belum ada
         }

         // Nama file unik (mengurangi risiko bentrok jika ada file bernama sama diupload
         // bersamaan)
         String uniqueFileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
         Path filePath = uploadPath.resolve(uniqueFileName);
         Files.copy(file.getInputStream(), filePath);

         // 3. Deteksi Tipe File (Pdf Digital vs Image/Scan dll)
         DocumentType detectedType = detectDocumentType(filePath.toFile(), file.getContentType(),
               file.getOriginalFilename());

         // 4. Rekam ke Database dengan status awal PARSING (langsung kita proses)
         MutationDocument document = MutationDocument.builder()
               .bankAccount(account)
               .fileName(file.getOriginalFilename())
               .fileType(detectedType)
               .status(DocumentStatus.PARSING)
               .filePath(filePath.toString())
               .periodStart(LocalDate.now()) // Default dummy, bisa di-update nanti jika kita parse dari PDF header
               .periodEnd(LocalDate.now())
               .build();

         document = mutationDocumentRepository.save(document);

         // 5. PANGGIL PARSER SESUAI TIPE FILE & BANK SEKARANG JUGA!
         if (detectedType == DocumentType.PDF_DIGITAL) {
            if (request.getBankName().equalsIgnoreCase("BRI")) {
               try {
                  log.info("Memulai parsing PDF BRI...");
                  // Gunakan BriPdfParserService yang baru saja kita buat tadi
                  List<BankTransaction> extractedTxs = briPdfParserService.parse(document, filePath.toString());

                  // Filter anti-duplikasi sebelum di-simpan (Idempotensi)
                  List<BankTransaction> txToSave = new ArrayList<>();
                  int duplicateCount = 0;
                  for (BankTransaction tx : extractedTxs) {
                     if (bankTransactionRepository.existsByDuplicateHash(tx.getDuplicateHash())) {
                        duplicateCount++;
                     } else {
                        txToSave.add(tx);
                     }
                  }

                  // Simpan ke Tabel BankTransaction
                  bankTransactionRepository.saveAll(txToSave);

                  // Perbarui status dokumen
                  document.setStatus(DocumentStatus.SUCCESS);
                  mutationDocumentRepository.save(document);

                  log.info("Parsing Selesai. Disimpan: {}, Duplikat diabaikan: {}", txToSave.size(), duplicateCount);

               } catch (Exception e) {
                  log.error("Gagal saat mencoba mem-parse PDF: {}", e.getMessage());
                  document.setStatus(DocumentStatus.FAILED);
                  document.setErrorMessage(e.getMessage());
                  mutationDocumentRepository.save(document);
               }
            } else {
               // Untuk Bank Lain yang belum didukung parser digitalnya di aplikasi kita
               log.warn("Bank {} belum ada Regex Parser PDF-nya. Ditandai FAILED.", request.getBankName());
               document.setStatus(DocumentStatus.FAILED);
               document.setErrorMessage("Parser PDF untuk bank " + request.getBankName() + " belum tersedia.");
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
                     document.setErrorMessage("Gagal menemukan transaksi melalui pemindaian Gambar Tesseract OCR BCA.");
                     mutationDocumentRepository.save(document);
                  }
               } catch (Exception e) {
                  log.error("Proses Tesseract OCR BCA Gagal Berjalan: {}", e.getMessage(), e);
                  document.setStatus(DocumentStatus.FAILED);
                  document.setErrorMessage("Proses Tesseract OCR Terhenti: " + e.getMessage());
                  mutationDocumentRepository.save(document);
               }
            } else {
               // Default fall-back untuk bank lain yang IMAGE_SCAN selain BCA
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
      // Jika format yang di-upload dari awal adalah JPEG/PNG, otomatis itu adalah
      // IMAGE_SCAN
      if (contentType != null && (contentType.startsWith("image/") || originalFilename.toLowerCase().endsWith(".jpg")
            || originalFilename.toLowerCase().endsWith(".png"))) {
         return DocumentType.IMAGE_SCAN;
      }

      // Jika itu PDF, kita gunakan PDFBox seperti test sebelumnya untuk "mengintip"
      // apakah ada teks
      if (originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf")) {
         try (PDDocument document = Loader.loadPDF(savedFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1); // Cek halaman 1 saja agar efisien/cepat
            String text = stripper.getText(document);

            // Jika teks hasil pelepasan dari PDF kosong / hanya spasi, berarti ia adalah
            // Gambar dalam file PDF
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

      // Secara default (fallback)
      return DocumentType.IMAGE_SCAN;
   }
}