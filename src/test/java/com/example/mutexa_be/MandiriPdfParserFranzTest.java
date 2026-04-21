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

public class MandiriPdfParserFranzTest {

        private MandiriPdfParserService parser;

        @BeforeEach
        void setUp() {
                TransactionRefinementService mockRefinement = Mockito.mock(TransactionRefinementService.class);

                Mockito.when(mockRefinement.normalizeDescription(anyString()))
                                .thenAnswer(inv -> inv.getArgument(0));
                Mockito.when(mockRefinement.extractCounterpartyName(anyString(), anyString(), anyBoolean()))
                                .thenReturn("MOCK_CP");
                Mockito.when(mockRefinement.categorizeTransaction(anyString(), any(BigDecimal.class), anyBoolean()))
                                .thenReturn(TransactionCategory.TRANSFER);

                parser = new MandiriPdfParserService(mockRefinement);
        }

        @Test
        void testParseFranzHermann() {
                MutationDocument doc = new MutationDocument();
                BankAccount account = new BankAccount();
                account.setAccountNumber("FRANZ_TEST");
                doc.setBankAccount(account);

                String path = "C:/Users/agung/Downloads/MANDIRI - FRANZ HERMANN.pdf";
                List<BankTransaction> txs = parser.parse(doc, path);

                System.out.println("========== MANDIRI PARSER (FRANZ HERMANN) ==========");
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
                System.out.printf("Total CR: %s (%d transaksi)%n", totalCR.toPlainString(), crCount);
                System.out.printf("Total DB: %s (%d transaksi)%n", totalDB.toPlainString(), dbCount);
                System.out.println("====================================================");

                // Basic assertions
                assertTrue(txs.size() > 0, "Harus ada minimal 1 transaksi");

                for (int i = 0; i < txs.size(); i++) {
                        BankTransaction tx = txs.get(i);
                        assertNotNull(tx.getTransactionDate(),
                                        "TX" + (i + 1) + " tanggal null: " + tx.getRawDescription());
                        assertNotNull(tx.getAmount(), "TX" + (i + 1) + " amount null");
                        assertNotNull(tx.getBalance(), "TX" + (i + 1) + " balance null");
                        assertTrue(tx.getAmount().compareTo(BigDecimal.ZERO) > 0,
                                        "TX" + (i + 1) + " amount harus positif: " + tx.getRawDescription());

                        // Pastikan tidak ada disclaimer bocor ke deskripsi
                        String desc = tx.getRawDescription().toLowerCase();
                        assertFalse(desc.contains("segala bentuk"),
                                        "TX" + (i + 1) + " disclaimer bocor: " + tx.getRawDescription());
                        assertFalse(desc.contains("e-statement ini merupakan"),
                                        "TX" + (i + 1) + " disclaimer bocor: " + tx.getRawDescription());
                        assertFalse(desc.contains("batas akhir transaksi"),
                                        "TX" + (i + 1) + " footer bocor: " + tx.getRawDescription());
                }
        }
}
