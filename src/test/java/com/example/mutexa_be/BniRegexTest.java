package com.example.mutexa_be;

import org.junit.jupiter.api.Test;
import java.util.regex.*;

public class BniRegexTest {
    @Test
    void testRegex() {
        String test1 = "959065 BY TRX BIFAST 3,505,492.002 D2,500.00";
        String test2 = "3,507,992.001 D1,000,000.00";
        String test3 = "13,093,492.0011 C10,000,000.00";
        String test4 = "658301015781530  tohari pci 01 agust 2025 3,507,992.001 D1,000,000.00";
        
        Pattern p = Pattern.compile("^(.*?\\s+)?(?<balance>\\d{1,3}(?:,\\d{3})*\\.\\d{2})(?<no>\\d+)\\s+(?<type>[DC])(?<amount>\\d{1,3}(?:,\\d{3})*\\.\\d{2})$");
        
        for (String t : new String[]{test1, test2, test3, test4}) {
            Matcher m = p.matcher(t);
            if (m.matches()) {
                System.out.println("Line: " + t);
                System.out.println("Desc: " + (m.group(1) == null ? "" : m.group(1).trim()));
                System.out.println("Balance: " + m.group("balance"));
                System.out.println("No: " + m.group("no"));
                System.out.println("Type: " + m.group("type"));
                System.out.println("Amount: " + m.group("amount"));
                System.out.println("---");
            } else {
                System.out.println("NO MATCH: " + t);
            }
        }
    }
}
