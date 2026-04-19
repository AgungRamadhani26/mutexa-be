package com.example.mutexa_be.service;

import com.example.mutexa_be.entity.enums.DocumentType;
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

/**
 * Service untuk menangani penyimpanan file fisik dan deteksi tipe dokumen.
 *
 * Prinsip SOLID yang diterapkan:
 * - SRP: Class ini HANYA bertugas mengelola file I/O di disk.
 * Tidak tahu apapun tentang parsing, database, atau business logic.
 *
 * Cara kerja:
 * 1. saveFile() → Menyimpan MultipartFile ke folder uploads/ dengan nama unik.
 * 2. detectType() → Menganalisis file untuk menentukan apakah PDF digital atau
 * scan/gambar.
 * - Jika content-type = image/* → IMAGE_SCAN
 * - Jika .pdf dan ada teks → PDF_DIGITAL
 * - Jika .pdf tapi kosong → IMAGE_SCAN (kemungkinan hasil scan)
 */
@Slf4j
@Service
public class FileStorageService {

   // Lokasi folder sementara tempat menyimpan file user di disk
   private static final String UPLOAD_DIR = "uploads/";

   /**
    * Menyimpan file yang diupload pengguna ke folder uploads/ dengan nama unik.
    *
    * @param file MultipartFile dari request upload
    * @return Path absolut file yang tersimpan di disk
    * @throws IOException jika gagal menyimpan file
    */
   public Path saveFile(MultipartFile file) throws IOException {
      Path uploadPath = Paths.get(UPLOAD_DIR);
      if (!Files.exists(uploadPath)) {
         Files.createDirectories(uploadPath);
      }

      // Tambahkan timestamp agar nama file unik dan tidak bentrok
      String uniqueFileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
      Path filePath = uploadPath.resolve(uniqueFileName);
      Files.copy(file.getInputStream(), filePath);

      log.info("File berhasil disimpan ke: {}", filePath);
      return filePath;
   }

   /**
    * Mendeteksi tipe dokumen berdasarkan konten file.
    *
    * Logika deteksi:
    * - File gambar (jpg, png, dll) → IMAGE_SCAN
    * - File PDF yang mengandung teks → PDF_DIGITAL (mutasi bank digital)
    * - File PDF tanpa teks → IMAGE_SCAN (kemungkinan hasil scan/foto)
    *
    * @param savedFile    File yang sudah tersimpan di disk
    * @param contentType  MIME type dari file (contoh: "application/pdf",
    *                     "image/png")
    * @param originalName Nama asli file sebelum disimpan
    * @return Tipe dokumen yang terdeteksi
    */
   public DocumentType detectType(File savedFile, String contentType, String originalName) {
      // Jika file gambar langsung → pasti IMAGE_SCAN
      if (contentType != null && (contentType.startsWith("image/")
            || originalName.toLowerCase().endsWith(".jpg")
            || originalName.toLowerCase().endsWith(".png"))) {
         return DocumentType.IMAGE_SCAN;
      }

      // Jika file PDF → cek apakah ada teks di halaman pertama
      if (originalName != null && originalName.toLowerCase().endsWith(".pdf")) {
         try (PDDocument document = Loader.loadPDF(savedFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String text = stripper.getText(document);

            if (text == null || text.trim().isEmpty()) {
               // PDF tanpa teks → kemungkinan hasil scan
               return DocumentType.IMAGE_SCAN;
            } else {
               return DocumentType.PDF_DIGITAL;
            }
         } catch (Exception e) {
            log.warn("Tidak dapat mengekstrak teks awal PDF, dianggap sebagai IMAGE_SCAN", e);
            return DocumentType.IMAGE_SCAN;
         }
      }

      // Default fallback untuk tipe file lainnya
      return DocumentType.IMAGE_SCAN;
   }
}
