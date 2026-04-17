package com.example.mutexa_be;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.regex.*;

public class BniScratchDebug2 {
    @Test
    void debugParser() throws Exception {
        File file = new File("C:/Users/agung/Documents/PDP_BCA_Finance/MutexaApp/Novalino/DataNoval/BNI.pdf");
        try (PDDocument doc = Loader.loadPDF(file, "dev1")) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String[] lines = stripper.getText(doc).split("\\r?\\n");
            
            Pattern dateRegex = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$");
            Pattern closingRegex = Pattern.compile("^(.*?\\s+)?(?<balance>\\d{1,3}(?:,\\d{3})*\\.\\d{2})(?<no>\\d+)\\s+(?<type>[DC])(?<amount>\\d{1,3}(?:,\\d{3})*\\.\\d{2})$");
            boolean inTx = false;
            for(int i=0; i<Math.min(lines.length, 50); i++) {
                String line = lines[i].trim();
                if(dateRegex.matcher(line).matches()) {
                    System.out.println("DATE MATCH: [" + line + "]");
                    inTx = true;
                } else if (inTx && closingRegex.matcher(line).matches()) {
                    System.out.println("CLOSING MATCH: [" + line + "]");
                    inTx = false;
                } else if (inTx) {
                    System.out.println("DESC: [" + line + "]");
                } else {
                    System.out.println("IGNORE: [" + line + "]");
                }
            }
        }
    }
}
