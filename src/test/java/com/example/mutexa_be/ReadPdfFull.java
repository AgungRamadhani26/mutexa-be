package com.example.mutexa_be;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;

public class ReadPdfFull {
    public static void main(String[] args) throws Exception {
        File file = new File("C:\\Users\\agung\\Documents\\PDP_BCA_Finance\\MutexaApp\\Novalino\\BNI ABADI SEJAHTERA PT.pdf");
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            
            System.out.println("========== PDF ANALYZER START ==========");
            String[] lines = text.split("\\r?\\n");
            
            System.out.println("Searching for 'ECHANNEL' patterns:");
            for (String line : lines) {
                if (line.contains("|")) {
                    System.out.println(line.trim());
                }
            }
            System.out.println("========== PDF ANALYZER END ==========");
        }
    }
}
