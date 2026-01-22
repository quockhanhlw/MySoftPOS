package com.example.mysoftpos.utils;

import com.example.mysoftpos.domain.model.CardInputData;
import java.util.Map;

public class EmvUtils {

    /**
     * Parse Tag 57 (Track 2 Equivalent Data) to extract PAN and Expiry.
     * Expects the Tag 57 value as a Hex String.
     */
    public static CardInputData parseTag57(String tag57Hex, Map<String, String> fullTags) {
        if (tag57Hex == null || tag57Hex.isEmpty()) return null;

        // 1. Sanitize: Remove trailing 'F' padding
        String clean = tag57Hex.toUpperCase().replaceAll("F*$", "");

        // 2. Find Separator 'D' or '='
        int separatorIndex = clean.indexOf('D');
        if (separatorIndex == -1) {
            separatorIndex = clean.indexOf('=');
        }

        if (separatorIndex == -1) {
            // No separator found, cannot parse
            return null;
        }

        // 3. Extract PAN
        String pan = clean.substring(0, separatorIndex);

        // 4. Extract Expiry (YYMM) - Next 4 digits after separator
        String expiry = "";
        if (separatorIndex + 5 <= clean.length()) { // Separator + 4 digits
            expiry = clean.substring(separatorIndex + 1, separatorIndex + 5);
        }

        // 5. Populate CardInputData
        // Pan, Expiry, Track2 (Tag 57 value cleaned? or Raw?), PosEntryMode (051 -> 071 internal logic), Pin (null), Tags
        // Note: CardInputData constructor usually expects raw track2 if needed, 
        // but for ISO DE 35 we often use the cleaned version without F padding? 
        // Let's pass 'clean' as Track2.
        return new CardInputData(
                pan,
                expiry,
                clean, // Track 2 Data for DE 35
                "071", // NFC Mode detected
                null,  // No PIN
                fullTags
        );
    }
}
