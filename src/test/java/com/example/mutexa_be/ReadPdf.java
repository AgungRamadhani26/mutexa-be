package com.example.mutexa_be;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;

public class ReadPdf {
    public static void main(String[] args) throws Exception {
        File file = new File("C:\\Users\\agung\\Documents\\PDP_BCA_Finance\\MutexaApp\\Novalino\\BNI ABADI SEJAHTERA PT.pdf");
        PDDocument document = Loader.loadPDF(file);
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(document);
        document.close();
        
        System.out.println("========== PDF CONTENT START ==========");
        String[] lines = text.split("\\r?\\n");
        int count = 0;
        for (String line : lines) {
            System.out.println(line);
            count++;
            if (count > 200) break; // limit output
        }
        System.out.println("========== PDF CONTENT END ==========");
    }
}
