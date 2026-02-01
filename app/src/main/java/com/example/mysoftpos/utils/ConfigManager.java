package com.example.mysoftpos.utils;

import android.content.Context;
import android.content.SharedPreferences;

import android.content.res.AssetManager;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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

    // Track 2 Map
    private java.util.Map<String, String> track2Map = new java.util.HashMap<>();

    public String getTrack2(String de22) {
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

            // Parse Track 2 Definitions
            JSONObject t2Obj = obj.optJSONObject("track2_definitions");
            if (t2Obj != null) {
                java.util.Iterator<String> keys = t2Obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String val = t2Obj.optString(key);
                    track2Map.put(key, val);
                }
            }

            // Parse Transaction Defaults
            JSONObject txnDef = obj.optJSONObject("transaction_defaults");
            if (txnDef != null) {
                defAmount = txnDef.optString("default_amount", "000000100000");
                mockPan = txnDef.optString("mock_pan", "970418xxxxxx1234");
                mockExpiry = txnDef.optString("mock_expiry", "2512");
                defServerId = txnDef.optString("server_id", "01");
            } else {
                defAmount = "000000100000";
                mockPan = "970418xxxxxx1234";
                mockExpiry = "2512";
                defServerId = "01";
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
        int trace = prefs.getInt(KEY_TRACE, 1);
        int next = (trace >= 999999) ? 1 : trace + 1;
        prefs.edit().putInt(KEY_TRACE, next).apply();
        return String.format("%06d", trace);
    }

    // FORCE CORRECT IP - Bypass SharedPreferences cache
    public String getServerIp() {
        return "10.145.54.175";
    }

    public int getServerPort() {
        return 8583;
    } // prefs.getInt(KEY_PORT, 8583);

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
