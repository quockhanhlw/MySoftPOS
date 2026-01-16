package com.example.mysoftpos.iso8583;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Manages STAN (F11) to avoid duplicates.
 *
 * Requirement: after each transaction, increment STAN and persist it.
 */
public final class TraceManager {
    private TraceManager() {}

    private static final String PREFS = "SoftPOSTxn";
    private static final String KEY_STAN = "last_stan";

    /** Returns next STAN as 6n, increments and saves. Wraps 000001..999999. */
    public static synchronized String nextStan6(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int last = sp.getInt(KEY_STAN, 0);
        int next = last + 1;
        if (next > 999999) next = 1;
        sp.edit().putInt(KEY_STAN, next).apply();
        return String.format(Locale.US, "%06d", next);
    }

    /** For QA/testing: reset to 0 so next is 000001. */
    public static synchronized void resetStan(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_STAN, 0)
                .apply();
    }

    /** Pads integer to 6 digits with leading zeros (e.g. 1 -> 000001). */
    public static String pad6(int value) {
        int v = value;
        if (v < 0) v = 0;
        if (v > 999999) v = 999999;
        return String.format(Locale.US, "%06d", v);
    }
}
