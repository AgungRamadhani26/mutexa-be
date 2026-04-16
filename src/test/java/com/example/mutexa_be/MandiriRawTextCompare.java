package com.example.mutexa_be;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class MandiriRawTextCompare {

    private static final String NATSIR_PATH = "C:/Users/agung/Documents/PDP_BCA_Finance/MutexaApp/Novalino/DataNoval/Rekening Koran Mandiri Mohammad Natsir.pdf";
    private static final String NATSIR_PASS = "dev1";

    @Test
    void compareThreeFiles() throws Exception {
        String[] files = {
            "C:/Users/agung/Documents/PDP_BCA_Finance/MutexaApp/Novalino/Data_Mutasi_PDF/BilgusMistison_Mandiri_JanuariFebruariMaret_2025.pdf",
            "C:/Users/agung/Downloads/MANDIRI - FRANZ HERMANN.pdf",
            NATSIR_PATH
        };
        String[] labels = { "BILGUS (original)", "FRANZ (masked)", "NATSIR (original)" };
        String[] passwords = { null, null, NATSIR_PASS };
        String[] outputs = { "raw_bilgus_p2.txt", "raw_franz_p2.txt", "raw_natsir_p2.txt" };

        for (int f = 0; f < files.length; f++) {
            File file = new File(files[f]);
            if (!file.exists()) {
                System.out.println("FILE NOT FOUND: " + files[f]);
                continue;
            }

            PDDocument doc = passwords[f] != null
                ? Loader.loadPDF(file, passwords[f])
                : Loader.loadPDF(file);

            try (doc; PrintWriter pw = new PrintWriter(new FileWriter(outputs[f]))) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);

                int totalPages = doc.getNumberOfPages();
                System.out.printf("=== %s === (%d pages)%n", labels[f], totalPages);

                for (int page = 1; page <= Math.min(3, totalPages); page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String text = stripper.getText(doc);
                    String[] lines = text.split("\\r?\\n");

                    pw.printf("=== PAGE %d ===%n", page);
                    System.out.printf("  Page %d: %d lines%n", page, lines.length);
                    for (int i = 0; i < lines.length; i++) {
                        pw.printf("L%03d: [%s]%n", i + 1, lines[i]);
                    }
                    pw.println();
                }
            }
        }

        // Side-by-side: Franz vs Natsir page 2
        System.out.println();
        System.out.println("====== FRANZ (masked) vs NATSIR (original) - Page 2 ======");

        String franzP2 = readPage("C:/Users/agung/Downloads/MANDIRI - FRANZ HERMANN.pdf", 2, null);
        String natsirP2 = readPage(NATSIR_PATH, 2, NATSIR_PASS);

        String[] franzLines = franzP2.split("\\r?\\n");
        String[] natsirLines = natsirP2.split("\\r?\\n");

        int maxLines = Math.max(franzLines.length, natsirLines.length);
        for (int i = 0; i < maxLines; i++) {
            String fl = i < franzLines.length ? franzLines[i] : "(empty)";
            String nl = i < natsirLines.length ? natsirLines[i] : "(empty)";
            String marker = fl.equals(nl) ? "==" : "!!" ;
            System.out.printf("L%03d %s FRANZ: %-75s | NATSIR: %s%n", i+1, marker, fl, nl);
        }
    }

    private String readPage(String path, int page, String password) throws Exception {
        File file = new File(path);
        PDDocument doc = password != null
            ? Loader.loadPDF(file, password)
            : Loader.loadPDF(file);
        try (doc) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            return stripper.getText(doc);
        }
    }
}
