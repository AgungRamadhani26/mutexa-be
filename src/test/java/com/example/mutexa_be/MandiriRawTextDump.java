package com.example.mutexa_be;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * Utility test to dump raw PDFBox text output for analysis.
 */
public class MandiriRawTextDump {

    @Test
    void dumpRawText() throws Exception {
        File pdfFile = new File("C:/Users/agung/Documents/PDP_BCA_Finance/MutexaApp/Novalino/Data_Mutasi_PDF/BilgusMistison_Mandiri_JanuariFebruariMaret_2025.pdf");
        
        try (PDDocument doc = Loader.loadPDF(pdfFile);
             PrintWriter out = new PrintWriter(new FileWriter("raw_text_mandiri_bilgus.txt"))) {
            
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            
            int totalPages = doc.getNumberOfPages();
            out.println("=== TOTAL PAGES: " + totalPages + " ===");
            out.println();
            
            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(doc);
                
                out.println("=== PAGE " + page + " ===");
                String[] lines = text.split("\\r?\\n");
                for (int i = 0; i < lines.length; i++) {
                    out.printf("L%03d: [%s]%n", i + 1, lines[i]);
                }
                out.println("=== END PAGE " + page + " ===");
                out.println();
            }
        }
        
        System.out.println("Raw text dumped to raw_text_mandiri_bilgus.txt");
    }
}
