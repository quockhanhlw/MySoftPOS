package com.example.mysoftpos.utils.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PA-DSS 1.x / 2.x: PAN masking utility.
 * Automatically detects and masks Primary Account Numbers in any string.
 * Shows only first 6 and last 4 digits (PA-DSS compliant).
 */
public final class PanMasker {

    private PanMasker() {
    }

    // Regex to match potential PAN (13-19 digit sequences)
    private static final Pattern PAN_PATTERN = Pattern.compile("\\b(\\d{13,19})\\b");

    // Regex to match PAN in track2 format (PAN=expiry...)
    private static final Pattern TRACK2_PATTERN = Pattern.compile("(\\d{13,19})=(\\d{4,})");

    /**
     * PA-DSS 1.1.4: Mask PAN — show first 6, last 4 only.
     * Scans the input for any 13-19 digit sequences and masks them.
     */
    public static String mask(String input) {
        if (input == null || input.isEmpty())
            return input;

        // First mask track2 data (PAN=EXPIRY...)
        String result = replaceAllMatches(TRACK2_PATTERN, input, m -> {
            String pan = m.group(1);
            return maskSinglePan(pan) + "=****";
        });

        // Then mask standalone PANs
        result = replaceAllMatches(PAN_PATTERN, result, m -> {
            String pan = m.group(1);
            return maskSinglePan(pan);
        });

        return result;
    }

    /**
     * Mask a single PAN: show first 6 + last 4, rest as asterisks.
     */
    public static String maskSinglePan(String pan) {
        if (pan == null || pan.length() < 13)
            return pan;
        int len = pan.length();
        int maskedLen = len - 10;
        if (maskedLen <= 0)
            maskedLen = 1; // Safety: ensure at least 1 asterisk
        // PA-DSS 2.2: Display only first 6 and last 4
        return pan.substring(0, 6) + repeat('*', maskedLen) + pan.substring(len - 4);
    }

    /**
     * Mask hex-encoded data that may contain PAN bytes.
     * Uses conservative approach: mask DE2 field position in ISO hex.
     */
    public static String maskHex(String hexData) {
        if (hexData == null || hexData.length() < 26)
            return hexData;
        // For full ISO packets, PAN masking is done at string level after unpacking.
        // Here we do a simple pass to mask long hex digit sequences that look like BCD
        // PANs.
        Pattern hexPan = Pattern.compile("([0-9A-Fa-f]{26,38})");
        return replaceAllMatches(hexPan, hexData, m -> {
            String hex = m.group(1);
            int len = hex.length();
            if (len >= 20) {
                // Show first 12 hex chars (6 BCD digits) + mask + last 8 hex chars (4 BCD
                // digits)
                int maskedLen = len - 20;
                if (maskedLen <= 0)
                    maskedLen = 2;
                return hex.substring(0, 12) + repeat('*', maskedLen) + hex.substring(len - 8);
            }
            return hex;
        });
    }

    // --- Compat helpers (avoid Java 9 Matcher.replaceAll(Function)) ---

    private interface MatchReplacer {
        String replace(Matcher m);
    }

    /** Android-compatible replaceAll with callback (works on all API levels). */
    private static String replaceAllMatches(Pattern pattern, String input, MatchReplacer replacer) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            sb.append(input, lastEnd, matcher.start());
            sb.append(replacer.replace(matcher));
            lastEnd = matcher.end();
        }
        sb.append(input, lastEnd, input.length());
        return sb.toString();
    }

    /** Repeat a char n times (Java 8 compat, avoids String.repeat). */
    private static String repeat(char c, int count) {
        if (count <= 0)
            return "";
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }
}
