package com.example.mysoftpos.iso8583;

import org.junit.Test;

import static org.junit.Assert.*;

public class IsoUnpackerTest {

    @Test
    public void pack_then_unpack_purchase_shouldRoundTrip() {
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

        IsoMessage req = IsoRequestBuilder.buildPurchase(c);
        byte[] payload = IsoPacker.pack(req);

        IsoMessage unpacked = IsoUnpacker.unpack(payload);

        assertEquals(req.getMti(), unpacked.getMti());
        for (int f : req.getFieldNumbers()) {
            assertEquals("F" + f, req.getField(f), unpacked.getField(f));
        }
    }

    @Test
    public void pack_then_unpack_framed_shouldRoundTrip() {
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

        IsoMessage req = IsoRequestBuilder.buildPurchase(c);
        byte[] payload = IsoPacker.pack(req);
        byte[] framed = IsoHeader.withLengthPrefix2(payload);

        IsoMessage unpacked = IsoUnpacker.unpackFramed(framed);

        assertEquals(req.getMti(), unpacked.getMti());
        assertEquals(req.getField(IsoField.AMOUNT_4), unpacked.getField(IsoField.AMOUNT_4));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unpackFramed_wrongLength_shouldThrow() {
        byte[] framed = new byte[] {0x00, 0x10, 0x01, 0x02, 0x03}; // says len=16 but has 3 bytes
        IsoUnpacker.unpackFramed(framed);
    }

    private static String padRight(String s, int len) {
        String v = s == null ? "" : s;
        if (v.length() >= len) return v.substring(0, len);
        StringBuilder sb = new StringBuilder(v);
        while (sb.length() < len) sb.append(' ');
        return sb.toString();
    }
}
