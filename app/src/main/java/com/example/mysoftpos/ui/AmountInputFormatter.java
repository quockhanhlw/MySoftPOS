package com.example.mysoftpos.ui;

import android.text.Editable;
import android.text.TextWatcher;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formats an amount input using Vietnamese grouping separator '.' while user types.
 * Input: 35000 -> display: 35.000
 * Keeps only digits internally.
 */
public final class AmountInputFormatter implements TextWatcher {

    public interface OnDigitsChanged {
        void onChanged(String digits);
    }

    private final DecimalFormat df;
    private final OnDigitsChanged callback;
    private boolean selfChange;

    public AmountInputFormatter(OnDigitsChanged callback) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        df = new DecimalFormat("#,##0", sym);
        df.setGroupingUsed(true);
        df.setMaximumFractionDigits(0);
        this.callback = callback;
    }

    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        if (selfChange) return;
        if (s == null) return;
        String raw = s.toString();
        String digits = raw.replaceAll("\\D+", "");
        if (callback != null) callback.onChanged(digits);

        if (digits.isEmpty()) return;

        try {
            long value = Long.parseLong(digits);
            String formatted = df.format(value);
            if (!formatted.equals(raw)) {
                selfChange = true;
                try {
                    final Editable editable = s;
                    if (editable != null) {
                        editable.replace(0, editable.length(), formatted);
                    }
                } finally {
                    selfChange = false;
                }
            }
        } catch (Exception ignored) {
            // ignore parse error
        }
    }
}
