package com.example.mysoftpos.testsuite.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.mysoftpos.testsuite.model.Scheme;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Manages scheme configurations (IP, port, timeout, etc.) persisted as a JSON file.
 * <p>
 * Storage location: {@code <internal>/config/scheme_config.json}
 * <p>
 * On first launch, automatically migrates data from the legacy SharedPreferences
 * ({@code scheme_prefs}) if present, then removes the old prefs.
 * <p>
 * File format (pretty-printed):
 * <pre>
 * {
 *   "version": 1,
 *   "lastModified": "2026-03-02 10:30:00",
 *   "schemes": [ { ... }, { ... } ]
 * }
 * </pre>
 */
public class SchemeRepository {

    private static final String TAG = "SchemeRepository";

    /** Config directory inside app internal storage */
    private static final String CONFIG_DIR = "config";

    /** JSON config file name */
    private static final String CONFIG_FILE = "scheme_config.json";

    /** Current file format version */
    private static final int FILE_VERSION = 1;

    /** Legacy SharedPreferences name (for migration) */
    private static final String LEGACY_PREFS_NAME = "scheme_prefs";
    private static final String LEGACY_KEY_SCHEMES = "schemes";
    private static final String LEGACY_KEY_INITIALIZED = "initialized";

    private final Context context;
    private final File configFile;

    // ───────────────────────────────── Constructor ─────────────────────────────────

