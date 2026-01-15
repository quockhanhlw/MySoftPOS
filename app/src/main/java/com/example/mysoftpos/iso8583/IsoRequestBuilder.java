package com.example.mysoftpos.iso8583;

/** Builds ISO8583 requests for supported transactions. */
public final class IsoRequestBuilder {
    private IsoRequestBuilder() {}

    /**
     * Build Purchase request.
     *
     * MTI choice depends on host. We use 0200 for financial request.
     */
    public static IsoMessage buildPurchase(TransactionContext c) {
        IsoMessage m = new IsoMessage("0200");

        // Required
        m.setField(IsoField.PAN_2, c.pan2);
        m.setField(IsoField.PROCESSING_CODE_3, c.processingCode3);
        m.setField(IsoField.AMOUNT_4, c.amount4);
        m.setField(IsoField.TRANSMISSION_DATETIME_7, c.transmissionDt7);
        m.setField(IsoField.STAN_11, c.stan11);
        m.setField(IsoField.LOCAL_TIME_12, c.localTime12);
        m.setField(IsoField.LOCAL_DATE_13, c.localDate13);
        m.setField(IsoField.MERCHANT_TYPE_18, c.mcc18);
        m.setField(IsoField.POS_ENTRY_MODE_22, c.posEntryMode22);
        m.setField(IsoField.POS_CONDITION_CODE_25, c.posCondition25);
        m.setField(IsoField.ACQUIRER_ID_32, c.acquirerId32);
        m.setField(IsoField.RRN_37, c.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, c.terminalId41);
        m.setField(IsoField.MERCHANT_ID_42, c.merchantId42);
        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, c.merchantNameLocation43);
        m.setField(IsoField.CURRENCY_CODE_49, c.currency49);

        // Conditional (set only when provided)
        m.setField(IsoField.COUNTRY_CODE_19, c.country19);
        m.setField(IsoField.CARD_SEQ_23, c.cardSeq23);
        m.setField(IsoField.TRACK2_35, c.track2_35);
        if (c.encryptPin) {
            m.setField(IsoField.PIN_BLOCK_52, c.pinBlock52);
        }
        m.setField(IsoField.ICC_DATA_55, c.iccData55);
        m.setField(IsoField.RESERVED_PRIVATE_60, c.field60);

        // Optional
        m.setField(IsoField.EXPIRATION_DATE_14, c.expiry14);
        m.setField(IsoField.MAC_128, c.mac128);

        IsoValidator.validatePurchase(m);
        return m;
    }

    /**
     * Build Void/Reversal request.
     * We choose MTI 0400 (reversal advice) as typical reversal transaction.
     */
    public static IsoMessage buildVoidReversal(TransactionContext c) {
        IsoMessage m = new IsoMessage("0400");

        // Required (must be present, and for reversal should be same as original)
        m.setField(IsoField.PAN_2, c.pan2);
        m.setField(IsoField.PROCESSING_CODE_3, c.processingCode3);
        m.setField(IsoField.AMOUNT_4, c.amount4);
        m.setField(IsoField.TRANSMISSION_DATETIME_7, c.transmissionDt7);
        m.setField(IsoField.STAN_11, c.stan11);
        m.setField(IsoField.LOCAL_TIME_12, c.localTime12);
        m.setField(IsoField.LOCAL_DATE_13, c.localDate13);
        m.setField(IsoField.MERCHANT_TYPE_18, c.mcc18);
        m.setField(IsoField.ACQUIRER_ID_32, c.acquirerId32);
        m.setField(IsoField.RRN_37, c.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, c.terminalId41);
        m.setField(IsoField.MERCHANT_ID_42, c.merchantId42);
        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, c.merchantNameLocation43);
        m.setField(IsoField.CURRENCY_CODE_49, c.currency49);
        m.setField(IsoField.ORIGINAL_DATA_ELEMENTS_90, c.originalDataElements90);
        m.setField(IsoField.MAC_128, c.mac128);

        // Conditional + keep if reversal
        m.setField(IsoField.COUNTRY_CODE_19, c.country19);
        m.setField(IsoField.CARD_SEQ_23, c.cardSeq23);
        m.setField(IsoField.ICC_DATA_55, c.iccData55);
        m.setField(IsoField.RESERVED_PRIVATE_60, c.field60);

        IsoValidator.validateReversal(m);
        return m;
    }
}

