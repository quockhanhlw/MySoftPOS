package com.example.mysoftpos.iso8583.spec;
import com.example.mysoftpos.iso8583.spec.IsoSpec;
import com.example.mysoftpos.iso8583.spec.IsoField;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * ISO8583 packing rules for this project (default assumptions).
 *
 * This is NOT a full WAY4 packager. It's a safe starting point for local testing:
 * - MTI: ASCII 4
 * - Bitmap: binary 8 or 16 bytes
 * - Fields: ASCII, fixed/LLVAR/LLLVAR.
 */
public final class IsoSpec {
    private IsoSpec() {}

    public static final Charset CHARSET = StandardCharsets.US_ASCII;

    public enum LenType {
        FIXED,
        LLVAR,
        LLLVAR
    }

    public enum ContentType {
        /** Printable ASCII. */
        ASCII,
        /** Raw bytes in hex string form ("0A1B...") that will be converted to bytes. */
        HEX_BYTES
    }

    public static final class FieldDef {
        public final int field;
        public final LenType lenType;
        public final int maxLen;
        public final ContentType contentType;

        public FieldDef(int field, LenType lenType, int maxLen, ContentType contentType) {
            this.field = field;
            this.lenType = lenType;
            this.maxLen = maxLen;
            this.contentType = contentType;
        }
    }

    /**
     * Minimal definitions ONLY for fields used in your rules.
     * If a field isn't defined here, packer will throw, forcing us to add it explicitly.
     */
    public static FieldDef def(int f) {
        switch (f) {
            case IsoField.PAN_2:
                return new FieldDef(2, LenType.LLVAR, 19, ContentType.ASCII);
            case IsoField.PROCESSING_CODE_3:
                return new FieldDef(3, LenType.FIXED, 6, ContentType.ASCII);
            case IsoField.AMOUNT_4:
                return new FieldDef(4, LenType.FIXED, 12, ContentType.ASCII);
            case IsoField.TRANSMISSION_DATETIME_7:
                return new FieldDef(7, LenType.FIXED, 10, ContentType.ASCII);
            case IsoField.STAN_11:
                return new FieldDef(11, LenType.FIXED, 6, ContentType.ASCII);
            case IsoField.LOCAL_TIME_12:
                return new FieldDef(12, LenType.FIXED, 6, ContentType.ASCII);
            case IsoField.LOCAL_DATE_13:
                return new FieldDef(13, LenType.FIXED, 4, ContentType.ASCII);
            case IsoField.EXPIRATION_DATE_14:
                return new FieldDef(14, LenType.FIXED, 4, ContentType.ASCII);
            case IsoField.MERCHANT_TYPE_18:
                return new FieldDef(18, LenType.FIXED, 4, ContentType.ASCII);
            case IsoField.COUNTRY_CODE_19:
                return new FieldDef(19, LenType.FIXED, 3, ContentType.ASCII);
            case IsoField.POS_ENTRY_MODE_22:
                return new FieldDef(22, LenType.FIXED, 3, ContentType.ASCII);
            case IsoField.CARD_SEQ_23:
                return new FieldDef(23, LenType.FIXED, 3, ContentType.ASCII);
            case IsoField.POS_CONDITION_CODE_25:
                return new FieldDef(25, LenType.FIXED, 2, ContentType.ASCII);
            case IsoField.ACQUIRER_ID_32:
                return new FieldDef(32, LenType.LLVAR, 11, ContentType.ASCII);
            case IsoField.TRACK2_35:
                return new FieldDef(35, LenType.LLVAR, 37, ContentType.ASCII);
            case IsoField.RRN_37:
                return new FieldDef(37, LenType.FIXED, 12, ContentType.ASCII);
            case IsoField.AUTH_CODE_38:
                return new FieldDef(38, LenType.FIXED, 6, ContentType.ASCII);
            case IsoField.TERMINAL_ID_41:
                return new FieldDef(41, LenType.FIXED, 8, ContentType.ASCII);
            case IsoField.MERCHANT_ID_42:
                return new FieldDef(42, LenType.FIXED, 15, ContentType.ASCII);
            case IsoField.MERCHANT_NAME_LOCATION_43:
                return new FieldDef(43, LenType.FIXED, 40, ContentType.ASCII);
            case IsoField.CURRENCY_CODE_49:
                return new FieldDef(49, LenType.FIXED, 3, ContentType.ASCII);
            case IsoField.PIN_BLOCK_52:
                return new FieldDef(52, LenType.FIXED, 16, ContentType.HEX_BYTES);
            case IsoField.ICC_DATA_55:
                return new FieldDef(55, LenType.LLLVAR, 999, ContentType.HEX_BYTES);
            case IsoField.RESERVED_PRIVATE_60:
                // NAPAS: ans...060, LLLVAR
                return new FieldDef(60, LenType.LLLVAR, 60, ContentType.ASCII);
            case IsoField.ORIGINAL_DATA_ELEMENTS_90:
                return new FieldDef(90, LenType.FIXED, 42, ContentType.ASCII);
            case IsoField.MAC_128:
                return new FieldDef(128, LenType.FIXED, 16, ContentType.HEX_BYTES);
            default:
                throw new IllegalArgumentException(
                        "No FieldDef for field " + f + ". Add it to IsoSpec.def().");
        }
    }
}







