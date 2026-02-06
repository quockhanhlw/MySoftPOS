package com.example.mysoftpos.utils.config;

import com.example.mysoftpos.utils.config.ConfigManager;

import android.content.Context;
import android.content.SharedPreferences;

import android.content.res.AssetManager;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class ConfigManager {

    private static final String PREF_NAME = "softpos_config";

    // Keys match JSON keys for simplicity where possible, but mapped to Pref keys
    private static final String KEY_TRACE = "trace_number";

    private static final String KEY_MCC_18 = "mcc_18";
    private static final String KEY_ACQ_32 = "acquirer_id_32";
    private static final String KEY_FWD_33 = "forwarding_inst_33";
    private static final String KEY_TID_41 = "terminal_id_41";
    private static final String KEY_MID_42 = "merchant_id_42";

    // F43 Components
    private static final String KEY_BANK_43 = "bank_name_43";
    private static final String KEY_LOC_43 = "location_43";
    private static final String KEY_COUNTRY_TEXT_43 = "country_text_43";

    private static final String KEY_CURRENCY_49 = "currency_code_49";

    private static final String KEY_IP = "server_ip";
    private static final String KEY_PORT = "server_port";

    private final SharedPreferences prefs;
    private static ConfigManager instance;

    // In-Memory Defaults from JSON
    private String defMcc, defAcq, defFwd, defTid, defMid, defBank, defLoc, defCountryTxt, defCurr;

    private ConfigManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadDefaultConfig(context);
    }

    public static synchronized ConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConfigManager(context.getApplicationContext());
        }
        return instance;
    }

    // Transaction Defaults
    private String defAmount, mockPan, mockExpiry;

    // ISO Constants
    private String procPurchase, procBalance, posCondition, mccBalance;

    // Track 2 Map (Legacy support if needed)
    private java.util.Map<String, String> track2Map = new java.util.HashMap<>();

    // Test Case Config Map
    private java.util.Map<String, TestCaseConfig> testCaseMap = new java.util.HashMap<>();

    public static class TestCaseConfig {
        public String track2;
        public String pan;
        public String expiry;
        public String description;
    }

    public TestCaseConfig getTestCaseConfig(String de22) {
        if (testCaseMap.containsKey(de22)) {
            return testCaseMap.get(de22);
        }
        return null;
    }

    public String getTrack2(String de22) {
        if (testCaseMap.containsKey(de22)) {
            TestCaseConfig cfg = testCaseMap.get(de22);
            if (cfg.track2 != null)
                return cfg.track2;
        }
        if (track2Map.containsKey(de22)) {
            return track2Map.get(de22);
        }
        // Fallback or Default
        return track2Map.get("012");
    }

    // Getters for Defaults
    public String getDefaultAmount() {
        return defAmount;
    }

    public String getMockPan() {
        return mockPan;
    }

    public String getMockExpiry() {
        return mockExpiry;
    }

    // Getters for ISO Constants
    public String getProcessingCodePurchase() {
        return procPurchase;
    }

    public String getProcessingCodeBalance() {
        return procBalance;
    }

    public String getPosConditionCode() {
        return posCondition;
    }

    public String getMerchantTypeBalance() {
        return mccBalance;
    }

    private void loadDefaultConfig(Context context) {
        try (InputStream is = context.getAssets().open("pos_config.json")) {
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            String json = new String(buffer, StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(json);

            defMcc = obj.optString("merchant_type_18", "5411");
            defAcq = obj.optString("acquirer_id_32", "970406");
            defFwd = obj.optString("forwarding_inst_33", "970406");
            defTid = obj.optString("terminal_id_41", "AUTO0001");
            defMid = obj.optString("merchant_id_42", "MYSOFTPOSSHOP01");

            JSONObject f43 = obj.optJSONObject("merchant_name_43");
            if (f43 != null) {
                defBank = f43.optString("bank_name", "MYSOFTPOS BANK");
                defLoc = f43.optString("location", "HA NOI");
                defCountryTxt = f43.optString("country_code", "VNM");
            } else {
                defBank = "MYSOFTPOS BANK";
                defLoc = "HA NOI";
                defCountryTxt = "VNM";
            }

            defCurr = obj.optString("currency_code_49", "704");

            // Parse Test Case Definitions
            JSONObject tcObj = obj.optJSONObject("test_cases");
            if (tcObj != null) {
                java.util.Iterator<String> keys = tcObj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONObject item = tcObj.optJSONObject(key);
                    if (item != null) {
                        TestCaseConfig config = new TestCaseConfig();
                        config.track2 = item.optString("track2", null);
                        // If track2 is empty string in JSON, treat as null
                        if (config.track2 != null && config.track2.isEmpty())
                            config.track2 = null;

                        config.pan = item.optString("pan", null);
                        config.expiry = item.optString("expiry", null);
                        config.description = item.optString("description", "");
                        testCaseMap.put(key, config);
                    }
                }
            }

            // Legacy Track 2 parsing (Removed in favor of test_cases, but kept map for
            // safety if needed)
            // ...

            // Parse Transaction Defaults (Root Level in JSON)
            defAmount = obj.optString("default_amount", "000000100000");
            mockPan = obj.optString("mock_pan", "970418xxxxxx1234");
            mockExpiry = obj.optString("mock_expiry", "2512");
            defServerId = obj.optString("server_id", "01");

            // Parse Server Config
            defIp = obj.optString("server_ip", "10.0.0.1");
            defPort = obj.optInt("server_port", 8583);

            // Parse Admin Account
            JSONObject adminObj = obj.optJSONObject("admin_account");
            if (adminObj != null) {
                adminUser = adminObj.optString("username", "admin");
                adminPass = adminObj.optString("password", "admin123456789");
            } else {
                adminUser = "admin";
                adminPass = "admin123456789"; // Fallback
            }

            // Parse Currency Rules
            JSONObject currRules = obj.optJSONObject("currency_rules");
            if (currRules != null) {
                usdCode = currRules.optString("usd_code", "840");
                usdCountrySuffix = currRules.optString("usd_country_code", "840");
            } else {
                usdCode = "840";
                usdCountrySuffix = "840";
            }

            // Parse ISO Constants
            JSONObject isoConst = obj.optJSONObject("iso_constants");
            if (isoConst != null) {
                procPurchase = isoConst.optString("processing_code_purchase", "000000");
                procBalance = isoConst.optString("processing_code_balance", "300000");
                posCondition = isoConst.optString("pos_condition_code", "00");
                mccBalance = isoConst.optString("merchant_type_balance", "6011");
            } else {
                procPurchase = "000000";
                procBalance = "300000";
                posCondition = "00";
                mccBalance = "6011";
            }

        } catch (Exception e) {
            e.printStackTrace();
            // Fallbacks
            defMcc = "5411";
            defAcq = "970406";
            defFwd = "970406";
            defTid = "AUTO0001";
            defMid = "MYSOFTPOSSHOP01";
            defBank = "MYSOFTPOS BANK";
            defLoc = "HA NOI";
            defCountryTxt = "VNM";
            defCurr = "704";
            // Check IP defaults
            defIp = "10.145.54.206";
            defPort = 8583;

            // Fallbacks for new fields
            defAmount = "000000100000";

            mockPan = "970418xxxxxx1234";
            mockExpiry = "2512";
            procPurchase = "000000";
            procBalance = "300000";
            posCondition = "00";
            mccBalance = "6011";
        }
    }

    public synchronized String getAndIncrementTrace() {
        int trace = prefs.getInt(KEY_TRACE, 111200);
        if (trace < 111200) {
            trace = 111200;
        }
        int next = (trace >= 999999) ? 1 : trace + 1;
        prefs.edit().putInt(KEY_TRACE, next).apply();
        return String.format(Locale.ROOT, "%06d", trace);
    }

    private String defIp;
    private int defPort;

    public String getServerIp() {
        return defIp != null ? defIp : "10.145.54.206";
    }

    public int getServerPort() {
        return defPort > 0 ? defPort : 8583;
    }

    private String adminUser;
    private String adminPass;

    public String getAdminUsername() {
        return adminUser != null ? adminUser : "admin";
    }

    public String getAdminPassword() {
        return adminPass != null ? adminPass : "admin123456789";
    }

    // Currency Rules
    private String usdCode;
    private String usdCountrySuffix;

    public String getUsdCurrencyCode() {
        return usdCode != null ? usdCode : "840";
    }

    public String getUsdCountrySuffix() {
        return usdCountrySuffix != null ? usdCountrySuffix : "840";
    }

    // ...
    private String defServerId; // New field

    // ...
    public String getServerId() {
        return defServerId != null ? defServerId : "01";
    }

    // ...

    // Supported Fields - FORCE DEFAULTS (JSON) TO BYPASS STALE PREFS
    public String getMcc18() {
        return defMcc;
    }

    public String getAcquirerId32() {
        return defAcq;
    }

    public String getForwardingInst33() {
        return defFwd;
    }

    public String getTerminalId() {
        return defTid;
    }

    public String getMerchantId() {
        return defMid;
    }

    public String getCurrencyCode49() {
        return defCurr;
    }

    public String getMerchantName() {
        String bank = defBank;
        String loc = defLoc;
        String ctry = defCountryTxt;

        // Format strict 40 bytes
        return String.format("%-22s %-13s %s", bank, loc, ctry);
    }
}
