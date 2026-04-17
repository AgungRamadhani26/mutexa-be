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

    @Override
    public String extract(String rawDescription, boolean isCredit) {
        if (rawDescription == null || rawDescription.trim().isEmpty()) return null;

        // Membersihkan spasi ganda dan karakter newline
        String text = normalizeText(rawDescription);

        // ==============================================================================
        // 1. SETOR TUNAI
        // Contoh CR: "SETOR TUNAI | PRATAMA ABADI SENTOSA"
        // ==============================================================================
        Matcher mSetor = Pattern.compile("SETOR TUNAI\\s*\\|\\s*(.+)").matcher(text);
        if (mSetor.find()) {
            return truncate(mSetor.group(1).trim());
        }

        // ==============================================================================
        // 2. PEMINDAHAN (TRANSFER) KE ANTAR REKENING BNI ATAU REGULER
        // Contoh DB: "TRANSFER KE | PEMINDAHAN KE 832070460 Bpk HENDRA GUNAWAN | hendro 02 agust ..."
        // Menargetkan pola "PEMINDAHAN KE [angka rekening] [Gelaran] [NAMA]". Gelar (Bpk/Ibu/Sdr) bersifat opsional.
        // ==============================================================================
        Matcher mTransferBpk = Pattern.compile("PEMINDAHAN KE\\s+\\d+\\s+(?:Bpk|Ibu|Sdr)?\\s*(.+?)\\s*\\|", Pattern.CASE_INSENSITIVE).matcher(text);
        if (mTransferBpk.find()) {
            return truncate(mTransferBpk.group(1).trim());
        }

        // ==============================================================================
        // 3. ECHANNEL - TRANSAKSI MASUK (PEMINDAHAN DARI)
        // Contoh CR: "TRF/PAY/TOP-UP ECHANNEL | PEMINDAHAN DARI 151588883 | 0000000000000000 | PT PRATAMA ABADI SENTOSA"
        // Target ekstraksi: Segmen paling akhir setelah karakter pipe terakhir ("|").
        // ==============================================================================
        Matcher mEchannelDari = Pattern.compile("PEMINDAHAN DARI.*?\\|.*?0{10,}.*?\\|\\s*(.+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (mEchannelDari.find()) {
            String fullSegment = mEchannelDari.group(1).trim();
            // Hilangkan kata "Lainnya" yang sering terikut di ujung transaksi BNI EChannel Out
            if (fullSegment.toLowerCase().endsWith("lainnya")) {
                fullSegment = fullSegment.substring(0, fullSegment.length() - 7).trim();
            }
            return truncate(fullSegment);
        }

        // ==============================================================================
        // 4. ECHANNEL - TRANSAKSI KELUAR (PEMINDAHAN KE)
        // Contoh DB: "TRF/PAY/TOP-UP ECHANNEL | PEMINDAHAN KE 658301015781530 | 0000000000000000 | 658301015781530 tohari pci 01 agust 2025"
        // Target ekstraksi: Mencari bagian teks di antara blok "angka rekening awalan" dan "tanggal/keterangan sisa"
        // ==============================================================================
        Matcher mEchannelKe = Pattern.compile("PEMINDAHAN KE.*?\\|.*?0{10,}.*?\\|\\s*\\d+\\s*(.+?)(?:\\s+\\d{2}\\s+(?:jan|feb|mar|apr|mei|jun|jul|agus|sep|okt|nov|des|agust))", Pattern.CASE_INSENSITIVE).matcher(text);
        if (mEchannelKe.find()) {
            return truncate(mEchannelKe.group(1).trim());
        }

        // Fallback untuk EChannel Keluar tanpa tanggal (kalau format terpotong)
        Matcher mEchannelKeNoDate = Pattern.compile("PEMINDAHAN KE.*?\\|.*?0{10,}.*?\\|\\s*\\d+\\s*(.+?)(?:$|\\s{2,})", Pattern.CASE_INSENSITIVE).matcher(text);
        if (mEchannelKeNoDate.find()) {
            return truncate(mEchannelKeNoDate.group(1).trim());
        }

        // ==============================================================================
        // 5. PENYESUAIAN JENIS BIAYA (FEES / PAJAK / BUNGA)
        // Jika tidak masuk pola di atas, maka cek kamus kata kunci umum BNI
        // ==============================================================================
        if (text.contains("BY TRX BIFAST") || text.contains("BIAYA BIFAST")) return "BIAYA BIFAST";
        if (text.contains("JASA GIRO") || text.contains("BUNGA")) return "BUNGA BANK";
        if (text.contains("BIAYA ADM REK") || text.contains("BIAYA ADMINISTRASI") || text.contains("BIAYA ADM")) return "BIAYA ADMINISTRASI";
        if (text.contains("PPH") || text.contains("PAJAK")) return "PAJAK PPH";

        // Fallback pendelegasian akhir jika tidak ada regex bank-specific yang sesuai
        return fallback(text);
    }
}
