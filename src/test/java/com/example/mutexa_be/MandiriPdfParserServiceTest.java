
package com.example.mutexa_be;

import com.example.mutexa_be.entity.BankAccount;
import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.entity.enums.MutationType;
import com.example.mutexa_be.entity.enums.TransactionCategory;
import com.example.mutexa_be.service.TransactionRefinementService;
import com.example.mutexa_be.service.parser.mandiri.MandiriPdfParserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

public class MandiriPdfParserServiceTest {

    private MandiriPdfParserService parser;
    private TransactionRefinementService mockRefinement;

    @BeforeEach
    void setUp() {
        mockRefinement = Mockito.mock(TransactionRefinementService.class);

        // Configure mock to pass through description and return defaults
        Mockito.when(mockRefinement.normalizeDescription(anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(mockRefinement.extractCounterpartyName(anyString(), anyString(), anyBoolean()))
                .thenReturn("MOCK_CP");
        Mockito.when(mockRefinement.categorizeTransaction(anyString(), any(BigDecimal.class), anyBoolean()))
                .thenReturn(TransactionCategory.TRANSFER);

        parser = new MandiriPdfParserService(mockRefinement);
    }

    @Test
    void testParseBilgusMandiri() {
        MutationDocument doc = new MutationDocument();
        BankAccount account = new BankAccount();
        account.setAccountNumber("1020006501685");
        doc.setBankAccount(account);

        String path = "C:/Users/agung/Documents/PDP_BCA_Finance/MutexaApp/Novalino/Data_Mutasi_PDF/BilgusMistison_Mandiri_JanuariFebruariMaret_2025.pdf";
        List<BankTransaction> txs = parser.parse(doc, path);

        // Print semua transaksi untuk verifikasi visual
        System.out.println("========== MANDIRI PARSER TEST ==========");
        System.out.println("Total transaksi: " + txs.size());
        System.out.println();

        BigDecimal totalCR = BigDecimal.ZERO;
        BigDecimal totalDB = BigDecimal.ZERO;
        int crCount = 0;
        int dbCount = 0;

        for (int i = 0; i < txs.size(); i++) {
            BankTransaction tx = txs.get(i);
            System.out.printf("TX %3d | %s | %s | %15s | %18s | %s%n",
                    i + 1,
                    tx.getTransactionDate(),
                    tx.getMutationType(),
                    tx.getAmount().toPlainString(),
                    tx.getBalance().toPlainString(),
                    tx.getRawDescription());

            if (tx.getMutationType() == MutationType.CR) {
                totalCR = totalCR.add(tx.getAmount());
                crCount++;
            } else {
                totalDB = totalDB.add(tx.getAmount());
                dbCount++;
            }
        }

        System.out.println();
        System.out.println("Total CR: " + totalCR.toPlainString() + " (" + crCount + " transaksi)");
        System.out.println("Total DB: " + totalDB.toPlainString() + " (" + dbCount + " transaksi)");
        System.out.println("==========================================");

        // Verifikasi: PDF memiliki 3 periode (Jan=50, Feb=34, Mar=48) = 132 transaksi
        assertEquals(132, txs.size(), "Harus ada 132 transaksi total (Jan:50 + Feb:34 + Mar:48)");

        // Verifikasi tidak ada tanggal null (fallback ke now)
        for (BankTransaction tx : txs) {
            assertNotNull(tx.getTransactionDate(), "Tanggal tidak boleh null: " + tx.getRawDescription());
            assertNotNull(tx.getAmount(), "Amount tidak boleh null");
            assertNotNull(tx.getBalance(), "Balance tidak boleh null");
            assertTrue(tx.getAmount().compareTo(BigDecimal.ZERO) > 0, "Amount harus positif: " + tx.getRawDescription());
        }

        // Verify TX1 (Transfer dari Bank lain, CR)
        BankTransaction tx1 = txs.get(0);
        assertEquals(MutationType.CR, tx1.getMutationType());
        assertEquals(0, new BigDecimal("100000000.0000").compareTo(tx1.getAmount()));
        assertTrue(tx1.getRawDescription().toUpperCase().contains("TRANSFER DARI BANK LAIN"));

        // Verify a Biaya transfer BI Fast (DB)
        BankTransaction tx5 = txs.get(4);
        assertEquals(MutationType.DB, tx5.getMutationType());
        assertEquals(0, new BigDecimal("2500.0000").compareTo(tx5.getAmount()));
        assertTrue(tx5.getRawDescription().toUpperCase().contains("BIAYA TRANSFER BI FAST"));
    }
}
