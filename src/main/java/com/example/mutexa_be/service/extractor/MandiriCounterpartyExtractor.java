
package com.example.mutexa_be.service.extractor;

import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MandiriCounterpartyExtractor extends AbstractCounterpartyExtractor {

    @Override
    public String getBankName() {
        return "MANDIRI";
    }

    @Override
    public String extract(String rawDescription, boolean isCredit) {
        if (rawDescription == null || rawDescription.trim().isEmpty()) return null;

        String text = normalizeText(rawDescription);
        
        // 0. Clean Time Residues (HH:mm:ss WIB)
        text = text.replaceAll("\\d{2}:\\d{2}:\\d{2}\\s*(?:WIB)?", "").trim();

        // 1. Transfer BI Fast (Pola: Transfer BI Fast Dari/Ke [BANK] [NAME] [ID])
        Matcher mBiFast = Pattern.compile("TRANSFER BI FAST (?:DARI|KE) (?:[A-Z]+ )?([A-Z0-9 ]+?)(?:\\s+\\d{7,}|$)").matcher(text);
        if (mBiFast.find()) return cleanMandiriName(mBiFast.group(1).trim());

        // 2. Transfer dari/ke Bank Lain (Pola: Transfer dari/ke Bank lain [BANK] [NAME] [ID])
        Matcher mOtherBank = Pattern.compile("TRANSFER (?:DARI|KE) BANK LAIN (?:[A-Z]+ )?([A-Z0-9 ]+?)(?:\\s+\\d{7,}|$)").matcher(text);
        if (mOtherBank.find()) return cleanMandiriName(mOtherBank.group(1).trim());

        // 3. Transfer antar Mandiri (DARI/KE [NAME])
        Matcher mMandiri = Pattern.compile("TRANSFER (?:DARI|KE) BANK MANDIRI ([A-Z0-9 ]+?)(?:\\s+\\d{7,}|$)").matcher(text);
        if (mMandiri.find()) return cleanMandiriName(mMandiri.group(1).trim());

        Matcher mAntarMandiri = Pattern.compile("TRANSFER ANTAR MANDIRI (?:DARI|KE) ([A-Z0-9 ]+?)(?:\\s+TRANSFER FEE|\\s+\\d|$)").matcher(text);
        if (mAntarMandiri.find()) return cleanMandiriName(mAntarMandiri.group(1).trim());

        // 4. Pola Singkat "DARI [NAME]" atau "KE [NAME]" (Biasanya tertangkap jika pola di atas gagal)
        Matcher mShort = Pattern.compile("(?:DARI|KE) (?:BANK [A-Z]+ )?([A-Z0-9 ]{3,20})(?:\\s+\\d{7,}|$)").matcher(text);
        if (mShort.find()) return cleanMandiriName(mShort.group(1).trim());

        // 5. Pembayaran / Payment
        if (text.contains("PEMBAYARAN") || text.contains("PENSIUN") || text.contains("PAYMENT")) {
            // Pola: PEMBAYARAN [NAME] [NO_REK/NO_PEL] atau [NAME] PT/CV
            Matcher mPay = Pattern.compile("(?:PEMBAYARAN|PAYMENT)\\s+([A-Z0-9 ,.]+?)(?:\\s+(?:[\\d-]{5,}|PT|CV)|$)").matcher(text);
            if (mPay.find()) {
                String potentialName = mPay.group(1).trim();
                if (potentialName.length() > 2) return cleanMandiriName(potentialName);
            }
            
            if (text.contains("KARTU KREDIT")) return "KARTU KREDIT";
            if (text.contains("BFI FINANCE")) return "BFI FINANCE";
        }

        // 6. Common Bank Terms (TAX, INTEREST, FEES)
        if (text.contains("BIAYA TRANSFER")) return "BIAYA TRANSFER";
        if (text.contains("BIAYA ADM")) {
            if (text.contains("REKENING")) return "BIAYA ADMINISTRASI REKENING";
            if (text.contains("KARTU")) return "BIAYA ADMINISTRASI KARTU";
            return "BIAYA ADMINISTRASI";
        }
        if (text.contains("PAJAK REKENING") || text.contains("TAX")) return "TAX";
        if (text.contains("BUNGA REKENING") || text.contains("INTEREST")) return "INTEREST";
        if (text.contains("BIAYA SALDO MINIMUM")) return "BIAYA SALDO MINIMUM";
        if (text.contains("BIAYA PENGGANTIAN KARTU")) return "BIAYA PENGGANTIAN KARTU";

        return fallback(text);
    }

    private String cleanMandiriName(String name) {
        if (name == null) return null;
        
        // Bersihkan sisa-sisa noise Mandiri
        name = name.toUpperCase();
        name = name.replaceAll("\\s+WIB$", "");
        name = name.replaceAll("^DARI\\s+", "");
        name = name.replaceAll("^KE\\s+", "");
        name = name.replaceAll("^BANK [A-Z]+\\s+", "");
        name = name.replaceAll("^(?:BCA|BRI|BNI|MANDIRI|UOB|CIMB|DANAMON|PERMATA)\\s+", "");
        
        // Remove months and excess info if it looks like a bank memo (e.g., "FEB BIAYA...")
        name = name.replaceAll("\\s+(?:JAN|FEB|MAR|APR|MEI|JUN|JUL|AGU|SEP|OKT|NOV|DES)\\s+.*$", "");
        name = name.replaceAll("\\s+(?:JANUARI|FEBRUARI|MARET|APRIL|MEI|JUNI|JULI|AGUSTUS|SEPTEMBER|OKTOBER|NOVEMBER|DESEMBER)\\s+.*$", "");
        
        return truncate(name.trim());
    }
}
