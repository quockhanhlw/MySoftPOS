package com.example.mysoftpos.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.mysoftpos.testsuite.model.TestScenario;
import com.example.mysoftpos.data.local.entity.CardEntity;
import com.example.mysoftpos.data.local.entity.TestCaseEntity;
import com.example.mysoftpos.data.local.entity.TestSuiteEntity;
import com.example.mysoftpos.testsuite.TestDataProvider;

/**
 * One-time local data bootstrap for baseline Room tables.
 */
public final class LocalDataBootstrapper {

    private static final String TAG = "LocalBootstrap";
    private static final String PREFS_NAME = "local_bootstrap";
    private static final String KEY_DONE = "baseline_seed_v2";

    private LocalDataBootstrapper() {
    }

    public static void runIfNeeded(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_DONE, false)) {
            return;
        }

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(app);

                seedSuiteSnapshot(db, app, "Napas", "PURCHASE");
                seedSuiteSnapshot(db, app, "Napas", "BALANCE");
                seedDefaultCards(db);

                prefs.edit().putBoolean(KEY_DONE, true).apply();
            } catch (Exception e) {
                Log.w(TAG, "Bootstrap failed: " + e.getMessage());
            }
        }).start();
    }

    private static void seedSuiteSnapshot(AppDatabase db, Context context, String scheme, String txnType) {
        java.util.List<TestScenario> scenarios = TestDataProvider.generateScenarios(context, scheme);
        if (scenarios == null || scenarios.isEmpty()) {
            return;
        }

        String suiteName = "Built-in " + scheme + " - " + txnType;
        TestSuiteEntity suite = db.testSuiteDao().findByNameSync(suiteName);
        long suiteId;
        if (suite == null) {
            suite = new TestSuiteEntity();
            suite.backendId = 0;
            suite.adminBackendId = 0;
            suite.name = suiteName;
            suite.description = "Hardcoded baseline suite";
            suite.createdAt = System.currentTimeMillis();
            suiteId = db.testSuiteDao().insert(suite);
        } else {
            suiteId = suite.id;
        }

        if (suiteId <= 0) {
            return;
        }

        db.testCaseDao().deleteBySuiteId(suiteId);
        for (TestScenario scenario : scenarios) {
            insertDefaultTestCase(db, suiteId, scheme, txnType, scenario);
        }
    }

    private static void insertDefaultTestCase(AppDatabase db, long suiteId, String scheme, String txnType,
                                              TestScenario scenario) {
        TestCaseEntity c = new TestCaseEntity();
        c.suiteId = suiteId;
        c.name = scenario.getDescription();
        c.transactionType = txnType;
        c.status = "READY";
        c.amount = scenario.getField(4);
        c.de22 = scenario.getField(22);
        c.expiry = scenario.getField(14);
        c.scheme = scheme;
        c.maskedPan = resolveMaskedPan(scenario);
        c.fieldConfigJson = scenarioFieldsToJson(scenario);
        c.timestamp = System.currentTimeMillis();
        c.isDefault = true;
        db.testCaseDao().insert(c);
    }

    private static String scenarioFieldsToJson(TestScenario scenario) {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            java.util.Map<Integer, String> fields = scenario.getAllFields();
            for (java.util.Map.Entry<Integer, String> entry : fields.entrySet()) {
                if (entry.getValue() != null) {
                    json.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return json.length() > 0 ? json.toString() : "{}";
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String resolveMaskedPan(TestScenario scenario) {
        String pan = scenario.getField(2);
        if (pan == null || pan.trim().isEmpty()) {
            String track2 = scenario.getField(35);
            if (track2 != null) {
                int split = track2.indexOf('=');
                if (split < 0) {
                    split = track2.indexOf('D');
                }
                pan = split > 0 ? track2.substring(0, split) : track2;
            }
        }
        if (pan == null || pan.trim().length() < 10) {
            return "970416******9923";
        }
        pan = pan.trim();
        return pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4);
    }

    private static void seedDefaultCards(AppDatabase db) {
        upsertCard(db, new CardEntity("970416******9923", "970416", "9923", "Napas"));
        upsertCard(db, new CardEntity("970430******5257", "970430", "5257", "Napas"));
        upsertCard(db, new CardEntity("970418******7647", "970418", "7647", "Napas"));
    }

    private static void upsertCard(AppDatabase db, CardEntity card) {
        CardEntity existing = db.cardDao().getByPanMasked(card.panMasked);
        if (existing != null) {
            return;
        }
        card.backendId = 0;
        card.adminBackendId = 0;
        card.posAccountBackendId = 0;
        db.cardDao().insert(card);
    }
}

