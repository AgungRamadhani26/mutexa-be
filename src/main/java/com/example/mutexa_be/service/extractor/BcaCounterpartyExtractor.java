package com.example.mutexa_be.service.extractor;

import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BcaCounterpartyExtractor extends AbstractCounterpartyExtractor {

   @Override
   public String getBankName() {
      return "BCA";
   }

    @Override
    public String extract(String rawDescription, boolean isCredit) {
        if (rawDescription == null || rawDescription.trim().isEmpty()) return null;
        String text = normalizeText(rawDescription);

        // 0. BIAYA TXN (Khusus BI-FAST)
        if (text.contains("BIAYA TXN")) return "BIAYA ADMINISTRASI";

        // 1. BI-FAST (Urutan kita: BI-FAST [CR/DB] TANGGAL :[DD/MM] BIF TRANSFER [DR/KE] [BANK_CODE] [NAME])
        Matcher mBiFast = Pattern.compile("BI-FAST (?:CR|DB).*?BIF TRANSFER (?:DR|KE) \\d{3} (.*)$").matcher(text);
        if (mBiFast.find()) return cleanFinalName(mBiFast.group(1).trim());

        // 2. TRSF E-BANKING (Pola: TRSF E-BANKING [CR/DB] [REF] [NOMINAL] [NAME])
        Matcher mTrsfE = Pattern.compile("TRSF E-BANKING (?:DB|CR).*?\\d{4,} (.*)$").matcher(text);
        if (mTrsfE.find()) {
            return cleanFinalName(mTrsfE.group(1).trim());
        }

        // 3. CENAIDJA
        Matcher mCena = Pattern.compile("CENAIDJA/([A-Z][A-Z0-9 ]+?)(?:\\s+PT)?\\s+\\d").matcher(text);
        if (mCena.find()) return truncate(mCena.group(1).trim());

        // 4. SETORAN
        Matcher mSetor = Pattern.compile("SETOR(?:AN)?\\s+(?:TUNAI|SALES)?\\s*([A-Z][A-Z ]{2,}?)\\s+\\d{2}-\\d").matcher(text);
        if (mSetor.find()) return truncate(mSetor.group(1).trim());

        // 5. Pola Umum DARI/KEPADA/KE
        Matcher mDari = Pattern.compile("(?:DARI|KEPADA|KE)\\s+([A-Z][A-Z0-9 ]{2,}?)(?:\\s+TRANSFER|\\s+\\d{10,}|$)").matcher(text);
        if (mDari.find()) {
            String name = mDari.group(1).trim();
            if (name.length() > 3) return cleanFinalName(name);
        }

        // 6. Badan Usaha (PT/CV)
        Matcher mCorp = Pattern.compile("((?:PT|CV)\\.?)\\s+([A-Z][A-Z0-9 ]{2,}?)(?:\\s+\\d|$)").matcher(text);
        if (mCorp.find()) return truncate(mCorp.group(0).replaceAll("\\s+\\d.*", "").trim());

        // 7. Internal / Fixed
        if (text.equals("INTEREST CREDIT")) return "INTEREST CREDIT";
        if (text.contains("OD INT CHARGE")) return "OD INT CHARGE";
        if (text.startsWith("BIAYA ADM") || text.contains("BIAYA ADMINISTRASI")) return "BIAYA ADMINISTRASI";
        if (text.contains("PAJAK BUNGA")) return "PAJAK BUNGA";
        if (text.contains("BUNGA KREDIT LOKAL")) return "BUNGA KREDIT LOKAL";

        return fallback(text);
    }

    private String cleanFinalName(String name) {
        if (name == null) return null;
        
        // 1. Bersihkan TANGGAL :DD/MM di awal nama (residue akibat interleaved layout PDF)
        name = name.replaceAll("^TANGGAL\\s*:\\s*\\d{2}/\\d{2}\\s*", "");
        
        // 2. Bersihkan Angka Nominal / Kode Referensi di awal nama 
        name = name.replaceAll("^\\d{4,}/", ""); 
        name = name.replaceAll("^[\\d,.]+[.,]\\d{2}\\s*", "");
        
        // 3. Bersihkan Noise Simbol dan ID Panjang di akhir nama
        // Misal: "ADIRA FINANC - - 011724212284" -> "ADIRA FINANC"
        name = name.replaceAll("\\s+[-/ ]+\\s+\\d{5,}.*$", ""); // Simbol + ID
        name = name.replaceAll("\\s+\\d{8,}.*$", "");           // ID Langsung
        name = name.replaceAll("[-/\\s]{2,}$", "");            // Sisa simbol menggantung
        
        // 4. Bersihkan Karakter Noise di awal/akhir
        name = name.replaceAll("^[^A-Z0-9]+", "").replaceAll("[^A-Z0-9]+$", "");

        return truncate(name.trim());
    }
}
