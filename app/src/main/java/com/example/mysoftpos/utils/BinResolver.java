package com.example.mysoftpos.utils;

import java.util.HashMap;
import java.util.Map;

public class BinResolver {

    private static final Map<String, String> BIN_MAP = new HashMap<>();

    static {
        // Napas / Common Banks
        BIN_MAP.put("970436", "Vietcombank");
        BIN_MAP.put("970415", "VietinBank");
        BIN_MAP.put("970418", "BIDV");
        BIN_MAP.put("970405", "Agribank");
        BIN_MAP.put("970403", "Sacombank");
        BIN_MAP.put("970407", "Techcombank");
        BIN_MAP.put("970422", "MB Bank");
        BIN_MAP.put("970432", "VPBank");
        BIN_MAP.put("970423", "TPBank");
        BIN_MAP.put("9704", "NAPAS"); 
    }

    public static String getBankName(String pan) {
        if (pan == null) return "Unknown";
        
        // Check 6 digits
        if (pan.length() >= 6) {
            String bin6 = pan.substring(0, 6);
            if (BIN_MAP.containsKey(bin6)) {
                return BIN_MAP.get(bin6);
            }
        }
        
        // Fallback for 4-digit match (if any generic) or 9704 prefix
        if (pan.startsWith("9704")) {
             return "NAPAS Card";
        }
        
        return "Unknown Bank";
    }
}
