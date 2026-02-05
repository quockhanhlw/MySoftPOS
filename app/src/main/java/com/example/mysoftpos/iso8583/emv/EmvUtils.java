package com.example.mysoftpos.iso8583.emv;

import com.example.mysoftpos.iso8583.emv.EmvUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EmvUtils {

    public static String getTag(String tlv, String tag) {
        // Mock implementation or real TLV parser needed?
        // For MySoftPOS context, this was likely a stub.
        // Assuming this parses a hex string.
        return "";
    }

    /**
     * Parses Tag 57 (Track 2 Equivalent Data).
     * Format: PAN + 'D'/'=' + Expiry + ServiceCode + Discret. + Padding 'F'
     */
    public static Map<String, String> parseTag57(String hex57) {
        Map<String, String> data = new HashMap<>();
        if (hex57 == null)
            return data;

        // 1. Sanitize: Remove trailing 'F'
        String clean = hex57.toUpperCase(Locale.ROOT).replaceAll("F+$", "");

        // 2. Split
        int sepIndex = clean.indexOf('D');
        if (sepIndex == -1)
            sepIndex = clean.indexOf('=');

        if (sepIndex != -1) {
            String pan = clean.substring(0, sepIndex);
            // Expiry is usually next 4 digits
            String trailer = clean.substring(sepIndex + 1);
            String expiry = (trailer.length() >= 4) ? trailer.substring(0, 4) : "";

            data.put("PAN", pan);
            data.put("EXPIRY", expiry);
            data.put("TRACK2", clean); // Store the full cleaned track2
        } else {
            // Fallback: Assume whole string is PAN? Unlikely for Tag 57.
            data.put("PAN", clean);
        }

        return data;
    }

    /**
     * Build BER-TLV Hex String.
     */
    public static String buildTlv(String tag, String value) {
        if (value == null)
            value = "";

        // Calculate Length (Bytes)
        int length = value.length() / 2;
        String lenHex;

        if (length <= 127) {
            lenHex = String.format("%02X", length);
        } else {
            // Long form: First byte is 10000000 | number_of_length_bytes
            lenHex = "81" + String.format("%02X", length);
        }

        return tag + lenHex + value;
    }
}
