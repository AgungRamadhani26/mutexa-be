package com.example.mutexa_be;

import com.example.mutexa_be.service.extractor.MandiriCounterpartyExtractor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MandiriGeneralityTest {

    private final MandiriCounterpartyExtractor extractor = new MandiriCounterpartyExtractor();

    @Test
    public void testBiFastTransfer() {
        String raw = "TRANSFER BI FAST DARI BANK BCA AGUNG PRABOWO 123456789";
        assertEquals("AGUNG PRABOWO", extractor.extract(raw, true));
        
        String raw2 = "TRANSFER BI FAST KE BANK MANDIRI BUDI SANTOSO 9876543210";
        assertEquals("BUDI SANTOSO", extractor.extract(raw2, false));
    }

    @Test
    public void testStandardTransfer() {
        String raw = "TRANSFER DARI BANK LAIN BRI SITI MAIMUNAH 0021012345678";
        assertEquals("SITI MAIMUNAH", extractor.extract(raw, true));
        
        String raw2 = "TRANSFER KE BANK LAIN BNI JOKO WIDODO 00912345678";
        assertEquals("JOKO WIDODO", extractor.extract(raw2, false));
    }

    @Test
    public void testInternalMandiri() {
        String raw = "TRANSFER DARI BANK MANDIRI RATNA SARI 123000999888";
        assertEquals("RATNA SARI", extractor.extract(raw, true));
        
        String raw2 = "TRANSFER ANTAR MANDIRI KE DANI RAMDAN 123000777666";
        assertEquals("DANI RAMDAN", extractor.extract(raw2, false));
    }

    @Test
    public void testShortPattern() {
        String raw = "DARI AGUS SETIAWAN 1234567";
        assertEquals("AGUS SETIAWAN", extractor.extract(raw, true));
        
        String raw2 = "KE LINDA PERMATA 7654321";
        assertEquals("LINDA PERMATA", extractor.extract(raw2, false));
    }

    @Test
    public void testPayments() {
        String raw = "PEMBAYARAN TOKOPEDIA 8877665544";
        assertEquals("TOKOPEDIA", extractor.extract(raw, false));
        
        String raw2 = "PAYMENT SHOPEE PAY 1122334455";
        assertEquals("SHOPEE PAY", extractor.extract(raw2, false));
        
        String raw3 = "PEMBAYARAN KARTU KREDIT 414931XXXXXX";
        assertEquals("KARTU KREDIT", extractor.extract(raw3, false));
    }

    @Test
    public void testBankTerms() {
        assertEquals("BIAYA ADMINISTRASI", extractor.extract("BIAYA ADM BULANAN", false));
        assertEquals("BIAYA ADMINISTRASI REKENING", extractor.extract("BIAYA ADM REKENING BULAN JAN", false));
        assertEquals("INTEREST", extractor.extract("BUNGA REKENING MARET", true));
        assertEquals("TAX", extractor.extract("TAX ON INTEREST", false));
        assertEquals("BIAYA SALDO MINIMUM", extractor.extract("BIAYA SALDO MINIMUM", false));
    }
}
