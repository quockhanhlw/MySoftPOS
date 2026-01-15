package com.example.mysoftpos.iso8583;

/** ISO8583 field numbers used in this project. */
public final class IsoField {
    private IsoField() {}

    public static final int PAN_2 = 2;
    public static final int PROCESSING_CODE_3 = 3;
    public static final int AMOUNT_4 = 4;
    public static final int TRANSMISSION_DATETIME_7 = 7;
    public static final int STAN_11 = 11;
    public static final int LOCAL_TIME_12 = 12;
    public static final int LOCAL_DATE_13 = 13;
    public static final int EXPIRATION_DATE_14 = 14;
    public static final int MERCHANT_TYPE_18 = 18;
    public static final int COUNTRY_CODE_19 = 19;
    public static final int POS_ENTRY_MODE_22 = 22;
    public static final int CARD_SEQ_23 = 23;
    public static final int POS_CONDITION_CODE_25 = 25;
    public static final int ACQUIRER_ID_32 = 32;
    public static final int TRACK2_35 = 35;
    public static final int RRN_37 = 37;
    public static final int AUTH_CODE_38 = 38;
    public static final int TERMINAL_ID_41 = 41;
    public static final int MERCHANT_ID_42 = 42;
    public static final int MERCHANT_NAME_LOCATION_43 = 43;
    public static final int CURRENCY_CODE_49 = 49;
    public static final int PIN_BLOCK_52 = 52;
    public static final int ICC_DATA_55 = 55;
    public static final int RESERVED_PRIVATE_60 = 60;
    public static final int ORIGINAL_DATA_ELEMENTS_90 = 90;
    public static final int MAC_128 = 128;

    public static void checkValid(int field) {
        if (field < 1 || field > 128) {
            throw new IllegalArgumentException("Invalid ISO field: " + field);
        }
    }
}
