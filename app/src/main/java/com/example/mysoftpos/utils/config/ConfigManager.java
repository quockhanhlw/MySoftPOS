package com.example.mysoftpos.utils.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Centralized configuration manager.
 * Loads defaults from pos_config.json, allows runtime overrides via
 * SharedPreferences.
 */
public class ConfigManager {

    private static final String TAG = "ConfigManager";
    private static final String PREF_NAME = "softpos_config";
    private static final String KEY_TRACE = "trace_number";
    private static final String KEY_IP = "server_ip";
    private static final String KEY_PORT = "server_port";
    private static final String KEY_TIMEOUT = "timeout";
    private static final String KEY_TID = "terminal_id";
    private static final String KEY_MID = "merchant_id";
    private static final String KEY_ENCRYPT_PIN = "encrypt_pin";

    private static final int DEFAULT_TRACE_START = 111300;

    private final SharedPreferences prefs;
    private static ConfigManager instance;

    // Cached values from JSON
    private String serverIp, serverId;
    private int serverPort, timeoutMs;
    private String terminalId, merchantId, merchantType;
    private String bankName, location, countryCode;
    private String acquirerId, forwardingInst, currencyCode, posConditionCode;
    private String procPurchase, procBalance, procVoid;
    private String adminUser, adminPass;
    private String usdCode, usdCountry;
    private int amountMax, panMinLen, panMaxLen;

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
        try (InputStream is = context.getAssets().open("pos_config.json")) {
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            JSONObject config = new JSONObject(new String(buffer, StandardCharsets.UTF_8));

            // Server
            JSONObject server = config.optJSONObject("server");
            if (server != null) {
                serverIp = server.optString("ip", "10.145.54.206");
                serverPort = server.optInt("port", 8583);
                timeoutMs = server.optInt("timeout_ms", 30000);
                serverId = server.optString("server_id", "01");
            }

            // Terminal
            JSONObject terminal = config.optJSONObject("terminal");
            if (terminal != null) {
                terminalId = terminal.optString("terminal_id", "AUTO0001");
                merchantId = terminal.optString("merchant_id", "MYSOFTPOSSHOP01");
                merchantType = terminal.optString("merchant_type", "5411");

                JSONObject merchant = terminal.optJSONObject("merchant_name");
                if (merchant != null) {
                    bankName = merchant.optString("bank_name", "MYSOFTPOS BANK");
                    location = merchant.optString("location", "HA NOI");
                    countryCode = merchant.optString("country_code", "VNM");
                }
            }

            // ISO Fields
            JSONObject isoFields = config.optJSONObject("iso_fields");
            if (isoFields != null) {
                acquirerId = isoFields.optString("acquirer_id_32", "970488");
                forwardingInst = isoFields.optString("forwarding_inst_33", "970418");
                currencyCode = isoFields.optString("currency_code_49", "704");
                posConditionCode = isoFields.optString("pos_condition_code_25", "00");
            }

            // Processing Codes
            JSONObject procCodes = config.optJSONObject("processing_codes");
            if (procCodes != null) {
                procPurchase = procCodes.optString("purchase", "000000");
                procBalance = procCodes.optString("balance_inquiry", "300000");
                procVoid = procCodes.optString("void", "000000");
            }

            // Currency Rules
            JSONObject currRules = config.optJSONObject("currency_rules");
            if (currRules != null) {
                JSONObject usd = currRules.optJSONObject("usd");
                if (usd != null) {
                    usdCode = usd.optString("code", "840");
                    usdCountry = usd.optString("country", "840");
                }
            }

            // Admin
            JSONObject admin = config.optJSONObject("admin_account");
            if (admin != null) {
                adminUser = admin.optString("username", "admin");
                adminPass = admin.optString("password", "admin123456789");
            }

            // Validation
            JSONObject validation = config.optJSONObject("validation");
            if (validation != null) {
                panMinLen = validation.optInt("pan_min_length", 13);
                panMaxLen = validation.optInt("pan_max_length", 19);
                amountMax = validation.optInt("amount_max", 100000000);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to load config", e);
            setDefaults();
        }
    }

    private void setDefaults() {
        serverIp = "10.145.54.206";
        serverPort = 8583;
        timeoutMs = 30000;
        serverId = "01";
        terminalId = "AUTO0001";
        merchantId = "MYSOFTPOSSHOP01";
        merchantType = "5411";
        bankName = "MYSOFTPOS BANK";
        location = "HA NOI";
        countryCode = "VNM";
        acquirerId = "970488";
        forwardingInst = "970418";
        currencyCode = "704";
        posConditionCode = "00";
        procPurchase = "000000";
        procBalance = "300000";
        procVoid = "000000";
        adminUser = "admin";
        adminPass = "admin123456789";
        usdCode = "840";
        usdCountry = "840";
        panMinLen = 13;
        panMaxLen = 19;
        amountMax = 100000000;
    }

