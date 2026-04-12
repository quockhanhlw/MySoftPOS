package com.example.mysoftpos.utils.format;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public final class AmountFormatUtils {

    private AmountFormatUtils() {
    }

    public static String normalizeCurrencyCode(String currencyCode) {
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            return "704";
        }
        String normalized = currencyCode.trim().toUpperCase(Locale.ROOT);
        if ("USD".equals(normalized)) {
            return "840";
        }
        if ("VND".equals(normalized)) {
            return "704";
        }
        return normalized;
    }

    public static String formatAmountDisplay(String rawAmount, String currencyCode) {
        if (rawAmount == null || rawAmount.trim().isEmpty()) {
            return "-";
        }

        String normalizedCurrency = normalizeCurrencyCode(currencyCode);
        String digits = rawAmount.replaceAll("[^0-9-]", "");
        if (digits.isEmpty() || "-".equals(digits)) {
            return rawAmount;
        }

        try {
            boolean negative = digits.startsWith("-");
            String absDigits = negative ? digits.substring(1) : digits;
            BigDecimal amount = new BigDecimal(absDigits);
            if (negative) {
                amount = amount.negate();
            }

            if ("704".equals(normalizedCurrency)) {
                // Only convert legacy ISO DE4-like fixed-width values (12 digits) for VND.
                // Normal entered/display amounts (e.g. 100000) must stay unchanged.
                if (!negative && absDigits.length() == 12 && absDigits.endsWith("00")) {
                    amount = amount.movePointLeft(2);
                }
                NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
                nf.setMaximumFractionDigits(0);
                nf.setMinimumFractionDigits(0);
                return nf.format(amount) + " VND";
            }

            if ("840".equals(normalizedCurrency)) {
                BigDecimal usd = amount.movePointLeft(2).setScale(2, RoundingMode.DOWN);
                NumberFormat cf = NumberFormat.getCurrencyInstance(Locale.US);
                cf.setMinimumFractionDigits(2);
                cf.setMaximumFractionDigits(2);
                return cf.format(usd);
            }

            NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
            nf.setMaximumFractionDigits(0);
            nf.setMinimumFractionDigits(0);
            return nf.format(amount) + " " + normalizedCurrency;
        } catch (Exception ignored) {
            return rawAmount;
        }
    }
}

