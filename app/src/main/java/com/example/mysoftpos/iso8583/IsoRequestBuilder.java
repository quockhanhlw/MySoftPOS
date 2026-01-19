package com.example.mysoftpos.iso8583;

import java.util.Locale;

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

        // Required (NAPAS: MTI 0200 + F3 000000)
        m.setField(IsoField.PAN_2, c.pan2);
        m.setField(IsoField.PROCESSING_CODE_3, "000000");
        m.setField(IsoField.AMOUNT_4, normalizeAmount12(c.amount4));
        m.setField(IsoField.TRANSMISSION_DATETIME_7, c.transmissionDt7);
        m.setField(IsoField.STAN_11, normalizeStan6(c.stan11));
        m.setField(IsoField.LOCAL_TIME_12, c.localTime12);
        m.setField(IsoField.LOCAL_DATE_13, c.localDate13);
        m.setField(IsoField.MERCHANT_TYPE_18, c.mcc18);
        m.setField(IsoField.POS_ENTRY_MODE_22, c.posEntryMode22);
        m.setField(IsoField.POS_CONDITION_CODE_25, c.posCondition25);
        m.setField(IsoField.ACQUIRER_ID_32, c.acquirerId32);
        m.setField(IsoField.RRN_37, c.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, TransactionContext.formatTid8(c.terminalId41));
        m.setField(IsoField.MERCHANT_ID_42, TransactionContext.formatMid15(c.merchantId42));
        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, c.merchantNameLocation43);
        m.setField(IsoField.CURRENCY_CODE_49, TransactionContext.defaultCurrencyVND());

        // Conditional
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
     * Build Void request (NAPAS Void flow).
     *
     * Per provided flow diagram:
     * - MTI = 0420 (Void Advice)
     * - Response MTI = 0430
     *
     * Business rule in this project:
     * - F3 Processing Code MUST be 020000
     */
    public static IsoMessage buildVoid(TransactionContext c) {
        // NAPAS void advice MTI
        IsoMessage m = new IsoMessage("0420");

        m.setField(IsoField.PAN_2, c.pan2);
        m.setField(IsoField.PROCESSING_CODE_3, "020000");
        m.setField(IsoField.AMOUNT_4, normalizeAmount12(c.amount4));
        m.setField(IsoField.TRANSMISSION_DATETIME_7, c.transmissionDt7);
        m.setField(IsoField.STAN_11, normalizeStan6(c.stan11));
        m.setField(IsoField.LOCAL_TIME_12, c.localTime12);
        m.setField(IsoField.LOCAL_DATE_13, c.localDate13);
        m.setField(IsoField.MERCHANT_TYPE_18, c.mcc18);
        m.setField(IsoField.ACQUIRER_ID_32, c.acquirerId32);
        m.setField(IsoField.RRN_37, c.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, TransactionContext.formatTid8(c.terminalId41));
        m.setField(IsoField.MERCHANT_ID_42, TransactionContext.formatMid15(c.merchantId42));
        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, c.merchantNameLocation43);
        m.setField(IsoField.CURRENCY_CODE_49, c.currency49);
        m.setField(IsoField.ORIGINAL_DATA_ELEMENTS_90, c.originalDataElements90);
        m.setField(IsoField.MAC_128, c.mac128);

        // Conditional
        m.setField(IsoField.COUNTRY_CODE_19, c.country19);
        m.setField(IsoField.CARD_SEQ_23, c.cardSeq23);
        m.setField(IsoField.ICC_DATA_55, c.iccData55);
        m.setField(IsoField.RESERVED_PRIVATE_60, c.field60);

        IsoValidator.validateReversal(m);
        return m;
    }

    /**
     * Build Reversal request (NAPAS Reversal Advice flow).
     *
     * Per provided flow diagram (initiated from ACQ):
     * - Reversal Advice Request MTI = 0420
     * - Reversal Advice Response MTI = 0430
     */
    public static IsoMessage buildReversal(TransactionContext c) {
        // NAPAS reversal advice MTI
        IsoMessage m = new IsoMessage("0420");

        m.setField(IsoField.PAN_2, c.pan2);
        // For reversal advice, allow using original processing code if provided, else default purchase code
        m.setField(IsoField.PROCESSING_CODE_3, normalizeProcCode6OrDefault(c.processingCode3, "000000"));
        m.setField(IsoField.AMOUNT_4, normalizeAmount12(c.amount4));
        m.setField(IsoField.TRANSMISSION_DATETIME_7, c.transmissionDt7);
        m.setField(IsoField.STAN_11, normalizeStan6(c.stan11));
        m.setField(IsoField.LOCAL_TIME_12, c.localTime12);
        m.setField(IsoField.LOCAL_DATE_13, c.localDate13);
        m.setField(IsoField.MERCHANT_TYPE_18, c.mcc18);
        m.setField(IsoField.ACQUIRER_ID_32, c.acquirerId32);
        m.setField(IsoField.RRN_37, c.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, TransactionContext.formatTid8(c.terminalId41));
        m.setField(IsoField.MERCHANT_ID_42, TransactionContext.formatMid15(c.merchantId42));
        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, c.merchantNameLocation43);
        m.setField(IsoField.CURRENCY_CODE_49, c.currency49);
        m.setField(IsoField.ORIGINAL_DATA_ELEMENTS_90, c.originalDataElements90);
        m.setField(IsoField.MAC_128, c.mac128);

        // Conditional
        m.setField(IsoField.COUNTRY_CODE_19, c.country19);
        m.setField(IsoField.CARD_SEQ_23, c.cardSeq23);
        m.setField(IsoField.ICC_DATA_55, c.iccData55);
        m.setField(IsoField.RESERVED_PRIVATE_60, c.field60);

        IsoValidator.validateReversal(m);
        return m;
    }

    /**
     * @deprecated Use {@link #buildVoid(TransactionContext)} or {@link #buildReversal(TransactionContext)}.
     */
    @Deprecated
    public static IsoMessage buildVoidReversal(TransactionContext c) {
        return buildReversal(c);
    }

    private static String normalizeAmount12(String f4) {
        if (f4 == null) return null;
        String v = f4.trim();
        if (v.matches("\\d{12}")) return v;
        // if user passed digits, format
        if (v.matches("\\d{1,12}")) return TransactionContext.formatAmount12(v);
        return v;
    }

    private static String normalizeStan6(String f11) {
        if (f11 == null) return null;
        String v = f11.trim();
        if (v.matches("\\d{6}")) return v;
        if (v.matches("\\d{1,6}")) return TransactionContext.formatStan6(v);
        return v;
    }

    private static String normalizeProcCode6OrDefault(String v, String def) {
        if (v == null || v.trim().isEmpty()) return def;
        String s = v.trim();
        if (s.matches("\\d{6}")) return s;
        if (s.matches("\\d{1,6}")) {
            return String.format(Locale.US, "%06d", Integer.parseInt(s));
        }
        return def;
    }
}
