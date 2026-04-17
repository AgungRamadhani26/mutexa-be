package com.example.mutexa_be;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.entity.enums.MutationType;
import com.example.mutexa_be.service.TransactionRefinementService;
import com.example.mutexa_be.service.parser.bni.BniPdfParserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;

public class BniPdfParserServiceTest {

    private BniPdfParserService bniPdfParserService;
    private MutationDocument mockDocument;

    @BeforeEach
    void setUp() {
        bniPdfParserService = new BniPdfParserService();
        mockDocument = new MutationDocument();
    }

    @Test
    void testParseBniPdf() {
        String filePath = "C:/Users/agung/Documents/PDP_BCA_Finance/MutexaApp/Novalino/DataNoval/BNI.pdf";
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("SKIPPING TEST: File BNI.pdf not found at " + filePath);
            return;
        }

        List<BankTransaction> transactions = bniPdfParserService.parsePdf(filePath, mockDocument, "dev1");

        // Print all transactions for manual review during test logic building
        int txNo = 1;
        BigDecimal totalCr = BigDecimal.ZERO;
        BigDecimal totalDb = BigDecimal.ZERO;
        for (BankTransaction tx : transactions) {
            String logLine = String.format("TX %3d | %s | %s | %15.4f | %17.4f | %s",
                    txNo++, tx.getTransactionDate(), tx.getMutationType(),
                    tx.getAmount(), tx.getBalance(), tx.getRawDescription());
            System.out.println(logLine);

            if (tx.getMutationType() == MutationType.CR) totalCr = totalCr.add(tx.getAmount());
            if (tx.getMutationType() == MutationType.DB) totalDb = totalDb.add(tx.getAmount());
        }

        System.out.println("\nTotal CR: " + totalCr);
        System.out.println("Total DB: " + totalDb);
        System.out.println("==========================================");

        // Based on the dump observation:
        assertFalse(transactions.isEmpty(), "Mestinya ada daftar transaksi");

        // Periksa Transaksi Pertama
        BankTransaction tx1 = transactions.get(0);
        assertEquals(LocalDate.of(2025, 8, 1), tx1.getTransactionDate());
        assertEquals(MutationType.DB, tx1.getMutationType());
        assertEquals(0, new BigDecimal("1000000").compareTo(tx1.getAmount()));
        assertEquals(0, new BigDecimal("3507992").compareTo(tx1.getBalance()));
        assertTrue(tx1.getRawDescription().contains("01 agust 2025"));

        // Periksa Transaksi Kedua
        BankTransaction tx2 = transactions.get(1);
        assertEquals(MutationType.DB, tx2.getMutationType());
        assertEquals(0, new BigDecimal("2500").compareTo(tx2.getAmount()));
        assertEquals(0, new BigDecimal("3505492").compareTo(tx2.getBalance()));
        assertTrue(tx2.getRawDescription().contains("BY TRX BIFAST"));
        assertFalse(tx2.getRawDescription().contains("959065"), "Journal No seharusnya di-strip");
    }
}
