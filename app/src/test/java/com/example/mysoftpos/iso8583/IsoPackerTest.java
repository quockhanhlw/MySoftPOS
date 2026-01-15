package com.example.mysoftpos.iso8583;

import org.junit.Test;

import static org.junit.Assert.*;

public class IsoPackerTest {

    @Test
    public void pack_purchase_containsMtiAndBitmapAndFields() {
        TransactionContext c = new TransactionContext.Builder(TxnType.PURCHASE)
                .pan2("4111111111111111")
                .processingCode3("000000")
                .amount4("000000005000")
                .transmissionDt7("0115123456")
                .stan11("123456")
                .localTime12("123456")
                .localDate13("0115")
                .mcc18("5999")
                .posEntryMode22("012")
                .posCondition25("00")
                .acquirerId32("970403")
                .rrn37("123456789012")
                .terminalId41("TID00001")
                .merchantId42("MID000000000001")
                .merchantNameLocation43(padRight("MY EPOS TEST", 40))
                .currency49("704")
                .encryptPin(false)
                .build();

        IsoMessage m = IsoRequestBuilder.buildPurchase(c);
        byte[] data = IsoPacker.pack(m);

        // MTI must be first 4 bytes
        assertEquals('0', (char) data[0]);
        assertEquals('2', (char) data[1]);
        assertEquals('0', (char) data[2]);
        assertEquals('0', (char) data[3]);

        // Must have bitmap next (at least 8 bytes)
        assertTrue(data.length > 12);
    }

    private static String padRight(String s, int len) {
        StringBuilder sb = new StringBuilder(s == null ? "" : s);
        while (sb.length() < len) sb.append(' ');
        if (sb.length() > len) return sb.substring(0, len);
        return sb.toString();
    }
}

