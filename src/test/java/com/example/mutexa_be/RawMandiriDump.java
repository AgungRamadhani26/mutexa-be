package com.example.mutexa_be;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;

public class RawMandiriDump {
    
    @Test
    public void dump() {
        File file = new File("C:\\Users\\agung\\Documents\\PDP_BCA_Finance\\MutexaApp\\Novalino\\Data_Mutasi_PDF\\BilgusMistison_Mandiri_JanuariFebruariMaret_2025.pdf");
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            System.out.println("--- START DUMP ---");
            System.out.println(text);
            System.out.println("--- END DUMP ---");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
