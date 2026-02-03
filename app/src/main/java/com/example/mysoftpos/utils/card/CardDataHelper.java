package com.example.mysoftpos.utils.card;

public class CardDataHelper {

    /**
     * Extracts the Primary Account Number (PAN) from Track 2 Equivalent Data.
     * Track 2 Format: PAN + Separator ('D' or '=') + Expiry (YYMM) + Service Code + Discretionary Data
     *
     * @param track2Hex The Track 2 data in Hex format.
     * @return The PAN, or null if parsing fails.
     */
    public static String extractPan(String track2Hex) {
        if (track2Hex == null) return null;
        int separatorIndex = findSeparator(track2Hex);
        if (separatorIndex != -1) {
            return track2Hex.substring(0, separatorIndex);
        }
        return null; // Invalid format
    }

    /**
     * Extracts the Expiry Date (YYMM) from Track 2 Equivalent Data.
     *
     * @param track2Hex The Track 2 data in Hex format.
     * @return The Expiry Date (YYMM), or null if parsing fails.
     */
    public static String extractExpiry(String track2Hex) {
        if (track2Hex == null) return null;
        int separatorIndex = findSeparator(track2Hex);
        if (separatorIndex != -1 && separatorIndex + 5 <= track2Hex.length()) {
            return track2Hex.substring(separatorIndex + 1, separatorIndex + 5);
        }
        return null;
    }

    private static int findSeparator(String data) {
        int dIndex = data.indexOf('D');
        if (dIndex != -1) return dIndex;
        return data.indexOf('='); // Fallback if converted to string representation
    }
}





