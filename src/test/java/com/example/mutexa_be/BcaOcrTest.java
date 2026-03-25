package com.example.mutexa_be;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class BcaOcrTest {
   @Test
   public void testOcr() throws Exception {
      String filePath = "C:\\Users\\agung\\Documents\\PDP_BCA_Finance\\MutexaApp\\Novalino\\bca november rosadi.pdf";
      File file = new File(filePath);
      if (!file.exists()) {
         System.out.println("FILE NOT FOUND: " + filePath);
         return;
      }

      ITesseract tesseract = new Tesseract();
      tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
      tesseract.setLanguage("eng");
      tesseract.setTessVariable("preserve_interword_spaces", "1");
      tesseract.setPageSegMode(6);

      try (PDDocument pdDocument = Loader.loadPDF(file)) {
         PDFRenderer renderer = new PDFRenderer(pdDocument);
         // Coba 3 halaman pertama saja untuk analisa
         for (int page = 0; page < Math.min(3, pdDocument.getNumberOfPages()); page++) {
            BufferedImage originalImage = renderer.renderImageWithDPI(page, 300);
            BufferedImage bWImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(),
                  BufferedImage.TYPE_BYTE_BINARY);
            Graphics2D graphics = bWImage.createGraphics();
            graphics.drawImage(originalImage, 0, 0, Color.WHITE, null);
            graphics.dispose();

            String ocrResult = tesseract.doOCR(bWImage);
            Files.writeString(
                  Paths.get("C:\\Users\\agung\\Documents\\PDP_BCA_Finance\\MutexaApp\\mutexa-be\\ocr_debug_page_" + page
                        + ".txt"),
                  ocrResult);
            System.out.println("PAGE " + page + " DUMPED");
         }
      }
   }
}