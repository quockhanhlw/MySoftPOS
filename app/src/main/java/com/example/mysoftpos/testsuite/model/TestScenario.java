package com.example.mysoftpos.testsuite.model;

import java.util.Map;
import java.util.TreeMap;

/**
 * Enhanced Test Scenario Model for NAPAS Specification.
 * Handles specialized debug logging and field formatting.
 */
public class TestScenario {
    private String id;
    private final String mti;
    private final Map<Integer, String> fields = new TreeMap<>();
    private final String description;

    public TestScenario(String mti, String description) {
        this.mti = mti;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setField(int fieldId, String value) {
        if (value != null) {
            fields.put(fieldId, value);
        }
    }

    public String getMti() {
        return mti;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Generates a debug formatted log string matching the required NAPAS Trace
     * Format.
     */
    public String toLog() {
        StringBuilder sb = new StringBuilder();
        sb.append("MTI: ").append(mti).append("\n");

        // DE 1: Primary Bitmap
        sb.append("001 (DE1): [").append(calculateBitmapHex()).append("]\n");

        for (Map.Entry<Integer, String> entry : fields.entrySet()) {
            int id = entry.getKey();
            String val = entry.getValue();
            String fieldName = getFieldName(id);

            String formattedId = String.format("%03d", id);
            String maskedVal = maskValue(id, val);

            sb.append(formattedId).append(" (").append(fieldName).append("): ").append(maskedVal).append("\n");
        }
        return sb.toString();
    }

    private String maskValue(int id, String val) {
        if (id == 2 && val.length() >= 10) {
            return val.substring(0, 6) + "****" + val.substring(val.length() - 4);
        }
        if (id == 35)
            return "[Content Hidden]";
        return val;
    }

    private String calculateBitmapHex() {
        long bitmap = 0L;
        // Basic primary bitmap (up to 64)
        for (Integer fieldId : fields.keySet()) {
            if (fieldId > 1 && fieldId <= 64) {
                bitmap |= (1L << (64 - fieldId));
            }
        }
        return String.format("%016X", bitmap);
    }

    private String getFieldName(int id) {
        switch (id) {
            case 2:
                return "PAN";
            case 3:
                return "Processing Code";
            case 4:
                return "Amount";
            case 7:
                return "Transmission Date/Time";
            case 11:
                return "STAN";
            case 12:
                return "Local Time";
            case 13:
                return "Local Date";
            case 14:
                return "Expiration Date";
            case 15:
                return "DE15";
            case 18:
                return "Merchant Type";
            case 22:
                return "POS Entry Mode";
            case 25:
                return "POS Condition Code";
            case 32:
                return "Acquirer ID";
            case 35:
                return "Track 2";
            case 37:
                return "RRN";
            case 41:
                return "Terminal ID";
            case 42:
                return "Merchant ID";
            case 43:
                return "Merchant Name/Loc";
            case 49:
                return "Currency Code";
            case 52:
                return "PIN";
            case 55:
                return "Chip Data";
            default:
                return "Field " + id;
        }
    }
}
