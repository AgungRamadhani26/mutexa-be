package com.example.mutexa_be;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class MandiriRawTextDumpFranz {

    @Test
    void dumpRawText() throws Exception {
        String pdfPath = "C:/Users/agung/Downloads/MANDIRI - FRANZ HERMANN.pdf";
        String outputPath = "raw_text_franz.txt";

        File file = new File(pdfPath);
        try (PDDocument doc = Loader.loadPDF(file);
             PrintWriter pw = new PrintWriter(new FileWriter(outputPath))) {

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            // Dump per halaman
            int totalPages = doc.getNumberOfPages();
            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(doc);
                String[] lines = text.split("\\r?\\n");

                pw.printf("=== PAGE %d ===%n", page);
                for (int i = 0; i < lines.length; i++) {
                    pw.printf("L%03d: [%s]%n", i + 1, lines[i]);
                }
                pw.println();
            }

            // Dump teks utuh (seperti yang parser gunakan)
            pw.println("=== FULL TEXT (ENTIRE DOCUMENT) ===");
            stripper.setStartPage(1);
            stripper.setEndPage(totalPages);
            String entireText = stripper.getText(doc);
            String[] allLines = entireText.split("\\r?\\n");
            for (int i = 0; i < allLines.length; i++) {
                pw.printf("F%04d: [%s]%n", i + 1, allLines[i]);
            }

            System.out.println("Raw text dumped to: " + new File(outputPath).getAbsolutePath());
            System.out.println("Total pages: " + totalPages);
            System.out.println("Total lines (full): " + allLines.length);
        }
    }
}
