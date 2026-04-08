package com.example.mutexa_be;

import com.example.mutexa_be.service.TransactionRefinementService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TransactionRefinementUnitTest {

    TransactionRefinementService service = new TransactionRefinementService();

    // ================================================================
    // BCA Tests
    // ================================================================

    @Test
    public void testBca_TrsfEBanking_NamadiUjung() {
        String result = service.extractCounterpartyName("BCA",
            "TRSF E-BANKING DB 0101/FTSCY/WS95271 55,000,000.00 MARSHA PRISCILLIA");
        assertEquals("MARSHA PRISCILLIA", result);
    }

    @Test
    public void testBca_InterestCredit() {
        String result = service.extractCounterpartyName("BCA", "INTEREST CREDIT");
        assertEquals("INTEREST CREDIT", result);
    }

    @Test
    public void testBca_OdIntCharge() {
        String result = service.extractCounterpartyName("BCA", "02/01 OD INT CHARGE 12181001-A");
        assertEquals("OD INT CHARGE", result);
    }

    @Test
    public void testBca_Cenaidja() {
        String result = service.extractCounterpartyName("BCA", "CENAIDJA/HALO BCA PT 1234567890");
        assertEquals("HALO BCA", result);
    }

    @Test
    public void testBca_TransferDana() {
        String result = service.extractCounterpartyName("BCA", "TRANSFER DANA DARI LISDRI SUSTIWI");
        assertEquals("LISDRI SUSTIWI", result);
    }

    // ================================================================
    // BRI Tests (deskripsi asli dari PDF Lisdri & Ambe)
    // ================================================================

    @Test
    public void testBri_BiFastKe() {
        // Dari PDF: "Transfer BI-Fast ke BANK CENTRAL ASIA - 8465752994 - Khintesa Nur Wibowo Mh"
        String result = service.extractCounterpartyName("BRI",
            "Transfer BI-Fast ke BANK CENTRAL ASIA - 8465752994 - Khintesa Nur Wibowo Mh");
        assertEquals("KHINTESA NUR WIBOWO MH", result);
    }

    @Test
    public void testBri_BiFastDari() {
        // Dari PDF: "Transfer BI-Fast dari BANK SYARIAH MANDIRI - LISDRISUSTIW"
        String result = service.extractCounterpartyName("BRI",
            "Transfer BI-Fast dari BANK SYARIAH MANDIRI - LISDRISUSTIW");
        assertEquals("LISDRISUSTIW", result);
    }

    @Test
    public void testBri_IbizTo() {
        // Dari PDF: "IBIZ AMBE MAJU BERS TO NIA YUSNIA"
        String result = service.extractCounterpartyName("BRI",
            "IBIZ AMBE MAJU BERS TO NIA YUSNIA");
        assertEquals("NIA YUSNIA", result);
    }

    @Test
    public void testBri_IbizTo_MayaWijayanti() {
        // Dari PDF: "IBIZ AMBE MAJU BERS TO MAYA WIJAYANTI PU"
        String result = service.extractCounterpartyName("BRI",
            "IBIZ AMBE MAJU BERS TO MAYA WIJAYANTI PU");
        assertEquals("MAYA WIJAYANTI PU", result);
    }

    @Test
    public void testBri_NbmbTo() {
        // Dari PDF: "NBMB RURIN PUTRI RI TO AMBE MAJU BERSAMA"
        String result = service.extractCounterpartyName("BRI",
            "NBMB RURIN PUTRI RI TO AMBE MAJU BERSAMA");
        assertEquals("RURIN PUTRI RI", result);
    }

    @Test
    public void testBri_TransferKe() {
        // Dari PDF: "Transfer Ke Anita Trisna Lati via BRImo"
        String result = service.extractCounterpartyName("BRI",
            "Transfer Ke Anita Trisna Lati via BRImo");
        assertEquals("ANITA TRISNA LATI", result);
    }

    @Test
    public void testBri_PembayaranBriva() {
        // Dari PDF: "Pembayaran BRIVA ke SHOPEE - 112010102128384327"
        String result = service.extractCounterpartyName("BRI",
            "Pembayaran BRIVA ke SHOPEE - 112010102128384327");
        assertEquals("SHOPEE", result);
    }

    @Test
    public void testBri_PembelianPln() {
        String result = service.extractCounterpartyName("BRI",
            "Pembelian Token PLN 86082012419 via BRImo");
        assertEquals("PLN", result);
    }

    @Test
    public void testBri_Qris() {
        String result = service.extractCounterpartyName("BRI",
            "QRISRNS938968996282#9360000210064671967");
        assertEquals("QRIS", result);
    }

    @Test
    public void testBri_BiayaAdm() {
        String result = service.extractCounterpartyName("BRI", "Biaya Administrasi");
        assertEquals("BIAYA ADMINISTRASI", result);
    }

    // ================================================================
    // Mandiri Tests (deskripsi asli dari PDF Ahsa Jaya)
    // ================================================================

    @Test
    public void testMandiri_McmKe() {
        // Dari PDF: multi-line "MCM InhouseTrf KE JUI SHIN INDONESIA Transfer Fee"
        String result = service.extractCounterpartyName("MANDIRI",
            "MCM InhouseTrf KE JUI SHIN INDONESIA Transfer Fee");
        assertEquals("JUI SHIN INDONESIA", result);
    }

    @Test
    public void testMandiri_McmDari() {
        String result = service.extractCounterpartyName("MANDIRI",
            "MCM InhouseTrf DARI AHSA JAYA METALINDO Transfer Fee");
        assertEquals("AHSA JAYA METALINDO", result);
    }

    @Test
    public void testMandiri_Cenaidja() {
        // Dari PDF: "CENAIDJA/SUN POWER CERAMICS PT AHSA-SUN POWER CERAM99102"
        String result = service.extractCounterpartyName("MANDIRI",
            "CENAIDJA/SUN POWER CERAMICS PT AHSA-SUN POWER CERAM99102");
        assertEquals("SUN POWER CERAMICS", result);
    }

    @Test
    public void testMandiri_Bninidja() {
        // Dari PDF: "BNINIDJA/EUIS MARIAM"
        String result = service.extractCounterpartyName("MANDIRI",
            "BNINIDJA/EUIS MARIAM");
        assertEquals("EUIS MARIAM", result);
    }

    @Test
    public void testMandiri_Brinidja() {
        // Dari PDF: "BRINIDJA/ANUGRAH BANGUN CAHAY AHSA-ANUGRAH"
        String result = service.extractCounterpartyName("MANDIRI",
            "BRINIDJA/ANUGRAH BANGUN CAHAY AHSA-ANUGRAH");
        assertEquals("ANUGRAH BANGUN CAHAY", result);
    }

    @Test
    public void testMandiri_SetorTunai() {
        // Dari PDF: "Setor tunai AHSA JAYA METALINDO 01-0718203"
        String result = service.extractCounterpartyName("MANDIRI",
            "Setor tunai AHSA JAYA METALINDO 01-0718203");
        assertEquals("AHSA JAYA METALINDO", result);
    }

    // ================================================================
    // UOB Tests (deskripsi asli dari PDF Ega Tekelindo)
    // ================================================================

    @Test
    public void testUob_PtName() {
        // Dari PDF: "Misc Credit TRANSFER DANA RIC511030152C01 PT. EGA TEKELINDO PRIMA"
        String result = service.extractCounterpartyName("UOB",
            "Misc Credit TRANSFER DANA RIC511030152C01 PT. EGA TEKELINDO PRIMA");
        assertEquals("PT. EGA TEKELINDO PRIMA", result);
    }

    @Test
    public void testUob_InterestCredit() {
        String result = service.extractCounterpartyName("UOB", "Interest Credit");
        assertEquals("INTEREST CREDIT", result);
    }

    @Test
    public void testUob_OdIntCharge() {
        String result = service.extractCounterpartyName("UOB", "OD Int Charge");
        assertEquals("OD INT CHARGE", result);
    }

    @Test
    public void testUob_WithholdingTax() {
        String result = service.extractCounterpartyName("UOB", "Withholding Tax Dr");
        assertEquals("WITHHOLDING TAX", result);
    }

    @Test
    public void testUob_MiscDebitDeposito() {
        String result = service.extractCounterpartyName("UOB", "Misc Debit PENEMPATAN DEPOSITO");
        assertEquals("PENEMPATAN DEPOSITO", result);
    }

    @Test
    public void testUob_Cash() {
        String result = service.extractCounterpartyName("UOB", "Cash BN");
        assertEquals("CASH", result);
    }

    // ================================================================
    // Generic/Fallback Tests
    // ================================================================

    @Test
    public void testGeneric_NullInput() {
        assertNull(service.extractCounterpartyName("BCA", null));
        assertNull(service.extractCounterpartyName("BCA", "  "));
        assertNull(service.extractCounterpartyName(null));
    }
}