    public SchemeRepository(Context context) {
        this.context = context.getApplicationContext();

        // Ensure config directory exists
        File dir = new File(this.context.getFilesDir(), CONFIG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.configFile = new File(dir, CONFIG_FILE);

        // Migrate from legacy SharedPreferences if the file doesn't exist yet
        if (!configFile.exists()) {
            migrateFromSharedPreferences();
        }

        // If still no file (fresh install, no legacy data), create defaults
        if (!configFile.exists()) {
            initDefaults();
        }
    }

    // ─────────────────────────── Migration from SharedPrefs ───────────────────────

    /**
     * One-time migration: reads legacy SharedPreferences data and writes it to the
     * new JSON file, then clears the old prefs.
     */
    private void migrateFromSharedPreferences() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
            if (!prefs.getBoolean(LEGACY_KEY_INITIALIZED, false)) {
                return; // Nothing to migrate
            }

            String json = prefs.getString(LEGACY_KEY_SCHEMES, "[]");
            List<Scheme> list = parseArray(json);

            if (!list.isEmpty()) {
                saveAll(list);
                Log.i(TAG, "Migrated " + list.size() + " schemes from SharedPreferences → " + configFile.getAbsolutePath());
            }

            // Clear legacy prefs
            prefs.edit().clear().apply();
        } catch (Exception e) {
            Log.e(TAG, "Migration failed: " + e.getMessage(), e);
        }
    }

    // ──────────────────────────────── Defaults ────────────────────────────────────

    private void initDefaults() {
        List<Scheme> defaults = new ArrayList<>();
        defaults.add(new Scheme("Napas", "9704", "N", "#1565C0", true));
        defaults.add(new Scheme("Visa", "4", "V", "#1A237E", true));
        defaults.add(new Scheme("Mastercard", "5", "M", "#C62828", true));
        saveAll(defaults);
    }

    // ─────────────────────────────── Public API ───────────────────────────────────

    /** Load all schemes from the config file. */
    public List<Scheme> getAll() {
        String content = readFile();
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            JSONObject root = new JSONObject(content);
            JSONArray arr = root.optJSONArray("schemes");
            return parseSchemeArray(arr);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing config file: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /** Add a new scheme and persist. */
    public void add(Scheme scheme) {
        List<Scheme> list = getAll();
        list.add(scheme);
        saveAll(list);
    }

    /** Update an existing scheme (matched by id) and persist. */
    public void update(Scheme scheme) {
        List<Scheme> list = getAll();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(scheme.getId())) {
                list.set(i, scheme);
                break;
            }
        }
        saveAll(list);
    }

    /** Delete a scheme by id and persist. */
    public void delete(String id) {
        List<Scheme> list = getAll();
        list.removeIf(s -> s.getId().equals(id));
        saveAll(list);
    }

    /** Find a scheme by its id. */
    public Scheme getById(String id) {
        for (Scheme s : getAll()) {
            if (s.getId().equals(id))
                return s;
        }
        return null;
    }

    /** Find a scheme by name (case-insensitive). */
    public Scheme getByName(String name) {
        for (Scheme s : getAll()) {
            if (s.getName().equalsIgnoreCase(name))
                return s;
        }
        return null;
    }

    /** Returns the absolute path of the config file (for debugging / export). */
    public String getConfigFilePath() {
        return configFile.getAbsolutePath();
    }

    /**
     * Reload from file — useful after an external file update (e.g. import / adb push).
     * Same as {@link #getAll()}.
     */
    public List<Scheme> reload() {
        return getAll();
    }

    // ───────────────────────────── File I/O ───────────────────────────────────────

    /** Persist the full scheme list to the JSON config file. */
    private void saveAll(List<Scheme> list) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", FILE_VERSION);
            root.put("lastModified", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));

            JSONArray arr = new JSONArray();
            for (Scheme s : list) {
                arr.put(toJson(s));
            }
            root.put("schemes", arr);

            writeFile(root.toString(2)); // pretty-print with indent=2
            Log.d(TAG, "Saved " + list.size() + " schemes to " + configFile.getAbsolutePath());
        } catch (JSONException e) {
            Log.e(TAG, "Error building JSON: " + e.getMessage(), e);
        }
    }

    /** Read the entire config file as a string. */
    private String readFile() {
        if (!configFile.exists()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading config file: " + e.getMessage(), e);
            return null;
        }
        return sb.toString();
    }

    /** Write content to the config file (atomic: write temp, then rename). */
    private void writeFile(String content) {
        File tempFile = new File(configFile.getParent(), CONFIG_FILE + ".tmp");
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(content);
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Error writing config file: " + e.getMessage(), e);
            return;
        }

        // Atomic rename
        if (tempFile.exists()) {
            if (configFile.exists()) {
                configFile.delete();
            }
            tempFile.renameTo(configFile);
        }
    }

    // ──────────────────────────── JSON Serialization ──────────────────────────────

    private JSONObject toJson(Scheme s) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", s.getId());
            obj.put("name", s.getName());
            obj.put("prefix", s.getPrefix());
            obj.put("iconLetter", s.getIconLetter());
            obj.put("color", s.getColor());
            obj.put("builtIn", s.isBuiltIn());
            // Connection
            obj.put("serverIp", s.getServerIp());
            obj.put("serverPort", s.getServerPort());
            obj.put("timeout", s.getTimeout());
            // Terminal / Merchant
            obj.put("terminalId", s.getTerminalId());
            obj.put("merchantId", s.getMerchantId());
            obj.put("mcc", s.getMcc());
            obj.put("acquirerId", s.getAcquirerId());
            obj.put("currencyCode", s.getCurrencyCode());
            obj.put("countryCode", s.getCountryCode());
            obj.put("merchantName", s.getMerchantName());
            obj.put("merchantLocation", s.getMerchantLocation());
            obj.put("merchantCountry", s.getMerchantCountry());
            obj.put("posConditionCode", s.getPosConditionCode());
        } catch (JSONException e) {
            Log.e(TAG, "Error serializing scheme: " + e.getMessage());
        }
        return obj;
    }

    private Scheme fromJson(JSONObject obj) throws JSONException {
        Scheme s = new Scheme();
        s.setId(obj.getString("id"));
        s.setName(obj.getString("name"));
        s.setPrefix(obj.optString("prefix", ""));
        s.setIconLetter(obj.optString("iconLetter", "?"));
        s.setColor(obj.optString("color", "#607D8B"));
        s.setBuiltIn(obj.optBoolean("builtIn", false));
        // Connection
        s.setServerIp(obj.optString("serverIp", ""));
        s.setServerPort(obj.optInt("serverPort", 0));
        s.setTimeout(obj.optInt("timeout", 30000));
        // Terminal / Merchant
        s.setTerminalId(obj.optString("terminalId", ""));
        s.setMerchantId(obj.optString("merchantId", ""));
        s.setMcc(obj.optString("mcc", ""));
        s.setAcquirerId(obj.optString("acquirerId", ""));
        s.setCurrencyCode(obj.optString("currencyCode", ""));
        s.setCountryCode(obj.optString("countryCode", ""));
        s.setMerchantName(obj.optString("merchantName", ""));
        s.setMerchantLocation(obj.optString("merchantLocation", ""));
        s.setMerchantCountry(obj.optString("merchantCountry", ""));
        s.setPosConditionCode(obj.optString("posConditionCode", "00"));
        return s;
    }

    /** Parse a JSONArray of scheme objects. */
    private List<Scheme> parseSchemeArray(JSONArray arr) {
        List<Scheme> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            try {
                list.add(fromJson(arr.getJSONObject(i)));
            } catch (JSONException e) {
                Log.w(TAG, "Skipping malformed scheme at index " + i);
            }
        }
        return list;
    }

    /** Parse a raw JSON array string (used for migration). */
    private List<Scheme> parseArray(String jsonArrayStr) {
        List<Scheme> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(jsonArrayStr);
            return parseSchemeArray(arr);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing legacy JSON: " + e.getMessage());
        }
        return list;
    }
}
