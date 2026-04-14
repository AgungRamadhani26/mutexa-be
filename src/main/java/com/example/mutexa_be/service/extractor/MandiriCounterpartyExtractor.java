
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

        // 1. Pembayaran / Payment (Prioritaskan untuk Debit karena biasanya ini info paling spesifik)
        if (text.contains("PEMBAYARAN") || text.contains("PENSIUN") || text.contains("PAYMENT")) {
            // A. Pola: PEMBAYARAN [NAME] [NO_REK/NO_PEL] atau [NAME] PT/CV
            // Kita prioritaskan nama biller (setelah kata kunci)
            Matcher mPay = Pattern.compile("(?:PEMBAYARAN|PAYMENT)\\s+(?:DI\\s+)?([A-Z0-9 ,./]+?)(?:\\s+(?:[\\d-]{5,}|PT|CV|TRANSFER|DARI|KE)|$)").matcher(text);
            if (mPay.find()) {
                String potentialName = mPay.group(1).trim();
                if (potentialName.length() > 2) return cleanMandiriName(potentialName);
            }

            // B. Coba pola NAME BEFORE (Mandiri often has: [USER_NAME] [ID] PEMBAYARAN) sebagai fallback
            Matcher mPayBefore = Pattern.compile("^(.+?)\\s+\\d{7,}\\s+(?:PEMBAYARAN|PAYMENT)").matcher(text);
            if (mPayBefore.find()) return cleanMandiriName(mPayBefore.group(1).trim());
        }

        // 2. Transfer BI Fast (Dukung variasi tanggal di tengah)
        Matcher mBiFast = Pattern.compile("TRANSFER BI FAST.*?(?:DARI|KE) (?:[A-Z]{3,}\\s+)?([A-Z0-9 ]+?)(?:\\s+\\d{7,}|$)").matcher(text);
        if (mBiFast.find()) return cleanMandiriName(mBiFast.group(1).trim());

        // 3. Transfer dari/ke Bank Lain
        Matcher mOtherBank = Pattern.compile("TRANSFER (?:DARI|KE) BANK LAIN.*?(?:[A-Z]{3,}\\s+)?([A-Z0-9 ]+?)(?:\\s+\\d{7,}|$)").matcher(text);
        if (mOtherBank.find()) return cleanMandiriName(mOtherBank.group(1).trim());

        // 4. Transfer antar Mandiri (DARI/KE [NAME])
        Matcher mMandiri = Pattern.compile("TRANSFER (?:DARI|KE) BANK MANDIRI ([A-Z0-9 ]+?)(?:\\s+\\d{7,}|$)").matcher(text);
        if (mMandiri.find()) return cleanMandiriName(mMandiri.group(1).trim());

        Matcher mAntarMandiri = Pattern.compile("TRANSFER ANTAR MANDIRI (?:DARI|KE) ([A-Z0-9 ]+?)(?:\\s+TRANSFER FEE|\\s+\\d|$)").matcher(text);
        if (mAntarMandiri.find()) return cleanMandiriName(mAntarMandiri.group(1).trim());

        // 5. Pola Singkat "DARI [NAME]" atau "KE [NAME]"
        Matcher mShort = Pattern.compile("(?:DARI|KE) (?:BANK [A-Z]+ )?([A-Z0-9 ]{3,20})(?:\\s+\\d{7,}|$)").matcher(text);
        if (mShort.find()) return cleanMandiriName(mShort.group(1).trim());

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
        
        name = name.toUpperCase();

        // 1. Bersihkan Angka / No Urut menggantung di awal (misal: "2 ANDRY", "10 BUDI")
        // Khusus jika diikuti spasi dan nama
        name = name.replaceAll("^\\d{1,3}\\s+", "");
        
        // 2. Bersihkan sisa-sisa noise Mandiri
        name = name.replaceAll("\\s+WIB$", "");
        name = name.replaceAll("^DARI\\s+", "");
        name = name.replaceAll("^KE\\s+", "");
        name = name.replaceAll("^BANK [A-Z]+\\s+", "");
        name = name.replaceAll("^(?:BCA|BRI|BNI|MANDIRI|UOB|CIMB|DANAMON|PERMATA)\\s+", "");
        
        // 3. Bersihkan kode transaksi alfanumerik (misal: TRF/123/ABC)
        name = name.replaceAll("(?:TRF|REF|FEE)[/\\s]*[A-Z0-9]+", " ");

        // 4. Bersihkan sisa disclaimer jika bocor ke CP
        name = name.replaceAll("(?i)BANK MANDIRI.*$", "").trim();
        name = name.replaceAll("(?i)ESTATEMENT.*$", "").trim();
        
        // 5. Remove months and excess info if it looks like a bank memo (e.g., "FEB BIAYA...")
        name = name.replaceAll("\\s+(?:JAN|FEB|MAR|APR|MEI|JUN|JUL|AGU|SEP|OKT|NOV|DES)\\s+.*$", "");
        name = name.replaceAll("\\s+(?:JANUARI|FEBRUARI|MARET|APRIL|MEI|JUNI|JULI|AGUSTUS|SEPTEMBER|OKTOBER|NOVEMBER|DESEMBER)\\s+.*$", "");
        
        // 6. Final clean: karakter non-alfanumerik di awal/akhir, spasi ganda
        name = name.replaceAll("^[^A-Z0-9]+", "").replaceAll("[^A-Z0-9]+$", "");
        return truncate(name.replaceAll("\\s{2,}", " ").trim());
    }
}
