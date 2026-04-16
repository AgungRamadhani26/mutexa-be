package com.example.mutexa_be;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Dumps character-level X,Y positions from Mandiri PDFs
 * to understand column layout for building a position-based parser.
 */
public class MandiriCharPositionDump {

    @Test
    void dumpCharPositions() throws Exception {
        String[] files = {
            "C:/Users/agung/Documents/PDP_BCA_Finance/MutexaApp/Novalino/Data_Mutasi_PDF/BilgusMistison_Mandiri_JanuariFebruariMaret_2025.pdf",
            "C:/Users/agung/Downloads/MANDIRI - FRANZ HERMANN.pdf"
        };
        String[] labels = { "BILGUS", "FRANZ" };

        for (int f = 0; f < files.length; f++) {
            File file = new File(files[f]);
            if (!file.exists()) continue;

            String outFile = "char_positions_" + labels[f].toLowerCase() + ".txt";
            try (PDDocument doc = Loader.loadPDF(file);
                 PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {

                // Only dump pages 1 and 2
                for (int pageNum = 1; pageNum <= Math.min(2, doc.getNumberOfPages()); pageNum++) {
                    pw.printf("=== %s PAGE %d ===%n", labels[f], pageNum);

                    PositionDumper dumper = new PositionDumper(pw);
                    dumper.setStartPage(pageNum);
                    dumper.setEndPage(pageNum);
                    dumper.setSortByPosition(true);
                    dumper.getText(doc);

                    pw.println();
                }

                System.out.printf("Written: %s%n", outFile);
            }
        }
    }

    /**
     * Groups characters by Y position (row) and shows their X positions.
     * This reveals the column structure of the PDF.
     */
    static class PositionDumper extends PDFTextStripper {
        private final PrintWriter pw;
        // Group characters by row (Y position rounded to nearest integer)
        private final TreeMap<Float, List<CharInfo>> rows = new TreeMap<>();

        PositionDumper(PrintWriter pw) throws IOException {
            this.pw = pw;
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            float y = Math.round(text.getYDirAdj() * 10f) / 10f; // round to 1 decimal
            float x = Math.round(text.getXDirAdj() * 10f) / 10f;
            if (Math.abs(y - 350.0f) < 0.2f && (text.getUnicode().equals("B") || text.getUnicode().equals("i"))) {
                System.out.printf("DEBUG -> Char: '%s', X: %.2f, W: %.2f, End: %.2f, SpaceW: %.2f%n",
                        text.getUnicode(), text.getXDirAdj(), text.getWidthDirAdj(), text.getXDirAdj() + text.getWidthDirAdj(), text.getWidthOfSpace());
            }

            rows.computeIfAbsent(y, k -> new ArrayList<>())
                .add(new CharInfo(x, text.getUnicode(), text.getFontSize()));
        }

        @Override
        public String getText(PDDocument doc) throws IOException {
            rows.clear();
            String result = super.getText(doc);

            // Now print grouped by Y (row)
            pw.printf("%-8s %-8s %-60s  [RAW CHARS with X positions]%n", "Y", "X-range", "RECONSTRUCTED TEXT");
            pw.println("-".repeat(150));

            for (Map.Entry<Float, List<CharInfo>> entry : rows.entrySet()) {
                float y = entry.getKey();
                List<CharInfo> chars = entry.getValue();
                chars.sort(Comparator.comparingDouble(c -> c.x));

                // Reconstruct text from chars
                StringBuilder text2 = new StringBuilder();
                float prevX = -999;
                for (CharInfo c : chars) {
                    // Add space if gap > 3 units between characters
                    if (prevX > 0 && (c.x - prevX) > 5) {
                        text2.append(" | ");
                    }
                    text2.append(c.ch);
                    prevX = c.x;
                }

                float minX = chars.get(0).x;
                float maxX = chars.get(chars.size() - 1).x;

                // Print brief version
                pw.printf("Y=%-6.1f X=[%5.1f-%5.1f]  %s%n", y, minX, maxX, text2.toString());

                // Also print detailed X positions for first few interesting rows
                if (text2.toString().contains("Tanggal") || text2.toString().contains("Keterangan") 
                    || text2.toString().contains("Nominal") || text2.toString().contains("Saldo")
                    || text2.toString().contains("iaya")) {
                    pw.print("         DETAIL: ");
                    for (CharInfo c : chars) {
                        pw.printf("[%s@%.1f] ", c.ch, c.x);
                    }
                    pw.println();
                }
            }

            return result;
        }

        static class CharInfo {
            float x;
            String ch;
            float fontSize;
            CharInfo(float x, String ch, float fontSize) {
                this.x = x;
                this.ch = ch;
                this.fontSize = fontSize;
            }
        }
    }
}
