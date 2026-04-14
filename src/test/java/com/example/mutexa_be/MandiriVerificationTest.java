package com.example.mutexa_be;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.service.parser.mandiri.MandiriPdfParserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.List;

@SpringBootTest
public class MandiriVerificationTest {

    @Autowired
    private MandiriPdfParserService parser;

    @Test
    public void testParsing() {
        File file = new File("C:\\\\Users\\\\agung\\\\Documents\\\\PDP_BCA_Finance\\\\MutexaApp\\\\Novalino\\\\Data_Mutasi_PDF\\\\BilgusMistison_Mandiri_JanuariFebruariMaret_2025.pdf");
        MutationDocument doc = new MutationDocument();
        List<BankTransaction> txs = parser.parse(doc, file.getAbsolutePath());
        
        System.out.println("============== MANDIRI EXTRACTION =============");
        for (int i = 0; i < txs.size(); i++) {
            BankTransaction t = txs.get(i);
            System.out.println("TX " + (i + 1) );
            System.out.println("  DATE: " + t.getTransactionDate());
            System.out.println("  TYPE: " + t.getMutationType());
            System.out.println("  AMT : " + t.getAmount());
            System.out.println("  BAL : " + t.getBalance());
            System.out.println("  CP  : [" + t.getCounterpartyName() + "]");
            System.out.println("  RAW : " + t.getRawDescription());
            System.out.println("----------------------------------------------");
        }
    }
}
