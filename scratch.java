import java.io.File;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class scratch {
   public static void main(String[] args) throws Exception {
       File file = new File("C:/Users/agung/Documents/PDP_BCA_Finance/MutexaApp/Novalino/DataNoval/BNI.pdf");
       if(!file.exists()) { System.out.println("No file"); return; }
       try (PDDocument doc = Loader.loadPDF(file, "dev1")) {
           PDFTextStripper stripper = new PDFTextStripper();
           stripper.setStartPage(1);
           stripper.setEndPage(2);
           String text = stripper.getText(doc);
           System.out.println(text.substring(0, Math.min(text.length(), 5000)));
       }
   }
}
