package com.example.mysoftpos.utils;

/**
 * Utility for PAN masking, scheme detection, BIN/last4 extraction.
 * Centralizes logic previously duplicated in RunnerViewModel,
 * MultiThreadRunnerActivity, PurchaseViewModel.
 */
public final class PanUtils {

    private PanUtils() {
    }

    public static String mask(String pan) {
        if (pan == null || pan.length() < 10)
            return pan;
        return pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4);
    }

    public static String getBin(String pan) {
        return (pan != null && pan.length() >= 6) ? pan.substring(0, 6) : "";
    }

    public static String getLast4(String pan) {
        return (pan != null && pan.length() >= 4) ? pan.substring(pan.length() - 4) : "";
    }

    public static String detectScheme(String pan) {
        if (pan == null)
            return "Unknown";
        if (pan.startsWith("9704"))
            return "Napas";
        if (pan.startsWith("4"))
            return "Visa";
        if (pan.startsWith("5"))
            return "Mastercard";
        return "Unknown";
    }

    public static String maskTrack2(String track2) {
        if (track2 == null || track2.length() < 10)
            return track2;
        return track2.substring(0, 6) + "......" + track2.substring(track2.length() - 4);
    }
}
