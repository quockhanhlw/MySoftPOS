package com.example.mysoftpos.ui.formatter;

public final class AmountFormatter {
    private AmountFormatter() {}

    /**
     * Format VND amount from digit string: "" -> 0, "50000" -> "50.000".
     */
    public static String formatVnd(String digits) {
        if (digits == null || digits.isEmpty()) return "0";

        // strip leading zeros
        int i = 0;
        while (i < digits.length() - 1 && digits.charAt(i) == '0') i++;
        digits = digits.substring(i);

        StringBuilder sb = new StringBuilder();
        int len = digits.length();
        for (int idx = 0; idx < len; idx++) {
            sb.append(digits.charAt(idx));
            int remaining = len - idx - 1;
            if (remaining > 0 && remaining % 3 == 0) sb.append('.');
        }
        return sb.toString();
    }
}






