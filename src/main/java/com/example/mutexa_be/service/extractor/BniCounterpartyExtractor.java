package com.example.mutexa_be.service.extractor;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service khusus untuk mngekstrak Counterparty Name (Nama Pengirim / Penerima)
 * dari deskripsi mutasi rekening Bank BNI.
 */
@Service
public class BniCounterpartyExtractor extends AbstractCounterpartyExtractor {

    @Override
    public String getBankName() {
        return "BNI";
    }

    /**
     * BNI menggunakan spasi ganda (" ") sebagai pemisah antara Nama dan
     * Catatan/Remark.
     * Kita override agar spasi ganda tidak hilang saat normalisasi awal.
     */
    @Override
    protected String normalizeText(String raw) {
        if (raw == null)
            return "";
        return raw.toUpperCase()
                .replaceAll("[\\r\\n]+", " ")
                .trim();
    }

    @Override
    public String extract(String rawDescription, boolean isCredit) {
        if (rawDescription == null || rawDescription.trim().isEmpty())
            return null;

        String text = normalizeText(rawDescription);

        // ==============================================================================
        // 1. PENYESUAIAN JENIS BIAYA (BI-FAST / ADM / BUNGA / PAJAK)
        // ==============================================================================
        if (text.contains("BY TRX BIFAST") || text.contains("BIAYA BIFAST"))
            return "BIAYA BIFAST";
        if (text.contains("JASA GIRO") || text.contains("BUNGA"))
            return "BUNGA BANK";
        if (text.contains("BIAYA ADM REK") || text.contains("BIAYA ADMINISTRASI") || text.contains("BIAYA ADM"))
            return "BIAYA ADMINISTRASI";
        if (text.contains("PPH") || text.contains("PAJAK"))
            return "PAJAK PPH";

        // ==============================================================================
        // 2. SETOR TUNAI
        // ==============================================================================
        if (text.contains("SETOR TUNAI")) {
            Matcher mSetor = Pattern.compile("SETOR TUNAI\\s*\\|\\s*(.+)").matcher(text);
            if (mSetor.find())
                return smartClean(mSetor.group(1));
        }

        // ==============================================================================
        // 3. MULTI-SEGMENT PIPE LOGIC (ECHANNEL & TRANSFER)
        // ==============================================================================
        if (text.contains("|")) {
            String[] segments = text.split("\\|");
            for (int i = 0; i < segments.length; i++)
                segments[i] = segments[i].trim();

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

            // Kasus TRANSFER / PEMINDAHAN (Minimal 2-3 segmen)
            for (String seg : segments) {
                if (seg.contains("PEMINDAHAN KE")) {
                    Matcher m = Pattern
                            .compile("PEMINDAHAN KE\\s+\\d+\\s+(?:Bpk|Ibu|Sdr)?\\s*(.+)", Pattern.CASE_INSENSITIVE)
                            .matcher(seg);
                    if (m.find())
                        return smartClean(m.group(1));
                }
                if (seg.contains("PEMINDAHAN DARI")) {
                    Matcher m = Pattern.compile("PEMINDAHAN DARI\\s+\\d+\\s*(.*)", Pattern.CASE_INSENSITIVE)
                            .matcher(seg);
                    if (m.find() && !m.group(1).trim().isEmpty())
                        return smartClean(m.group(1));
                }
            }
        }

        // ==============================================================================
        // 4. FALLBACK REGEX UNTUK FORMAT LAIN
        // ==============================================================================
        Matcher mTransferBpk = Pattern
                .compile("PEMINDAHAN KE\\s+\\d+\\s+(?:Bpk|Ibu|Sdr)?\\s*(.+?)(?:\\s*\\||$)", Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (mTransferBpk.find()) {
            return smartClean(mTransferBpk.group(1));
        }

        return fallback(text);
    }

    /**
     * Logika "Jenius" untuk memisahkan Nama Counterparty dari Noise (Bank Name,
     * Branch Code, Remark).
     */
    private String smartClean(String raw) {
        if (raw == null || raw.trim().isEmpty())
            return "UNKNOWN";

        String cleaned = raw;

        // 1. Deteksi Spasi Lebar (7+ spaces) sebagai pemisah kolom bayangan
        if (cleaned.contains("       ")) {
            cleaned = cleaned.split("\\s{7,}")[0].trim();
        }
        // 2. Deteksi Spasi Sedang (3-6 spaces) sebagai pemisah informasi
        else if (cleaned.contains("   ")) {
            cleaned = cleaned.split("\\s{3,}")[0].trim();
        }

        // 3. Potong jika menemukan Kata Kunci Perbankan (Bank Keywords) di tengah
        // kalimat.
        // Pola: Nama [SPASI] KEYWORD [APAPUN]
        String bankPattern = "\\s+\\b(BNI|BCA|BRI|MANDIRI|UOB|BIFAST|TF|TRF|TRSF|DB|CR|NOTA|TRACK|BAN|PCI|TBS|BYR)\\b.*$";
        cleaned = Pattern.compile(bankPattern, Pattern.CASE_INSENSITIVE).matcher(cleaned).replaceAll("").trim();

        // 4. Bersihkan pola tanggal (01 AGUST 2025, 02/08/25, dsb) yang mungkin tersisa
        cleaned = cleaned.replaceAll(
                "\\s+\\d{1,2}[/ ](?:JAN|FEB|MAR|APR|MEI|JUN|JUL|AGUS|AGUST|SEP|OKT|NOV|DES|\\d{2})[/ ]?\\d{0,4}.*$",
                "");
        cleaned = cleaned.replaceAll("\\s+\\d{1,2}/\\d{1,2}/?\\d{0,4}.*$", "");

        // 5. Bersihkan Suffix Remark umum lainnya
        String[] noteSuffixes = {
                "BON KANTOR", "LAINNYA", "REMARK", "NOTE", "INV", "PAYMENT", "FORKLIP", "CCTV", "PULSA", "TOKEN"
        };
        for (String suffix : noteSuffixes) {
            cleaned = Pattern.compile("\\s+" + suffix + "(?:\\s+.*|$)", Pattern.CASE_INSENSITIVE).matcher(cleaned)
                    .replaceAll("").trim();
        }

        // 6. Final normalization: hapus spasi ganda yang tersisa dan bersihkan edge
        cleaned = cleaned.replaceAll("\\s{2,}", " ").trim();

        return truncate(cleaned);
    }
}
