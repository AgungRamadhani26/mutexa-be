
package com.example.mutexa_be;

import com.example.mutexa_be.entity.BankAccount;
import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.service.TransactionRefinementService;
import com.example.mutexa_be.service.parser.mandiri.MandiriPdfParserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class MandiriPdfParserServiceTest {

    private MandiriPdfParserService parser;
    private TransactionRefinementService mockRefinement;

    @BeforeEach
    void setUp() {
        mockRefinement = Mockito.mock(TransactionRefinementService.class);
        parser = new MandiriPdfParserService(mockRefinement);
    }

    @Test
    void testParse() {
        MutationDocument doc = new MutationDocument();
        BankAccount account = new BankAccount();
        account.setAccountNumber("1234567890");
        doc.setBankAccount(account);

        String path = "C:/Users/agung/Documents/PDP_BCA_Finance/MutexaApp/Novalino/Data_Mutasi_PDF/BilgusMistison_Mandiri_JanuariFebruariMaret_2025.pdf";
        List<BankTransaction> txs = parser.parse(doc, path);
        
        System.out.println("Parsed " + txs.size() + " transactions.");
        for (BankTransaction tx : txs) {
            System.out.println(tx.getTransactionDate() + " | " + tx.getMutationType() + " | " + tx.getAmount() + " | " + tx.getBalance() + " | " + tx.getRawDescription());
        }
        
        assertFalse(txs.isEmpty());
    }
}
