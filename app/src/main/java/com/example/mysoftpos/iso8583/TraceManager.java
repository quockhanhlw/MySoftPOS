package com.example.mysoftpos.iso8583;

import java.util.Locale;

/** Minimal compatibility helper used by legacy JVM tests. */
public final class TraceManager {
    private TraceManager() {
    }

    public static String pad6(int trace) {
        if (trace < 0) {
            trace = 0;
        }
        return String.format(Locale.ROOT, "%06d", trace % 1_000_000);
    }
}

