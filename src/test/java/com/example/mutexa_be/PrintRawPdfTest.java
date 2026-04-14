
package com.example.mutexa_be;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.io.File;
public class PrintRawPdfTest {
    @Test
    public void printPdf() throws Exception {
        File file = new File("C:/Users/agung/Documents/PDP_BCA_Finance/MutexaApp/Novalino/Data_Mutasi_PDF/BilgusMistison_Mandiri_JanuariFebruariMaret_2025.pdf");
        PDDocument doc = Loader.loadPDF(file);
        PDFTextStripper stripper = new PDFTextStripper(); stripper.setSortByPosition(false);
        String text = stripper.getText(doc);
        System.out.println("--- START PDF TEXT ---");
        System.out.println(text.substring(0, Math.min(text.length(), 2000)));
        System.out.println("--- END PDF TEXT ---");
        doc.close();
    }
}
