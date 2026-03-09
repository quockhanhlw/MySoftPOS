package com.example.mysoftpos.iso8583;

/**
 * Minimal validator retained for backward-compatible JVM tests.
 * The production flow validates at higher layers; these checks preserve the old test contract.
 */
public final class IsoValidator {
    private IsoValidator() {
    }

    public static void validatePurchase(IsoMessage message) {
        require(message != null, "message is required");
        require("0200".equals(message.getMti()), "Purchase request MTI must be 0200");
        require(hasExactDigits(message, IsoField.PAN_2, 13, 19), "Invalid PAN");
        require(hasExactDigits(message, IsoField.PROCESSING_CODE_3, 6, 6), "Invalid processing code");
        require("000000".equals(message.getField(IsoField.PROCESSING_CODE_3)), "Unsupported purchase processing code");
        require(hasExactDigits(message, IsoField.AMOUNT_4, 12, 12), "Invalid amount");
        require(hasExactDigits(message, IsoField.TRANSMISSION_DATETIME_7, 10, 10), "Invalid transmission date/time");
        require(hasExactDigits(message, IsoField.STAN_11, 6, 6), "Invalid STAN");
        require(hasExactDigits(message, IsoField.LOCAL_TIME_12, 6, 6), "Invalid local time");
        require(hasExactDigits(message, IsoField.LOCAL_DATE_13, 4, 4), "Invalid local date");
        require(hasExactDigits(message, IsoField.MERCHANT_TYPE_18, 4, 4), "Invalid MCC");
        require(hasExactDigits(message, IsoField.POS_ENTRY_MODE_22, 3, 3), "Invalid POS entry mode");
        require(hasExactDigits(message, IsoField.POS_CONDITION_CODE_25, 2, 2), "Invalid POS condition code");
        require(isDigits(message.getField(IsoField.ACQUIRER_ID_32)), "Invalid acquirer ID");
        require(message.getField(IsoField.RRN_37) != null && message.getField(IsoField.RRN_37).length() == 12,
                "Invalid RRN");
        require(message.getField(IsoField.TERMINAL_ID_41) != null && message.getField(IsoField.TERMINAL_ID_41).length() == 8,
                "Invalid terminal ID");
        require(message.getField(IsoField.MERCHANT_ID_42) != null && message.getField(IsoField.MERCHANT_ID_42).length() == 15,
                "Invalid merchant ID");
        require(message.getField(IsoField.MERCHANT_NAME_LOCATION_43) != null
                && message.getField(IsoField.MERCHANT_NAME_LOCATION_43).length() == 40,
                "Invalid merchant name/location");
        require(hasExactDigits(message, IsoField.CURRENCY_CODE_49, 3, 3), "Invalid currency code");
    }

    public static void validatePurchaseResponse(IsoMessage response) {
        require(response != null, "response is required");
        require("0210".equals(response.getMti()), "Purchase response MTI must be 0210");
        require(hasExactDigits(response, IsoField.RESPONSE_CODE_39, 2, 2), "Invalid response code");
    }

    private static boolean hasExactDigits(IsoMessage message, int field, int min, int max) {
        String value = message.getField(field);
        return value != null && value.length() >= min && value.length() <= max && isDigits(value);
    }

    private static boolean isDigits(String value) {
        return value != null && value.matches("\\d+");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}

