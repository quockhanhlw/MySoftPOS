package com.example.mysoftpos.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ConfigManager {

    private static final String TAG = "ConfigManager";
    private static final String PREF_NAME = "softpos_config";
    private static final String KEY_TRACE = "trace_number";

    private final SharedPreferences prefs;
    private static ConfigManager instance;

    // Configuration Values (Loaded from JSON)
    private String mcc18, acqId32, fwdInst33, tid41, mid42, curr49;
    private String bankName, location, countryCode;
    private String serverIp, serverId;
    private int serverPort;

    private ConfigManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadConfig(context);
    }

    public static synchronized ConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConfigManager(context.getApplicationContext());
        }
        return instance;
    }

    private void loadConfig(Context context) {
        // 1. Load POS Config (App Params) from pos_config.json
        try {
            JSONObject pos = loadJsonFromAsset(context, "pos_config.json");

            this.mcc18 = pos.optString("merchant_type_18", "");
            this.acqId32 = pos.optString("acquirer_id_32", "");
            this.fwdInst33 = pos.optString("forwarding_inst_33", "");
            this.tid41 = pos.optString("terminal_id_41", "");
            this.mid42 = pos.optString("merchant_id_42", "");
            this.curr49 = pos.optString("currency_code_49", "");

            JSONObject f43 = pos.optJSONObject("merchant_name_43");
            if (f43 != null) {
                this.bankName = f43.optString("bank_name", "");
                this.location = f43.optString("location", "");
                this.countryCode = f43.optString("country_code", "");
            } else {
                this.bankName = "";
                this.location = "";
                this.countryCode = "";
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load pos_config.json", e);
        }

        // 2. Load Server Config (Network Params) from server_config.json
        try {
            JSONObject server = loadJsonFromAsset(context, "server_config.json");

            this.serverIp = server.optString("server_ip", "");
            this.serverPort = server.optInt("server_port", 8583);
            this.serverId = server.optString("server_id", "");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load server_config.json", e);
        }
    }

    /**
     * Helper to read a JSON file from Assets.
     */
    private JSONObject loadJsonFromAsset(Context context, String filename) throws Exception {
        try (InputStream is = context.getAssets().open(filename)) {
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            String json = new String(buffer, StandardCharsets.UTF_8);
            return new JSONObject(json);
        }
    }

    public synchronized String getAndIncrementTrace() {
        int trace = prefs.getInt(KEY_TRACE, 1);
        int next = (trace >= 999999) ? 1 : trace + 1;
        prefs.edit().putInt(KEY_TRACE, next).apply();
        return String.format("%06d", trace);
    }

    // --- Getters ---

    public String getServerIp() {
        return serverIp;
    }

    public int getServerPort() {
        return serverPort;
    }

    public String getServerId() {
        return serverId;
    }

    public String getMcc18() {
        return mcc18;
    }

    public String getAcquirerId32() {
        return acqId32;
    }

    public String getForwardingInst33() {
        return fwdInst33;
    }

    public String getTerminalId() {
        return tid41;
    }

    public String getMerchantId() {
        return mid42;
    }

    public String getCurrencyCode49() {
        return curr49;
    }

    public String getMerchantName() {
        if (bankName == null)
            return "Unknown";
        // Format strict 40 bytes
        return String.format("%-22s %-13s %s", bankName, location, countryCode);
    }
}
