package com.example.mysoftpos.utils;

/**
 * Centralized Intent extra key constants.
 * Eliminates hardcoded strings scattered across Activities.
 */
public final class IntentKeys {

    private IntentKeys() {
    }

    // User / Auth
    public static final String USERNAME = "USERNAME";
    public static final String USER_ROLE = "USER_ROLE";

    // Transaction
    public static final String TXN_TYPE = "TXN_TYPE";
    public static final String AMOUNT = "AMOUNT";
    public static final String CURRENCY = "CURRENCY";
    public static final String CURRENCY_CODE = "CURRENCY_CODE";
    public static final String COUNTRY_CODE = "COUNTRY_CODE";
    public static final String SUCCESS = "SUCCESS";
    public static final String MASKED_PAN = "MASKED_PAN";
    public static final String TXN_DATE = "TXN_DATE";
    public static final String TXN_ID = "TXN_ID";
    public static final String BALANCE_TYPE = "BALANCE_TYPE";

    // ISO Data
    public static final String RAW_RESPONSE = "RAW_RESPONSE";
    public static final String RAW_REQUEST = "RAW_REQUEST";
    public static final String PAN = "PAN";
    public static final String EXPIRY = "EXPIRY";
    public static final String PIN_BLOCK = "PIN_BLOCK";
    public static final String TRACK2 = "TRACK2";
    public static final String DE22 = "DE22";

    // Test Suite
    public static final String CHANNEL = "CHANNEL";
    public static final String SCHEME = "SCHEME";
    public static final String PERF_MODE = "PERF_MODE";
    public static final String DESC = "DESC";
    public static final String SCENARIOS = "SCENARIOS";
    public static final String SELECTED_SCENARIOS = "SELECTED_SCENARIOS";
    public static final String SUITE_NAME = "SUITE_NAME";
}
