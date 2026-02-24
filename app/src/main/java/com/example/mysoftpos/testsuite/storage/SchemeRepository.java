package com.example.mysoftpos.testsuite.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.mysoftpos.testsuite.model.Scheme;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SchemeRepository {

    private static final String PREFS_NAME = "scheme_prefs";
    private static final String KEY_SCHEMES = "schemes";
    private static final String KEY_INITIALIZED = "initialized";

    private final SharedPreferences prefs;

    public SchemeRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            initDefaults();
        }
    }

    private void initDefaults() {
        List<Scheme> defaults = new ArrayList<>();
        defaults.add(new Scheme("Napas", "9704", "N", "#1565C0", true));
        defaults.add(new Scheme("Visa", "4", "V", "#1A237E", true));
        defaults.add(new Scheme("Mastercard", "5", "M", "#C62828", true));
        saveAll(defaults);
        prefs.edit().putBoolean(KEY_INITIALIZED, true).apply();
    }

    public List<Scheme> getAll() {
        String json = prefs.getString(KEY_SCHEMES, "[]");
        List<Scheme> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void add(Scheme scheme) {
        List<Scheme> list = getAll();
        list.add(scheme);
        saveAll(list);
    }

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

    public void delete(String id) {
        List<Scheme> list = getAll();
        list.removeIf(s -> s.getId().equals(id));
        saveAll(list);
    }

    public Scheme getById(String id) {
        for (Scheme s : getAll()) {
            if (s.getId().equals(id))
                return s;
        }
        return null;
    }

    private void saveAll(List<Scheme> list) {
        JSONArray arr = new JSONArray();
        for (Scheme s : list) {
            arr.put(toJson(s));
        }
        prefs.edit().putString(KEY_SCHEMES, arr.toString()).apply();
    }

    private JSONObject toJson(Scheme s) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", s.getId());
            obj.put("name", s.getName());
            obj.put("prefix", s.getPrefix());
            obj.put("iconLetter", s.getIconLetter());
            obj.put("color", s.getColor());
            obj.put("builtIn", s.isBuiltIn());
            obj.put("serverIp", s.getServerIp());
            obj.put("serverPort", s.getServerPort());
            obj.put("timeout", s.getTimeout());
        } catch (JSONException e) {
            e.printStackTrace();
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
        s.setServerIp(obj.optString("serverIp", ""));
        s.setServerPort(obj.optInt("serverPort", 0));
        s.setTimeout(obj.optInt("timeout", 30000));
        return s;
    }

    /** Find scheme by name (case-insensitive) */
    public Scheme getByName(String name) {
        for (Scheme s : getAll()) {
            if (s.getName().equalsIgnoreCase(name))
                return s;
        }
        return null;
    }
}
