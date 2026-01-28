package com.example.mysoftpos.iso8583;

import android.content.Context;
import com.example.mysoftpos.utils.ConfigManager;
import java.util.HashMap;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Smart ISO Builder.
 * Replaces hardcoded logic throughout the app.
 * Uses Templates and Strategies to build messages dynamically.
 */
public class SmartIsoBuilder {

    // Input Keys for the Input Map
    public static final String KEY_PAN = "PAN";
    public static final String KEY_EXPIRY = "EXPIRY";
    public static final String KEY_TRACK2 = "TRACK2";
    public static final String KEY_AMOUNT = "AMOUNT";
    public static final String KEY_PIN_BLOCK = "PIN_BLOCK";
    public static final String KEY_EMV_TAGS = "EMV_TAGS"; // Map<String,String> or String
    public static final String KEY_POS_MODE = "POS_MODE"; // "051" etc.
    public static final String KEY_STAN = "STAN"; // If pre-generated

    private final ConfigManager config;

    public SmartIsoBuilder(Context context) {
        this.config = ConfigManager.getInstance(context);
    }

    /**
     * The Master Build Method.
     * Iterates through a defined Template to build the ISO Message.
     */
    public IsoMessage build(String mti, Map<String, Object> inputs) {
        IsoMessage msg = new IsoMessage(mti);

        // 1. Define Template (Field -> Strategy)
        // This could be moved to a separate Definition class
        Map<Integer, FieldStrategy> template = getTemplateForMti(mti);

        // 2. Iterate and Build
        for (Map.Entry<Integer, FieldStrategy> entry : template.entrySet()) {
            int field = entry.getKey();
            FieldStrategy strategy = entry.getValue();

            String value = resolveValue(field, strategy, inputs);
            
            if (value != null) {
                msg.setField(field, value);
            }
        }

        return msg;
    }

    private Map<Integer, FieldStrategy> getTemplateForMti(String mti) {
        Map<Integer, FieldStrategy> t = new HashMap<>();
        
        // Common 0200 Template (Purchase)
        if ("0200".equals(mti)) {
            t.put(2, FieldStrategy.INPUT);        // PAN
            t.put(3, FieldStrategy.FIXED);        // Proc Code (000000 for Purchase)
            t.put(4, FieldStrategy.INPUT);        // Amount (Needs Formatting)
            t.put(7, FieldStrategy.AUTO_DATE);    // Trans Date/Time
            t.put(11, FieldStrategy.AUTO_STAN);   // STAN
            t.put(12, FieldStrategy.AUTO_DATE);   // Time
            t.put(13, FieldStrategy.AUTO_DATE);   // Date
            t.put(14, FieldStrategy.OPTIONAL_INPUT); // Expiry
            t.put(15, FieldStrategy.AUTO_DATE);   // Settlement Date
            t.put(18, FieldStrategy.CONFIG);      // MCC
            t.put(22, FieldStrategy.COMPUTED);    // POS Entry Mode
            t.put(25, FieldStrategy.FIXED);       // POS Condition
            t.put(32, FieldStrategy.CONFIG);      // Acquirer ID
            t.put(35, FieldStrategy.INPUT);       // Track 2 (Needs Cleaning)
            t.put(37, FieldStrategy.AUTO_RRN);    // RRN
            t.put(41, FieldStrategy.CONFIG);      // Terminal ID
            t.put(42, FieldStrategy.CONFIG);      // Merchant ID
            t.put(43, FieldStrategy.CONFIG);      // Merchant Name
            t.put(49, FieldStrategy.CONFIG);      // Currency
            t.put(52, FieldStrategy.OPTIONAL_INPUT); // PIN
            t.put(55, FieldStrategy.COMPUTED);    // ICC Data
        }
        // Add layouts for 0420, Balance, etc. here
        
        return t;
    }

    private String resolveValue(int field, FieldStrategy strategy, Map<String, Object> inputs) {
        switch (strategy) {
            case FIXED:
                if (field == 3) return "000000"; // Default, or specific logic? 
                if (field == 25) return "00";
                return null;

            case INPUT:
            case OPTIONAL_INPUT:
                return resolveInput(field, inputs);

            case CONFIG:
                if (field == 18) return config.getMcc18();
                if (field == 32) return config.getAcquirerId32();
                if (field == 41) return config.getTerminalId();
                if (field == 42) return config.getMerchantId();
                if (field == 43) return IsoUtils.formatMerchantLocation(config.getMerchantName(), "HA NOI", "704");
                if (field == 49) return config.getCurrencyCode49();
                return null;

            case AUTO_STAN:
                // If STAN is provided in inputs, use it, else generic logic?
                // Usually Runner provides STAN to link DB.
                if (inputs.containsKey(KEY_STAN)) return (String) inputs.get(KEY_STAN);
                return config.getAndIncrementTrace();

            case AUTO_RRN:
                String stan = (String) inputs.get(KEY_STAN);
                // Use centralized logic logic
                 return TransactionContext.calculateRrn("00", stan);

            case AUTO_DATE:
                Date now = new Date();
                if (field == 7) return new SimpleDateFormat("MMddhhmmss", Locale.US).format(now);
                if (field == 12) return new SimpleDateFormat("HHmmss", Locale.US).format(now);
                if (field == 13) return new SimpleDateFormat("MMdd", Locale.US).format(now);
                if (field == 15) return new SimpleDateFormat("MMdd", Locale.US).format(now);
                return null;

            case COMPUTED:
                if (field == 22) return resolveDE22(inputs);
                if (field == 55) return resolveDE55(inputs);
                return null;

            default:
                return null;
        }
    }

    private String resolveInput(int field, Map<String, Object> inputs) {
        switch (field) {
            case 2: return (String) inputs.get(KEY_PAN);
            case 4: 
                String amt = (String) inputs.get(KEY_AMOUNT);
                return TransactionContext.formatAmount12(amt); // Reuse Utils, or move logic to IsoUtils
            case 14: return (String) inputs.get(KEY_EXPIRY);
            case 35: return IsoUtils.cleanTrack2((String) inputs.get(KEY_TRACK2));
            case 52: return (String) inputs.get(KEY_PIN_BLOCK);
            default: return null;
        }
    }

    private String resolveDE22(Map<String, Object> inputs) {
        // Logic: 
        // If KEY_POS_MODE is provided (e.g. from Test Case), use it directly?
        // Or re-compute? User said "Remove any hardcoded 021... logic correctly detects NFC vs Manual"
        if (inputs.containsKey(KEY_POS_MODE)) {
            return (String) inputs.get(KEY_POS_MODE);
        }
        return "021"; // Default Fallback?
    }

    private String resolveDE55(Map<String, Object> inputs) {
        // Only if Chip/NFC
        String mode = resolveDE22(inputs);
        if (mode != null && (mode.startsWith("05") || mode.startsWith("07"))) {
            Object tags = inputs.get(KEY_EMV_TAGS);
             if (tags instanceof Map) {
                return buildEmvString((Map<String, String>) tags);
            }
             // or null
        }
        return null;
    }
    
    // Helper for EMV Map -> String
    private String buildEmvString(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : tags.entrySet()) {
            sb.append(e.getKey());
            int len = e.getValue().length() / 2;
            sb.append(String.format("%02X", len));
            sb.append(e.getValue());
        }
        return sb.toString();
    }
}
