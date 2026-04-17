package com.example.mutexa_be;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import java.io.File;
import java.io.IOException;
import java.util.List;
public class CoordDumper extends PDFTextStripper {
    public CoordDumper() throws IOException { super(); }
    @Override protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
        if (!textPositions.isEmpty()){
            System.out.printf("X: %7.2f | Y: %7.2f | Text: %s%n", textPositions.get(0).getXDirAdj(), textPositions.get(0).getYDirAdj(), string);
        }
    }
    public static void main(String[] args) throws Exception {
        PDDocument doc = Loader.loadPDF(new File("C:\\Users\\agung\\Documents\\PDP_BCA_Finance\\MutexaApp\\Novalino\\DataNoval\\BNI.pdf"), "dev1");
        CoordDumper cd = new CoordDumper(); cd.setStartPage(1); cd.setEndPage(1); cd.getText(doc); doc.close();
    }
}
