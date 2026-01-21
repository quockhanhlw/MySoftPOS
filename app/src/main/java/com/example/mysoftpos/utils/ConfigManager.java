package com.example.mysoftpos.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Manages ISO8583 Parameters (TID, MID, Trace Number).
 * Implements Singleton pattern with persistent storage.
 */
public class ConfigManager {

    private static final String PREFS_NAME = "MySoftPosParams";
    private static final String KEY_TRACE = "trace_number";
    private static final String KEY_TID = "terminal_id_counter";
    private static final String KEY_MID = "merchant_id_counter";
    private static final String KEY_SERVER_ID = "server_id";

    private static ConfigManager instance;
    private final SharedPreferences prefs;

    private ConfigManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized ConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConfigManager(context);
        }
        return instance;
    }
    
    // --- Getters (Current Values) ---

    /**
     * Get current System Trace Audit Number (STAN).
     * Default: 000001
     */
    public String getTrace() {
        int val = prefs.getInt(KEY_TRACE, 1); 
        return String.format(Locale.US, "%06d", val);
    }

    /**
     * Get current Terminal ID (TID).
     * Default: 00000001
     */
    public String getTid() {
        int val = prefs.getInt(KEY_TID, 1);
        return String.format(Locale.US, "%08d", val);
    }

    /**
     * Get current Merchant ID (MID).
     * Default: MERCHANT0000001
     */
    public String getMid() {
        int val = prefs.getInt(KEY_MID, 1);
        return "MERCHANT" + String.format(Locale.US, "%07d", val);
    }

    /**
     * Get current Server ID.
     * Parametrized for App POS.
     * Default: 01
     */
    public String getServerId() {
        return prefs.getString(KEY_SERVER_ID, "01");
    }

    public void setServerId(String serverId) {
        prefs.edit().putString(KEY_SERVER_ID, serverId).apply();
    }

    // --- Actions (Increment) ---

    /**
     * Increment Trace Number. Wraps at 999999.
     */
    public synchronized void incrementTrace() {
        int current = prefs.getInt(KEY_TRACE, 1);
        int next = current + 1;
        if (next > 999999) next = 1;
        prefs.edit().putInt(KEY_TRACE, next).apply();
    }

    /**
     * Atomically retrieves the current Trace Number and increments the counter for the next transaction.
     * Ensures persistence immediately.
     * 
     * @return The current trace number (6 digits) to be used for the current transaction.
     */
    public synchronized String getAndIncrementTrace() {
        int current = prefs.getInt(KEY_TRACE, 1);
        
        // Calculate Next
        int next = current + 1;
        if (next > 999999) next = 1;
        
        // Save Next immediately (Synchronous commit to ensure safety against crashes/races)
        prefs.edit().putInt(KEY_TRACE, next).commit();
        
        // Return Current
        return String.format(Locale.US, "%06d", current);
    }

    /**
     * Increment TID and MID (Testing purpose).
     */
    public synchronized void incrementTidAndMid() {
        // TID
        int tid = prefs.getInt(KEY_TID, 1);
        int nextTid = tid + 1;
        if (nextTid > 99999999) nextTid = 1;
        
        // MID
        int mid = prefs.getInt(KEY_MID, 1);
        int nextMid = mid + 1;
        if (nextMid > 9999999) nextMid = 1;
        
        prefs.edit()
                .putInt(KEY_TID, nextTid)
                .putInt(KEY_MID, nextMid)
                .apply();
    }
}
