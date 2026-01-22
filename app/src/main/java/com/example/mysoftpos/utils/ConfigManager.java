package com.example.mysoftpos.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class ConfigManager {

    private static final String PREF_NAME = "softpos_config";
    private static final String KEY_TRACE = "trace_number";
    private static final String KEY_IP = "server_ip";
    private static final String KEY_PORT = "server_port";
    private static final String KEY_TID = "terminal_id";
    private static final String KEY_MID = "merchant_id";
    private static final String KEY_SERVER_ID = "server_id";

    private final SharedPreferences prefs;
    private static ConfigManager instance;

    private ConfigManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized ConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConfigManager(context.getApplicationContext());
        }
        return instance;
    }

    public synchronized String getAndIncrementTrace() {
        int trace = prefs.getInt(KEY_TRACE, 1);
        int next = (trace >= 999999) ? 1 : trace + 1;
        prefs.edit().putInt(KEY_TRACE, next).apply();
        return String.format("%06d", trace);
    }
    
    public String getServerIp() {
        return prefs.getString(KEY_IP, "192.168.1.15"); // Default Mock
    }
    
    public int getServerPort() {
        return prefs.getInt(KEY_PORT, 8888); 
    }
    
    public String getTerminalId() {
        return prefs.getString(KEY_TID, "TESTTID1");
    }
    
    public String getMerchantId() {
        return prefs.getString(KEY_MID, "TESTMERCHANT123");
    }
    
    public String getServerId() {
        return prefs.getString(KEY_SERVER_ID, "01");
    }
}
