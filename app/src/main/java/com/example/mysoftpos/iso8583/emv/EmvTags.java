package com.example.mysoftpos.iso8583.emv;
import com.example.mysoftpos.iso8583.emv.EmvTags;

/**
 * NAPAS EMV Tag Constants.
 */
public final class EmvTags {
    private EmvTags() {
    }

    public static final String AC = "9F26"; // Application Cryptogram
    public static final String CID = "9F27"; // Cryptogram Information Data
    public static final String IAD = "9F10"; // Issuer Application Data
    public static final String UNPREDICTABLE_NUM = "9F37";
    public static final String ATC = "9F36"; // Application Transaction Counter
    public static final String TVR = "95"; // Terminal Verification Results
    public static final String TXN_DATE = "9A"; // Transaction Date
    public static final String TXN_TYPE = "9C"; // Transaction Type
    public static final String TXN_CURRENCY = "5F2A";
    public static final String AIP = "82"; // Application Interchange Profile
    public static final String PAN_SEQ_NUM = "5F34"; // PAN Sequence Number
}






