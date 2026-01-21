package com.example.mysoftpos.utils;

import com.example.mysoftpos.iso8583.IsoMessage;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;

/**
 * Simple utility to pack IsoMessage into bytes for demo purposes.
 * Now includes basic parsing for Field extraction.
 */
public class SimpleIsoPacker {

    public static byte[] pack(IsoMessage msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("MTI=").append(msg.getMti());
        
        for (Map.Entry<Integer, String> entry : msg.getFields().entrySet()) {
            sb.append("|").append(entry.getKey()).append("=").append(entry.getValue());
        }
        
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * Parses the response string (Key=Value format) to extract specific fields.
     */
    public static String unpackField(byte[] responseData, int fieldId) {
        if (responseData == null) return null;
        String s = new String(responseData, StandardCharsets.UTF_8);
        
        // Split by pipe |
        String[] parts = s.split("\\|");
        String targetKey = String.valueOf(fieldId) + "=";
        
        for (String part : parts) {
            if (part.startsWith(targetKey)) {
                return part.substring(targetKey.length());
            }
        }
        return null;
    }
    
    public static String unpackResponseCode(byte[] responseData) {
         String val = unpackField(responseData, 39);
         return val == null ? "XX" : val;
    }
}
