package com.example.mutexa_be;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

public class PdfTextExtractionTest {

   @Test
   public void inspectBriPdf() {
      String filepath = "C:\\Users\\agung\\Documents\\PDP_BCA_Finance\\MutexaApp\\Novalino\\Mutasi Rek BRI Lisdri.pdf";
      extractText(filepath);
   }

   @Test
   public void inspectBcaPdf() {
      String filepath = "C:\\Users\\agung\\Documents\\PDP_BCA_Finance\\MutexaApp\\Novalino\\bca november rosadi.pdf";
      extractText(filepath);
   }

   private void extractText(String filepath) {
      System.out.println("\n========== MEMBACA FILE ==========");
      System.out.println("Lokasi: " + filepath);
      File file = new File(filepath);

      if (!file.exists()) {
         System.err.println("❌ ERROR: File tidak ditemukan di path tersebut!");
         return;
      }

      try (PDDocument document = Loader.loadPDF(file)) {
         PDFTextStripper stripper = new PDFTextStripper();

         // Mengurutkan posisi teks dari kiri ke kanan, atas ke bawah
         // Ini penting untuk mempertahankan bentuk kolom (tanggal, deskripsi, nominal)
         stripper.setSortByPosition(true);

         // Cukup baca halaman 1 (atau halaman pertama dan kedua) sebagai sampel
         stripper.setStartPage(1);
         stripper.setEndPage(1);

         String text = stripper.getText(document);

         if (text == null || text.trim().isEmpty()) {
            System.out.println(
                  "⚠️ HASIL: PDF ini tidak memiliki layer teks. Kemungkinan ini adalah hasil *SCAN IMAGE* dan butuh proses OCR (Ollama/Tesseract).");
         } else {
            System.out.println("✅ HASIL: Teks berhasil diekstrak! (Digital PDF)");
            System.out.println("\n--- MENTAHAN TEKS HALAMAN 1 ---");
            // Batasi print sampai 1500 huruf saja untuk analisa
            System.out.println(text.substring(0, Math.min(text.length(), 1500)));
            System.out.println("\n-------------------------------");
         }

      } catch (IOException e) {
         System.err.println("❌ ERROR: Gagal membaca dokumen PDF. Alasan: " + e.getMessage());
      }
   }
}
