package com.example.mutexa_be;
import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.service.parser.bca.BcaPdfParserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class BcaParserTest {

    @Autowired
    BcaPdfParserService parser;

    @Test
    void testParseBca() {
        MutationDocument doc = new MutationDocument();
        String file = "C:\\\\Users\\\\agung\\\\Documents\\\\PDP_BCA_Finance\\\\MutexaApp\\\\Novalino\\\\Data_Mutasi_PDF\\\\BilgusMistison_BCA_JanuariFebruariMaret_2025.pdf";
        List<BankTransaction> txs = parser.parse(doc, file);
        System.out.println("============== HASIL PARSING BCA =============");
        System.out.println("Total Parsed: " + txs.size());
        
        for (int i = 0; i < Math.min(10, txs.size()); i++) {
            BankTransaction t = txs.get(i);
            System.out.println(t.getTransactionDate() + " | " + t.getMutationType() + " " + t.getAmount() + " | SALDO: " + t.getBalance() + " | DESC: " + t.getRawDescription());
        }
        System.out.println("...");
        for (int i = Math.max(0, txs.size() - 5); i < txs.size(); i++) {
            BankTransaction t = txs.get(i);
            System.out.println(t.getTransactionDate() + " | " + t.getMutationType() + " " + t.getAmount() + " | SALDO: " + t.getBalance() + " | DESC: " + t.getRawDescription());
        }
        System.out.println("============== SELESAI =============");
    }
}
