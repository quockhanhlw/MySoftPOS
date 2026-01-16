package com.example.mysoftpos.iso8583;

import androidx.annotation.NonNull;

import java.util.Locale;

/** Parcelable replacement kept simple via Intent extras (Strings). */
public final class PurchaseFlowData {
    private PurchaseFlowData() {}

    public static final String EXTRA_AMOUNT_DIGITS = "extra_amount_digits";
    public static final String EXTRA_AMOUNT_F4 = "extra_amount_f4";

    public static final String EXTRA_PAN = "extra_pan";
    public static final String EXTRA_EXPIRY_YYMM = "extra_expiry_yymm"; // optional
    public static final String EXTRA_TRACK2 = "extra_track2";            // optional
    public static final String EXTRA_ENTRY_MODE_22 = "extra_entry_mode_22";

    /**
     * Parse amount string that may contain currency grouping separators ('.' or ',').
     * Returns pure digits (no leading zeros normalization) for validation.
     */
    @NonNull
    public static String normalizeAmountDigits(@NonNull String input) {
        String d = input.replaceAll("\\D+", "");
        if (d.isEmpty()) return "0";
        // Avoid extremely long values early
        if (d.length() > 12) {
            throw new IllegalArgumentException("Amount too large (>12 digits): " + d);
        }
        return d;
    }

    @NonNull
    public static String toIsoAmount12FromDigits(@NonNull String digits) {
        String d = normalizeAmountDigits(digits);
        return String.format(Locale.US, "%012d", Long.parseLong(d));
    }
}
