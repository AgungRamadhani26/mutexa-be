package com.example.mutexa_be.service.extractor;

/**
 * Interface untuk mengekstrak nama counterparty (pihak lawan transaksi)
 * dari deskripsi mutasi bank.
 *
 * Prinsip SOLID: OCP (Open/Closed Principle) & SRP (Single Responsibility Principle)
 */
public interface CounterpartyExtractor {
    
    /**
     * Mengembalikan identitas bank yang ditangani oleh extractor ini.
     * @return Kode bank, contoh: "BRI", "BCA"
     */
    String getBankName();

    /**
     * Mengekstrak nama counterparty dari deskripsi mentah.
     * @param rawDescription Deskripsi asli dari bank
     * @param isCredit True jika transaksi masuk (Deposit/Credit), False jika keluar (Withdrawal/Debit)
     * @return Nama hasil ekstraksi atau fallback
     */
    String extract(String rawDescription, boolean isCredit);
}
