package com.example.mysoftpos.iso8583;

import org.junit.Test;

import static org.junit.Assert.*;

public class NapasField60And90Test {

    @Test
    public void field60_upiChipCaseA_length27() {
        String f60 = TransactionContext.buildField60UpiChipCaseA(
                '6', '1', "03", "000", '1');
        assertEquals(27, f60.length());
    }

    @Test
    public void field90_full42_buildsCorrectly() {
        String f90 = TransactionContext.buildField90Full42(
                "0200",
                "123",
                "0115123456",
                "970403",
                null);
        assertEquals(42, f90.length());
        assertTrue(f90.matches("\\d{42}"));
        // forwardingId must be 11 zeros if null
        assertTrue(f90.endsWith("00000000000"));
    }

    @Test(expected = IllegalStateException.class)
    public void validator_reversal_requiresStrictF90() {
        IsoMessage m = new IsoMessage("0400")
                .setField(IsoField.PAN_2, "4111111111111111")
                .setField(IsoField.PROCESSING_CODE_3, "000000")
                .setField(IsoField.AMOUNT_4, "000000005000")
                .setField(IsoField.TRANSMISSION_DATETIME_7, "0115123456")
                .setField(IsoField.STAN_11, "123456")
                .setField(IsoField.LOCAL_TIME_12, "123456")
                .setField(IsoField.LOCAL_DATE_13, "0115")
                .setField(IsoField.MERCHANT_TYPE_18, "5999")
                .setField(IsoField.ACQUIRER_ID_32, "970403")
                .setField(IsoField.RRN_37, "123456789012")
                .setField(IsoField.TERMINAL_ID_41, TransactionContext.formatTid8("POS1"))
                .setField(IsoField.MERCHANT_ID_42, TransactionContext.formatMid15("MID"))
                .setField(IsoField.MERCHANT_NAME_LOCATION_43, "X")
                .setField(IsoField.CURRENCY_CODE_49, "704")
                .setField(IsoField.ORIGINAL_DATA_ELEMENTS_90, "123");

        IsoValidator.validateReversal(m);
    }
}