    // ==================== TRACE NUMBER ====================
    public synchronized String getAndIncrementTrace() {
        int trace = prefs.getInt(KEY_TRACE, DEFAULT_TRACE_START);
        if (trace < DEFAULT_TRACE_START)
            trace = DEFAULT_TRACE_START;
        int next = (trace >= 999999) ? 1 : trace + 1;
        prefs.edit().putInt(KEY_TRACE, next).apply();
        return String.format(Locale.ROOT, "%06d", trace);
    }

    // ==================== SERVER ====================
    public String getServerIp() {
        return prefs.getString(KEY_IP, serverIp);
    }

    public void setServerIp(String ip) {
        prefs.edit().putString(KEY_IP, ip).apply();
    }

    public int getServerPort() {
        try {
            return prefs.getInt(KEY_PORT, serverPort);
        } catch (ClassCastException e) {
            try {
                String val = prefs.getString(KEY_PORT, String.valueOf(serverPort));
                if (val != null) {
                    int p = Integer.parseInt(val);
                    setServerPort(p); // Self-heal
                    return p;
                }
            } catch (Exception ignored) {
            }
            return serverPort;
        }
    }

    public void setServerPort(int port) {
        prefs.edit().putInt(KEY_PORT, port).apply();
    }

    public int getTimeout() {
        try {
            return prefs.getInt(KEY_TIMEOUT, timeoutMs);
        } catch (ClassCastException e) {
            try {
                String val = prefs.getString(KEY_TIMEOUT, String.valueOf(timeoutMs));
                if (val != null) {
                    int t = Integer.parseInt(val);
                    setTimeout(t); // Self-heal
                    return t;
                }
            } catch (Exception ignored) {
            }
            return timeoutMs;
        }
    }

    public void setTimeout(int ms) {
        prefs.edit().putInt(KEY_TIMEOUT, ms).apply();
    }

    public String getServerId() {
        return serverId;
    }

    // ==================== TERMINAL ====================
    public String getTerminalId() {
        return prefs.getString(KEY_TID, terminalId);
    }

    public void setTerminalId(String tid) {
        prefs.edit().putString(KEY_TID, tid).apply();
    }

    public String getMerchantId() {
        return prefs.getString(KEY_MID, merchantId);
    }

    public void setMerchantId(String mid) {
        prefs.edit().putString(KEY_MID, mid).apply();
    }

    public String getMcc18() {
        return merchantType;
    }

    public String getMerchantName() {
        return String.format(Locale.ROOT, "%-22s %-13s %s", bankName, location, countryCode);
    }

    // ==================== ISO FIELDS ====================
    public String getAcquirerId32() {
        return acquirerId;
    }

    public String getForwardingInst33() {
        return forwardingInst;
    }

    public String getCurrencyCode49() {
        return currencyCode;
    }

    public String getPosConditionCode() {
        return posConditionCode;
    }

    // ==================== PROCESSING CODES ====================
    public String getProcessingCodePurchase() {
        return procPurchase;
    }

    public String getProcessingCodeBalance() {
        return procBalance;
    }

    public String getProcessingCodeVoid() {
        return procVoid;
    }

    // ==================== CURRENCY ====================
    public String getUsdCurrencyCode() {
        return usdCode;
    }

    public String getUsdCountrySuffix() {
        return usdCountry;
    }

    // ==================== ADMIN ====================
    public String getAdminUsername() {
        return adminUser;
    }

    public String getAdminPassword() {
        return adminPass;
    }

    // ==================== SECURITY ====================
    public boolean isPinEncryptionEnabled() {
        return prefs.getBoolean(KEY_ENCRYPT_PIN, true);
    }

    public void setPinEncryptionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENCRYPT_PIN, enabled).apply();
    }

    // ==================== VALIDATION ====================
    public int getPanMinLength() {
        return panMinLen;
    }

    public int getPanMaxLength() {
        return panMaxLen;
    }

    public int getAmountMax() {
        return amountMax;
    }

    // ==================== DEFAULTS (Hardcoded for backward compatibility)
    // ====================
    public String getDefaultAmount() {
        return "100000";
    }

    public String getMockPan() {
        return "9704189991010867647";
    }

    public String getMockExpiry() {
        return "3101";
    }

    public String getTrack2(String de22) {
        return "9704189991010867647=31016010000000123";
    }
}
