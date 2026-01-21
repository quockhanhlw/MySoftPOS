package com.example.mysoftpos.iso8583;

import org.junit.Test;

import static org.junit.Assert.*;

public class IsoRequestBuilderTest {

    @Test
    public void purchase_hasAllMandatoryFields() {
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
                .merchantNameLocation43("MY EPOS TEST")
                .currency49("704")
                .encryptPin(false)
                .build();

        IsoMessage m = IsoRequestBuilder.buildPurchase(c);
        for (int f : FieldRules.PURCHASE_REQUIRED) {
            assertTrue("missing F" + f, m.hasField(f));
        }
        assertEquals("0200", m.getMti());
    }

    @Test(expected = IllegalStateException.class)
    public void purchase_missingRequired_shouldThrow() {
        TransactionContext c = new TransactionContext.Builder(TxnType.PURCHASE)
                .pan2("4111111111111111")
                // missing proc code etc
                .build();
        IsoRequestBuilder.buildPurchase(c);
    }
}
