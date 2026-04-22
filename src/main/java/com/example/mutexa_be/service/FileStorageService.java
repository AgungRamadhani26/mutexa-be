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
     * Hanya mendukung PDF asli (Digital) yang mengandung teks.
     *
     * @param savedFile    File yang sudah tersimpan di disk
     * @param contentType  MIME type dari file
     * @param originalName Nama asli file
     * @return PDF_DIGITAL jika valid, atau null jika tidak didukung
     */
    public DocumentType detectType(File savedFile, String contentType, String originalName) {
        // Jika bukan PDF, langsung tidak didukung
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            return null;
        }

        // Cek konten PDF: Harus memiliki teks (bukan hasil scan)
        try (PDDocument document = Loader.loadPDF(savedFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String text = stripper.getText(document);

            if (text != null && !text.trim().isEmpty()) {
                return DocumentType.PDF_DIGITAL;
            }
        } catch (Exception e) {
            log.warn("Gagal menganalisis konten PDF: {}", e.getMessage());
        }

        return null; // Tidak mengandung teks atau bukan PDF
    }
}
