
package com.example.mutexa_be.service.extractor;

import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ekstraktor counterparty khusus untuk e-Statement Mandiri (Livin').
 *
 * Raw description yang diterima sudah dirakit oleh MandiriPdfParserService
 * dalam format bersih, contoh:
 *   "Transfer dari BANK MANDIRI DESSY ARIZTIA SAVITR 1010006427981 Pelunasan DP an Dessy Lapagu"
 *   "Transfer BI Fast Ke BCA BILGUS MISTISON 33331808"
 *   "Pembayaran BFI Finance Indonesia Tbk, PT 8853754692407706"
 *   "Biaya administrasi rekening"
 *   "Pajak rekening"
 */
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

        // =====================================================
        // 1. BIAYA / FEE TETAP (prioritas tertinggi karena paling spesifik)
        // =====================================================
        if (text.contains("BIAYA TRANSFER")) return "BIAYA TRANSFER";

        if (text.contains("BIAYA ADMINISTRASI") || text.contains("BIAYA ADM")) {
            if (text.contains("REKENING")) return "BIAYA ADMINISTRASI REKENING";
            if (text.contains("KARTU")) return "BIAYA ADMINISTRASI KARTU";
            return "BIAYA ADMINISTRASI";
        }

        if (text.contains("BIAYA SALDO MINIMUM")) return "BIAYA SALDO MINIMUM";
        if (text.contains("BIAYA PENGGANTIAN KARTU")) return "BIAYA PENGGANTIAN KARTU";
        if (text.contains("PAJAK REKENING") || text.contains("TAX ON INTEREST") || text.equals("TAX")) return "TAX";
        if (text.contains("BUNGA REKENING") || text.equals("INTEREST")) return "INTEREST";

        // =====================================================
        // 2. PEMBAYARAN (payment to biller)
        // =====================================================
        if (text.contains("PEMBAYARAN") || text.contains("PAYMENT")) {
            // 2a. Kartu kredit
            if (text.contains("KARTU KREDIT") || text.contains("CREDIT CARD")) return "KARTU KREDIT";

            // 2b. Pembayaran ke biller (nama biller + nomor referensi)
            // Contoh: "PEMBAYARAN BFI FINANCE INDONESIA TBK, PT 8853754692407706"
            // Contoh: "PEMBAYARAN XENDIT 88908 889081866353115"
            // Contoh: "PAYMENT SHOPEE PAY 1122334455"
            Matcher mPay = Pattern.compile(
                    "(?:PEMBAYARAN|PAYMENT)\\s+(.+?)(?:\\s+\\d{5,}|\\s+TBK|$)"
            ).matcher(text);
            if (mPay.find()) {
                String billerName = mPay.group(1).trim();
                // Bersihkan suffix badan usaha
                billerName = billerName.replaceAll("\\s*,\\s*PT\\s*$", "");
                billerName = billerName.replaceAll("\\s+TBK\\s*$", "");
                billerName = billerName.replaceAll("\\s+\\d+$", "");
                if (billerName.length() > 2) return cleanMandiriName(billerName);
            }
        }

        // =====================================================
        // 3. TRANSFER ANTAR MANDIRI
        // Contoh: "TRANSFER ANTAR MANDIRI DARI MERPATI ABADI SEJAHTERA TRANSFER FEE 2025..."
        // =====================================================
        Matcher mAntar = Pattern.compile(
                "TRANSFER ANTAR MANDIRI\\s+DARI\\s+(.+?)(?:\\s+TRANSFER\\s+FEE|\\s+\\d{10,}|$)"
        ).matcher(text);
        if (mAntar.find()) return cleanMandiriName(mAntar.group(1).trim());

        // =====================================================
        // 4. TRANSFER BI FAST (Dari/Ke bank)
        // Contoh: "TRANSFER BI FAST KE BCA BILGUS MISTISON 33331808"
        // Contoh: "TRANSFER BI FAST DARI BANK BNI SUTRISNO 799999205"
        // Contoh: "TRANSFER BI FAST DARI BRI MUNAWAR AZRAFI 065201021034500"
        // =====================================================
        Matcher mBiFast = Pattern.compile(
                "TRANSFER BI FAST\\s+(?:DARI|KE)\\s+(?:BANK\\s+)?([A-Z]+)\\s+(.+?)(?:\\s+\\d{5,}|$)"
        ).matcher(text);
        if (mBiFast.find()) {
            return cleanMandiriName(mBiFast.group(2).trim());
        }

        // =====================================================
        // 5. TRANSFER DARI/KE BANK MANDIRI
        // Contoh: "TRANSFER DARI BANK MANDIRI DESSY ARIZTIA SAVITR 1010006427981"
        // Contoh: "TRANSFER KE BANK MANDIRI SRI REJEKI 1550010522749"
        // =====================================================
        Matcher mMandiri = Pattern.compile(
                "TRANSFER\\s+(?:DARI|KE)\\s+BANK\\s+MANDIRI\\s+(.+?)(?:\\s+\\d{5,})"
        ).matcher(text);
        if (mMandiri.find()) return cleanMandiriName(mMandiri.group(1).trim());

        // =====================================================
        // 6. TRANSFER DARI/KE BANK LAIN
        // Contoh: "TRANSFER DARI BANK LAIN BRI EMHA ANANDA PERKASA 2901003208304"
        // Contoh: "TRANSFER KE BANK LAIN BCA BILGUS MISTISON 3991289419"
        // =====================================================
        Matcher mBankLain = Pattern.compile(
                "TRANSFER\\s+(?:DARI|KE)\\s+BANK\\s+LAIN\\s+([A-Z]+)\\s+(.+?)(?:\\s+\\d{5,}|$)"
        ).matcher(text);
        if (mBankLain.find()) {
            String name = mBankLain.group(2).trim();
            if (name.length() > 2) return cleanMandiriName(name);
        }

        // =====================================================
        // 7. POLA GENERIK DARI/KE (fallback untuk transfer)
        // =====================================================
        Matcher mDariKe = Pattern.compile(
                "(?:DARI|KE)\\s+(?:BANK\\s+)?(?:BCA|BRI|BNI|MANDIRI|UOB|CIMB|DANAMON|PERMATA|BSI)\\s+(.+?)(?:\\s+\\d{5,}|$)"
        ).matcher(text);
        if (mDariKe.find()) {
            String name = mDariKe.group(1).trim();
            if (name.length() > 2) return cleanMandiriName(name);
        }

        // =====================================================
        // 8. FALLBACK
        // =====================================================
        return fallback(text);
    }

    /**
     * Membersihkan nama counterparty dari noise khas Mandiri.
     */
    private String cleanMandiriName(String name) {
        if (name == null || name.trim().isEmpty()) return null;

        name = name.toUpperCase().trim();

        // Hapus nomor urut menggantung di awal (misal: "2 ANDRY")
        name = name.replaceAll("^\\d{1,3}\\s+", "");

        // Hapus nomor rekening/referensi di akhir (5+ digit)
        name = name.replaceAll("\\s+\\d{5,}.*$", "");

        // Hapus sisa "Transfer Fee" dan referensi
        name = name.replaceAll("\\s+TRANSFER\\s+FEE.*$", "");

        // Hapus prefix arah jika bocor
        name = name.replaceAll("^DARI\\s+", "");
        name = name.replaceAll("^KE\\s+", "");

        // Hapus kode bank jika bocor sebagai prefix
        name = name.replaceAll("^(?:BCA|BRI|BNI|MANDIRI|UOB|CIMB|DANAMON|PERMATA|BSI)\\s+", "");

        // Hapus sisa timestamp
        name = name.replaceAll("\\s+WIB$", "");

        // Hapus sisa disclaimer
        name = name.replaceAll("(?i)BANK MANDIRI.*$", "").trim();
        name = name.replaceAll("(?i)ESTATEMENT.*$", "").trim();

        // Final cleanup: karakter non-alfanumerik di edge, spasi ganda
        name = name.replaceAll("^[^A-Z0-9]+", "").replaceAll("[^A-Z0-9]+$", "");
        name = name.replaceAll("\\s{2,}", " ").trim();

        return name.isEmpty() ? null : truncate(name);
    }
}
