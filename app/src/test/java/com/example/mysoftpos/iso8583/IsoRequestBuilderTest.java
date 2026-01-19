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

    @Test
    public void reversal_hasAllMandatoryFields_andMtiIs0420() {
        String f90 = TransactionContext.buildField90Full42(
                "0200",
                "123456",
                "0115123456",
                "970403",
                null);

        TransactionContext c = new TransactionContext.Builder(TxnType.VOID_REVERSAL)
                .pan2("4111111111111111")
                .processingCode3("020000")
                .amount4("000000005000")
                .transmissionDt7("0115123456")
                .stan11("123456")
                .localTime12("123456")
                .localDate13("0115")
                .mcc18("5999")
                .acquirerId32("970403")
                .rrn37("123456789012")
                .terminalId41("TID00001")
                .merchantId42("MID000000000001")
                .merchantNameLocation43("MY EPOS TEST")
                .currency49("704")
                .originalDataElements90(f90)
                .mac128("ABCD")
                .build();

        IsoMessage m = IsoRequestBuilder.buildReversal(c);
        for (int f : FieldRules.REVERSAL_REQUIRED) {
            assertTrue("missing F" + f, m.hasField(f));
        }
        assertEquals("0420", m.getMti());
    }

    @Test
    public void void_hasMti0420_andProcCode020000() {
        String f90 = TransactionContext.buildField90Full42(
                "0200",
                "123456",
                "0115123456",
                "970403",
                null);

        TransactionContext c = new TransactionContext.Builder(TxnType.VOID_REVERSAL)
                .pan2("4111111111111111")
                // Processing code will be overridden for void
                .processingCode3("999999")
                .amount4("000000005000")
                .transmissionDt7("0115123456")
                .stan11("123456")
                .localTime12("123456")
                .localDate13("0115")
                .mcc18("5999")
                .acquirerId32("970403")
                .rrn37("123456789012")
                .terminalId41("TID00001")
                .merchantId42("MID000000000001")
                .merchantNameLocation43("MY EPOS TEST")
                .currency49("704")
                .originalDataElements90(f90)
                .mac128("ABCD")
                .build();

        IsoMessage m = IsoRequestBuilder.buildVoid(c);
        assertEquals("0420", m.getMti());
        assertEquals("020000", m.getField(IsoField.PROCESSING_CODE_3));
    }
}
