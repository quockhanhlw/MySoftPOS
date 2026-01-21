package com.example.mysoftpos.utils;

import java.util.Arrays;

public class TlvParser {

    /**
     * Search for a specific tag in the TLV data.
     * Note: This is a simplified recursive parser.
     *
     * @param data The TLV byte array.
     * @param tag  The tag to search for (e.g., 0x57 for Track2).
     * @return The value of the tag, or null if not found.
     */
    public static byte[] findTag(byte[] data, int tag) {
        int offset = 0;
        while (offset < data.length) {
            // Read Tag
            int currentTag = data[offset++] & 0xFF;
            if ((currentTag & 0x1F) == 0x1F) { // Multi-byte tag
                currentTag = (currentTag << 8) | (data[offset++] & 0xFF);
                // We handle up to 2-byte tags for simplicity
            }

            // Read Length
            if (offset >= data.length) break;
            int length = data[offset++] & 0xFF;
            if ((length & 0x80) != 0) { // Multi-byte length
                int numBytes = length & 0x7F;
                length = 0;
                for (int i = 0; i < numBytes; i++) {
                    if (offset >= data.length) break;
                    length = (length << 8) | (data[offset++] & 0xFF);
                }
            }

            // Check boundaries
            if (offset + length > data.length) {
                break; // Invalid length or end of data
            }

            // Check match
            if (currentTag == tag) {
                return Arrays.copyOfRange(data, offset, offset + length);
            }

            // If constructed tag (bit 6 set), recurse inside
            boolean isConstructed = (currentTag >> 8 == 0) ? (currentTag & 0x20) != 0 : ((currentTag >> 8) & 0x20) != 0;
            if (isConstructed) {
                byte[] val = Arrays.copyOfRange(data, offset, offset + length);
                byte[] result = findTag(val, tag);
                if (result != null) return result;
            }

            offset += length;
        }
        return null;
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
