package com.example.mutexa_be.service.extractor;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service khusus untuk mngekstrak Counterparty Name (Nama Pengirim / Penerima)
 * dari deskripsi mutasi rekening Bank BNI.
 * <p>
 * Berbeda dengan bank lain, ciri khas BNI adalah pemisahan segmen informasinya
 * seringkali menggunakan karakter pipe ("|").
 * </p>
 */
@Service
public class BniCounterpartyExtractor extends AbstractCounterpartyExtractor {

    @Override
    public String getBankName() {
        return "BNI";
    }

    /**
     * BNI menggunakan spasi ganda ("  ") sebagai pemisah antara Nama dan Catatan/Remark.
     * Kita override agar spasi ganda tidak hilang saat normalisasi awal.
     */
    @Override
    protected String normalizeText(String raw) {
        if (raw == null) return "";
        return raw.toUpperCase()
                .replaceAll("[\\r\\n]+", " ")
                .trim();
    }

    @Override
    public String extract(String rawDescription, boolean isCredit) {
        if (rawDescription == null || rawDescription.trim().isEmpty()) return null;

        // Mendapatkan teks mentah berspasi ganda
        String text = normalizeText(rawDescription);

        // ==============================================================================
        // 1. PENYESUAIAN JENIS BIAYA (FEES / PAJAK / BUNGA) - Pre-emptive check
        // ==============================================================================
        if (text.contains("BY TRX BIFAST") || text.contains("BIAYA BIFAST")) return "BIAYA BIFAST";
        if (text.contains("JASA GIRO") || text.contains("BUNGA")) return "BUNGA BANK";
        if (text.contains("BIAYA ADM REK") || text.contains("BIAYA ADMINISTRASI") || text.contains("BIAYA ADM")) return "BIAYA ADMINISTRASI";
        if (text.contains("PPH") || text.contains("PAJAK")) return "PAJAK PPH";

        // ==============================================================================
        // 2. SETOR TUNAI
        // ==============================================================================
        if (text.contains("SETOR TUNAI")) {
            Matcher mSetor = Pattern.compile("SETOR TUNAI\\s*\\|\\s*(.+)").matcher(text);
            if (mSetor.find()) return smartClean(mSetor.group(1));
        }

        // ==============================================================================
        // 3. MULTI-SEGMENT PIPE LOGIC (ECHANNEL & TRANSFER)
        // ==============================================================================
        if (text.contains("|")) {
            String[] segments = text.split("\\|");
            for (int i = 0; i < segments.length; i++) segments[i] = segments[i].trim();

            // Kasus ECHANNEL (Biasanya 4 segmen)
            if (text.contains("ECHANNEL") && segments.length >= 4) {
                String target = segments[3];
                
                // Hilangkan No Rekening di awal target segmen jika ada
                Matcher mAccInSeg1 = Pattern.compile("\\d{5,}+").matcher(segments[1]);
                if (mAccInSeg1.find()) {
                    String accNo = mAccInSeg1.group();
                    String cleanAccNo = accNo.replaceAll("^0+", "");
                    String cleanTarget = target.replaceAll("^0+", "");

                    if (cleanTarget.startsWith(cleanAccNo)) {
                        target = target.replaceAll("^\\d+\\s*", "").trim();
                    }
                }
                
                return smartClean(target);
            }

            // Kasus TRANSFER / PEMINDAHAN KE (Minimal 2-3 segmen)
            for (String seg : segments) {
                if (seg.contains("PEMINDAHAN KE")) {
                    Matcher m = Pattern.compile("PEMINDAHAN KE\\s+\\d+\\s+(?:Bpk|Ibu|Sdr)?\\s*(.+)", Pattern.CASE_INSENSITIVE).matcher(seg);
                    if (m.find()) return smartClean(m.group(1));
                }
                if (seg.contains("PEMINDAHAN DARI")) {
                    Matcher m = Pattern.compile("PEMINDAHAN DARI\\s+\\d+\\s*(.*)", Pattern.CASE_INSENSITIVE).matcher(seg);
                    if (m.find() && !m.group(1).trim().isEmpty()) return smartClean(m.group(1));
                }
            }
        }

        // ==============================================================================
        // 4. FALLBACK REGEX UNTUK FORMAT LAIN
        // ==============================================================================
        Matcher mTransferBpk = Pattern.compile("PEMINDAHAN KE\\s+\\d+\\s+(?:Bpk|Ibu|Sdr)?\\s*(.+?)(?:\\s*\\||$)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (mTransferBpk.find()) {
            return smartClean(mTransferBpk.group(1));
        }

        // Fallback pendelegasian akhir jika tidak ada pola yang cocok
        return fallback(text);
    }

    /**
     * Helper untuk membersihkan Nama dari Catatan (Remark) menggunakan deteksi
     * spasi ganda dan daftar kata kunci sampah.
     */
    private String smartClean(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "UNKNOWN";

        // 1. Deteksi Spasi Lebar (Boundary between Name and Note)
        // Kita gunakan 3 spasi atau lebih sebagai pemisah aman agar tidak memotong nama 
        // yang mungkin memiliki typo spasi ganda.
        if (raw.contains("   ")) {
            raw = raw.split("\\s{3,}")[0].trim();
        }

        // 2. Bersihkan pola tanggal (01 AGUST 2025, 02/08/2025, dsb)
        raw = raw.replaceAll("\\s+\\d{2}[/ ](?:JAN|FEB|MAR|APR|MEI|JUN|JUL|AGUS|AGUST|SEP|OKT|NOV|DES|\\d{2})[/ ]?\\d{0,4}.*$", "");
        raw = raw.replaceAll("\\s+\\d{2}/\\d{2}/?\\d{0,4}.*$", "");

        // 3. Bersihkan Suffix Sederhana (Jika hanya dipisahkan 1-2 spasi tapi ada di daftar kata sampah)
        String[] noteSuffixes = {
            "BON KANTOR", "LAINNYA", "REMARK", "NOTE", "INV", "PAYMENT", 
            "FORKLIP", "CCTV", "PULSA", "TOKEN", "PAY", "TRF TO", "TRF FROM"
        };
        
        String cleaned = raw.trim();
        for (String suffix : noteSuffixes) {
            // Kita gunakan boundary \\b agar tidak memotong nama yang mengandung kata tersebut di tengah
            String pattern = "\\s+" + suffix + "(?:\\s+.*|$)";
            cleaned = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(cleaned).replaceAll("").trim();
        }

        // Terakhir kembalikan dengan pembersihan standar (PT/CV removal)
        return truncate(cleaned);
    }
}
