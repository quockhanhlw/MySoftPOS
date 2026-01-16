package com.example.mysoftpos.iso8583;

import org.junit.Test;

public class IsoValidatorDateTimeAndF3Test {

    @Test(expected = IllegalStateException.class)
    public void purchase_invalidF13_shouldThrow() {
        IsoMessage m = new IsoMessage("0200")
                .setField(IsoField.PAN_2, "4111111111111111")
                .setField(IsoField.PROCESSING_CODE_3, "000000")
                .setField(IsoField.AMOUNT_4, "000000005000")
                .setField(IsoField.TRANSMISSION_DATETIME_7, "0115123456")
                .setField(IsoField.STAN_11, "000001")
                .setField(IsoField.LOCAL_TIME_12, "123456")
                // invalid: contains '/'
                .setField(IsoField.LOCAL_DATE_13, "15/6")
                .setField(IsoField.MERCHANT_TYPE_18, "5999")
                .setField(IsoField.POS_ENTRY_MODE_22, "012")
                .setField(IsoField.POS_CONDITION_CODE_25, "00")
                .setField(IsoField.ACQUIRER_ID_32, "970403")
                .setField(IsoField.RRN_37, "123456789012")
                .setField(IsoField.TERMINAL_ID_41, TransactionContext.formatTid8("POS1"))
                .setField(IsoField.MERCHANT_ID_42, TransactionContext.formatMid15("MID"))
                .setField(IsoField.MERCHANT_NAME_LOCATION_43, TransactionContext.formatMid15("X") + TransactionContext.formatMid15("Y") + "          ")
                .setField(IsoField.CURRENCY_CODE_49, "704");

        IsoValidator.validatePurchase(m);
    }

    @Test(expected = IllegalStateException.class)
    public void purchase_wrongF3_shouldThrow() {
        IsoMessage m = new IsoMessage("0200")
                .setField(IsoField.PAN_2, "4111111111111111")
                .setField(IsoField.PROCESSING_CODE_3, "020000")
                .setField(IsoField.AMOUNT_4, "000000005000")
                .setField(IsoField.TRANSMISSION_DATETIME_7, "0115123456")
                .setField(IsoField.STAN_11, "000001")
                .setField(IsoField.LOCAL_TIME_12, "123456")
                .setField(IsoField.LOCAL_DATE_13, "0115")
                .setField(IsoField.MERCHANT_TYPE_18, "5999")
                .setField(IsoField.POS_ENTRY_MODE_22, "012")
                .setField(IsoField.POS_CONDITION_CODE_25, "00")
                .setField(IsoField.ACQUIRER_ID_32, "970403")
                .setField(IsoField.RRN_37, "123456789012")
                .setField(IsoField.TERMINAL_ID_41, TransactionContext.formatTid8("POS1"))
                .setField(IsoField.MERCHANT_ID_42, TransactionContext.formatMid15("MID"))
                .setField(IsoField.MERCHANT_NAME_LOCATION_43, "X")
                .setField(IsoField.CURRENCY_CODE_49, "704");

        IsoValidator.validatePurchase(m);
    }
}

