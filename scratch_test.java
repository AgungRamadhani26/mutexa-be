import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.Loader;
import java.io.File;

public class scratch_test {
   public static void main(String[] args) throws Exception {
       File file = new File("C:/Users/agung/Downloads/MANDIRI - FRANZ HERMANN.pdf");
       try (PDDocument doc = Loader.loadPDF(file)) {
           new PDFTextStripper() {
               @Override protected void processTextPosition(TextPosition tp) {
                   String txt = tp.getUnicode();
                   float y = Math.round(tp.getYDirAdj() * 10f) / 10f;
                   if (y == 350.0f && (txt.equals("B") || txt.equals("i") && Math.abs(tp.getXDirAdj() - 129.2) < 2.0)) {
                       System.out.printf("Char: '%s', X: %.2f, W: %.2f, End: %.2f, SpaceW: %.2f%n",
                               txt, tp.getXDirAdj(), tp.getWidthDirAdj(), tp.getXDirAdj() + tp.getWidthDirAdj(), tp.getWidthOfSpace());
                   }
               }
           }.getText(doc);
       }
   }
}
