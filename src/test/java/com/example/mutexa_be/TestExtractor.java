package com.example.mutexa_be;

import com.example.mutexa_be.service.extractor.BniCounterpartyExtractor;

public class TestExtractor {
    public static void main(String[] args) {
        BniCounterpartyExtractor ext = new BniCounterpartyExtractor();
        
        String[] samples = {
            "TRF/PAY/TOP-UP ECHANNEL | PEMINDAHAN KE 658301015781530 | 0000000000000000 | 658301015781530  tohari pci 01 agust 2025",
            "TRF/PAY/TOP-UP ECHANNEL | PEMINDAHAN KE 2601078747501 | 0000000000000000 | 002601078747501 sipon 01 agust 2025",
            "TRF/PAY/TOP-UP ECHANNEL | PEMINDAHAN DARI 151588883 | 0000000000000000 | PT PRATAMA ABADI SENTOSA  BON KANTOR",
            "TRF/PAY/TOP-UP ECHANNEL | PEMINDAHAN DARI 903567888 | 0000000000000000 | PRATAMA ABADI SENTOSA PT BNI PAS",
            "TRANSFER KE | PEMINDAHAN KE   832070460 Bpk HENDRA  GUNAWAN | hendro 02 agust 2025  TRF TO:000000000832070460",
            "TRF/PAY/TOP-UP ECHANNEL | PEMINDAHAN KE 6690191014 | 0000000000000000 | 6690191014       cctv pratama keramik 02/08/",
            "TRF/PAY/TOP-UP ECHANNEL | PEMINDAHAN KE 658301015781530 | 0000000000000000 | 658301015781530 tohari pci 01 agust 2025",
            "SETOR TUNAI | PRATAMA ABADI SENTOSA",
            "TRF/PAY/TOP-UP ECHANNEL | PEMINDAHAN KE 902033091 | 0000000000000000 | 0902033091       dika apw 04/08/2025",
            "TRF/PAY/TOP-UP ECHANNEL | PEMINDAHAN KE 6815151511 | 0000000000000000 | 6815151511       pas blitar  forklip"
        };
        
        for (String s : samples) {
            String c = ext.extract(s, false);
            System.out.println("Extracted: '" + c + "' from: " + s);
        }
    }
}
