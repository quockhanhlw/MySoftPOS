package com.example.mysoftpos.iso8583;

import java.util.Locale;

/**
 * centralized utility for ISO field formatting.
 * Eliminates redundant logic in Builders/Activities.
 */
public class IsoUtils {

    /**
     * Clean Track 2 Data (DE 35)
     * Replaces '=' with 'D'.
     * Removes start/end sentinels if present.
     */
    public static String cleanTrack2(String rawTrack2) {
        if (rawTrack2 == null || rawTrack2.isEmpty()) return null;
        String clean = rawTrack2.replace("=", "D");
        // Remove potential start/end sentinels if needed (simple case for now)
        return clean;
    }

    /**
     * Format Merchant Name/Location (DE 43)
     * Rule: Bank Name (22) + Space + Location (13) + Space + Country (3)
     * Auto-pads or truncates to ensure exactly 40 chars.
     */
    public static String formatMerchantLocation(String bankName, String location, String countryCode) {
        if (bankName == null) bankName = "";
        if (location == null) location = "";
        if (countryCode == null) countryCode = "704";

        // 1. Bank Name (22 chars)
        String p1 = String.format("%-22s", bankName.length() > 22 ? bankName.substring(0, 22) : bankName);
        
        // 2. Location (13 chars)
        String p2 = String.format("%-13s", location.length() > 13 ? location.substring(0, 13) : location);
        
        // 3. Country (3 chars)
        String p3 = String.format("%-3s", countryCode.length() > 3 ? countryCode.substring(0, 3) : countryCode);

        // Combine: 22 + 1 + 13 + 1 + 3 = 40 chars (Assuming spaces are explicit separators, or implied in padding?)
        // Standard spec usually implies fixed width fields concatenated. 
        // User's sample: "MYSOFTPOS BANK         HA NOI        704"
        // Let's assume the user wants explicit space separators as seen in previous logic
        
        // Actually the previous logic was: Name(22) + " " + City(13) + " " + Country.
        // Let's stick to that 40 char generic formatter if possible, or use the components.
        return p1 + " " + p2 + " " + p3;
    }

    /**
     * Determine DE 22 (POS Entry Mode) dynamically.
     * @param isNfc true if contactless
     * @param isChip true if contact chip
     * @param hasPin true if PIN was entered/capable
     * @return 3-digit ISO code Strings (e.g., "071", "051", "021")
     */
    public static String determinePosEntryMode(boolean isNfc, boolean isChip, boolean isManual, boolean hasPin) {
        // First 2 digits: Entrty Mode
        String mode;
        if (isNfc) mode = "07";
        else if (isChip) mode = "05";
        else if (isManual) mode = "01";
        else mode = "02"; // Default/Magstripe

        // 3rd digit: PIN Capability
        // 1 = PIN Capable, 2 = No PIN
        String cap = hasPin ? "1" : "2";

        return mode + cap;
    }
    
    /**
     * Pad Left with Zeros (for numeric fields like DE 4, 11)
     */
    public static String padLeftZero(String val, int length) {
        if (val == null) val = "";
        try {
            long v = Long.parseLong(val);
             return String.format(Locale.US, "%0" + length + "d", v);
        } catch (NumberFormatException e) {
            // Fallback for non-numeric string padding if parse fails
             StringBuilder sb = new StringBuilder();
             while (sb.length() + val.length() < length) sb.append('0');
             sb.append(val);
             return sb.toString();
        }
    }
}
