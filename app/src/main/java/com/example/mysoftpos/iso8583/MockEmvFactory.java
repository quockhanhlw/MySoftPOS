package com.example.mysoftpos.iso8583;

import com.example.mysoftpos.utils.EmvUtils;
import java.util.Random;

/**
 * Factory to generate Mock DE 55 Data for Simulator.
 */
public class MockEmvFactory {

    public static String generateMockDe55(String transactionDate, String currencyCode) {
        StringBuilder sb = new StringBuilder();

        // 1. 9F26 - ARQC (8 bytes)
        sb.append(EmvUtils.buildTlv(EmvTags.AC, "4E69485123961805"));

        // 2. 9F27 - CID (1 byte)
        sb.append(EmvUtils.buildTlv(EmvTags.CID, "80"));

        // 3. 9F10 - IAD (Variable, using mock 7 bytes)
        sb.append(EmvUtils.buildTlv(EmvTags.IAD, "06010A03000000"));

        // 4. 9F37 - Unpredictable Number (4 bytes)
        // Generate random 4 bytes hex
        sb.append(EmvUtils.buildTlv(EmvTags.UNPREDICTABLE_NUM, generateRandomHex(4)));

        // 5. 9F36 - ATC (2 bytes)
        sb.append(EmvUtils.buildTlv(EmvTags.ATC, "0001"));

        // 6. 95 - TVR (5 bytes)
        sb.append(EmvUtils.buildTlv(EmvTags.TVR, "0000008000"));

        // 7. 9A - Transaction Date (YYMMDD)
        if (transactionDate == null)
            transactionDate = "251201"; // Fallback
        sb.append(EmvUtils.buildTlv(EmvTags.TXN_DATE, transactionDate));

        // 8. 9C - Transaction Type (1 byte) - Purchase "00"
        sb.append(EmvUtils.buildTlv(EmvTags.TXN_TYPE, "00"));

        // 9. 5F2A - Currency Code (2 bytes)
        if (currencyCode == null)
            currencyCode = "0704"; // VND
        // Ensure 2 bytes (0704)
        if (currencyCode.length() == 3)
            currencyCode = "0" + currencyCode;
        sb.append(EmvUtils.buildTlv(EmvTags.TXN_CURRENCY, currencyCode));

        // 10. 82 - AIP (2 bytes)
        sb.append(EmvUtils.buildTlv(EmvTags.AIP, "1800"));

        return sb.toString();
    }

    private static String generateRandomHex(int byteLength) {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < byteLength; i++) {
            sb.append(String.format("%02X", r.nextInt(256)));
        }
        return sb.toString();
    }
}
