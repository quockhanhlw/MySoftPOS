package com.example.mysoftpos.iso8583;

import com.example.mysoftpos.utils.ConfigManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Smart ISO Builder.
 * Implements NAPAS Specification logic.
 * Refactored for Dependency Injection (SRP/DIP).
 */
public class SmartIsoBuilder {

    public static final String KEY_PAN = "PAN";
    public static final String KEY_EXPIRY = "EXPIRY";
    public static final String KEY_TRACK2 = "TRACK2";
    public static final String KEY_AMOUNT = "AMOUNT";
    public static final String KEY_PIN_BLOCK = "PIN_BLOCK";
    public static final String KEY_EMV_TAGS = "EMV_TAGS";
    public static final String KEY_POS_MODE = "POS_MODE";
    public static final String KEY_PROC_CODE = "PROC_CODE";
    public static final String KEY_STAN = "STAN";

    private final ConfigManager config;

    // DIP: Config is injected, not created locally.
    public SmartIsoBuilder(ConfigManager config) {
        this.config = config;
    }

    public IsoMessage build(String mti, Map<String, Object> inputs,
            java.util.List<com.example.mysoftpos.data.local.entity.FieldConfiguration> fieldConfigs) {
        IsoMessage msg = new IsoMessage(mti);

        // Dynamic Construction
        if (fieldConfigs != null && !fieldConfigs.isEmpty()) {
            for (com.example.mysoftpos.data.local.entity.FieldConfiguration cfg : fieldConfigs) {
                String value = resolveFromConfig(cfg, inputs);
                if (value != null) {
                    msg.setField(cfg.fieldId, value);
                }
            }
        } else {
            // Fallback to Hardcoded Template (Legacy Support)
            Map<Integer, FieldStrategy> template = getTemplateForMti(mti);
            for (Map.Entry<Integer, FieldStrategy> entry : template.entrySet()) {
                int field = entry.getKey();
                FieldStrategy strategy = entry.getValue();
                String value = resolveValue(field, strategy, inputs);
                if (value != null) {
                    msg.setField(field, value);
                }
            }
        }
        return msg;
    }

    private String resolveFromConfig(com.example.mysoftpos.data.local.entity.FieldConfiguration cfg,
            Map<String, Object> inputs) {
        String sourceType = cfg.sourceType != null ? cfg.sourceType : "FIXED";

        switch (sourceType) {
            case "FIXED":
                return cfg.value; // e.g. "00" for DE 25

            case "INPUT":
                return resolveInput(cfg.fieldId, inputs);

            case "AUTO":
                // Map to existing strategy logic based on field ID
                // This is a simplified glue layer
                if (cfg.fieldId == 7)
                    return resolveValue(7, FieldStrategy.AUTO_DATE, inputs);
                if (cfg.fieldId == 11)
                    return resolveValue(11, FieldStrategy.AUTO_STAN, inputs);
                if (cfg.fieldId == 12)
                    return resolveValue(12, FieldStrategy.AUTO_DATE, inputs);
                if (cfg.fieldId == 13)
                    return resolveValue(13, FieldStrategy.AUTO_DATE, inputs);
                if (cfg.fieldId == 15)
                    return resolveValue(15, FieldStrategy.AUTO_DATE, inputs);
                if (cfg.fieldId == 37)
                    return resolveValue(37, FieldStrategy.AUTO_RRN, inputs);

                // Computed/Complex fields
                if (cfg.fieldId == 22)
                    return resolveValue(22, FieldStrategy.COMPUTED, inputs);
                if (cfg.fieldId == 23)
                    return resolveValue(23, FieldStrategy.COMPUTED, inputs);
                if (cfg.fieldId == 35)
                    return resolveValue(35, FieldStrategy.COMPUTED, inputs);
                if (cfg.fieldId == 55)
                    return resolveValue(55, FieldStrategy.COMPUTED, inputs);
                if (cfg.fieldId == 60)
                    return resolveValue(60, FieldStrategy.COMPUTED, inputs);

                // Config-based (Terminal/Merchant)
                if (cfg.fieldId == 18 || cfg.fieldId == 32 || cfg.fieldId == 41 ||
                        cfg.fieldId == 42 || cfg.fieldId == 43 || cfg.fieldId == 49) {
                    return resolveValue(cfg.fieldId, FieldStrategy.CONFIG, inputs);
                }

                return null;

            default:
                return null;
        }
    }

    private Map<Integer, FieldStrategy> getTemplateForMti(String mti) {
        Map<Integer, FieldStrategy> t = new HashMap<>();

        // COMMON FINANCIAL (0200, 0100/0200_BAL)
        if ("0200".equals(mti) || "0200_BAL".equals(mti) || "0100".equals(mti)) {
            t.put(2, FieldStrategy.INPUT); // M
            t.put(3, FieldStrategy.INPUT); // M
            t.put(4, FieldStrategy.INPUT); // M
            t.put(7, FieldStrategy.AUTO_DATE); // M
            t.put(11, FieldStrategy.AUTO_STAN); // M
            t.put(12, FieldStrategy.AUTO_DATE); // M
            t.put(13, FieldStrategy.AUTO_DATE); // M
            t.put(14, FieldStrategy.OPTIONAL_INPUT); // C (Expiry)
            t.put(15, FieldStrategy.AUTO_DATE); // M (Settlement)
            t.put(18, FieldStrategy.CONFIG); // M
            t.put(22, FieldStrategy.COMPUTED); // M (POS Mode)
            t.put(23, FieldStrategy.COMPUTED); // C (Card Seq)
            t.put(25, FieldStrategy.FIXED); // M (00)
            t.put(32, FieldStrategy.CONFIG); // M
            t.put(35, FieldStrategy.COMPUTED); // C (Track 2)
            t.put(37, FieldStrategy.AUTO_RRN); // M
            t.put(41, FieldStrategy.CONFIG); // M
            t.put(42, FieldStrategy.CONFIG); // M
            t.put(43, FieldStrategy.CONFIG); // M
            t.put(49, FieldStrategy.CONFIG); // M
            t.put(52, FieldStrategy.OPTIONAL_INPUT); // C
            t.put(55, FieldStrategy.COMPUTED); // C (Chip Data)
            t.put(60, FieldStrategy.COMPUTED); // O (Private)
        } else if ("0420".equals(mti)) {
            t.put(2, FieldStrategy.INPUT);
            t.put(3, FieldStrategy.INPUT);
            t.put(4, FieldStrategy.INPUT);
            t.put(7, FieldStrategy.INPUT);
            t.put(11, FieldStrategy.AUTO_STAN);
            t.put(12, FieldStrategy.INPUT);
            t.put(13, FieldStrategy.INPUT);
            t.put(18, FieldStrategy.CONFIG);
            t.put(22, FieldStrategy.COMPUTED);
            t.put(32, FieldStrategy.CONFIG);
            t.put(37, FieldStrategy.INPUT);
            t.put(41, FieldStrategy.CONFIG);
            t.put(42, FieldStrategy.CONFIG);
            t.put(49, FieldStrategy.CONFIG);
            t.put(55, FieldStrategy.COMPUTED);
            t.put(60, FieldStrategy.COMPUTED);
            t.put(90, FieldStrategy.COMPUTED);
        }
        return t;
    }

    private String resolveValue(int field, FieldStrategy strategy, Map<String, Object> inputs) {
        switch (strategy) {
            case FIXED:
                if (field == 25)
                    return "00";
                return null;

            case INPUT:
            case OPTIONAL_INPUT:
                return resolveInput(field, inputs);

            case CONFIG:
                if (field == 18)
                    return config.getMcc18();
                if (field == 32)
                    return config.getAcquirerId32();
                if (field == 41)
                    return config.getTerminalId();
                if (field == 42)
                    return config.getMerchantId();
                if (field == 43)
                    return IsoUtils.formatMerchantLocation(config.getMerchantName(), "HA NOI", "704");
                if (field == 49)
                    return config.getCurrencyCode49();
                return null;

            case AUTO_STAN:
                if (inputs.containsKey(KEY_STAN))
                    return (String) inputs.get(KEY_STAN);
                return config.getAndIncrementTrace();

            case AUTO_RRN:
                String stan = (String) inputs.get(KEY_STAN);
                return TransactionContext.calculateRrn("00", stan);

            case AUTO_DATE:
                Date now = new Date();
                if (field == 7)
                    return new SimpleDateFormat("MMddHHmmss", Locale.US).format(now);
                if (field == 12)
                    return new SimpleDateFormat("HHmmss", Locale.US).format(now);
                if (field == 13)
                    return new SimpleDateFormat("MMdd", Locale.US).format(now);
                if (field == 15)
                    return new SimpleDateFormat("MMdd", Locale.US).format(now);
                return null;

            case COMPUTED:
                if (field == 22)
                    return resolveDE22(inputs);
                if (field == 23)
                    return resolveDE23(inputs);
                if (field == 35)
                    return resolveDE35(inputs);
                if (field == 55)
                    return resolveDE55(inputs);
                if (field == 60)
                    return resolveDE60(inputs);
                return null;

            default:
                return null;
        }
    }

    private String resolveInput(int field, Map<String, Object> inputs) {
        switch (field) {
            case 2:
                return (String) inputs.get(KEY_PAN);
            case 3:
                return (String) inputs.get(KEY_PROC_CODE);
            case 4:
                String amt = (String) inputs.get(KEY_AMOUNT);
                return IsoUtils.formatAmount12(amt);
            case 14:
                return (String) inputs.get(KEY_EXPIRY);
            case 52:
                return (String) inputs.get(KEY_PIN_BLOCK);
            default:
                return null;
        }
    }

    // --- ISO LOGIC COMPLIANCE ---

    private String resolveDE22(Map<String, Object> inputs) {
        if (inputs.containsKey(KEY_POS_MODE)) {
            return (String) inputs.get(KEY_POS_MODE);
        }
        return "021";
    }

    private String resolveDE23(Map<String, Object> inputs) {
        // STRICT LOGIC: Present ONLY for Chip (05x) and Contactless (07x).
        // 01x (Manual), 02x (Mag), 08x (Fallback) MUST NOT send DE 23.
        String mode = resolveDE22(inputs);
        if (mode == null || (!mode.startsWith("05") && !mode.startsWith("07"))) {
            return null;
        }

        Object tagsObj = inputs.get(KEY_EMV_TAGS);
        if (tagsObj instanceof Map) {
            Map<String, String> tags = (Map<String, String>) tagsObj;
            String seq = tags.get("5F34");
            if (seq != null) {
                try {
                    int val = Integer.parseInt(seq);
                    return String.format(Locale.US, "%03d", val);
                } catch (NumberFormatException e) {
                    return "000";
                }
            }
        }
        return "000";
    }

    private String resolveDE35(Map<String, Object> inputs) {
        String track2 = (String) inputs.get(KEY_TRACK2);
        if (track2 == null || track2.isEmpty())
            return null;

        String mode = resolveDE22(inputs);
        // 05x/07x use 'D'. Others use '='.
        if (mode != null && (mode.startsWith("05") || mode.startsWith("07"))) {
            return track2.replace('=', 'D');
        } else {
            return track2.replace('D', '=');
        }
    }

    private String resolveDE55(Map<String, Object> inputs) {
        // STRICT LOGIC: Present ONLY for Chip (05x) and Contactless (07x).
        String mode = resolveDE22(inputs);
        if (mode == null || (!mode.startsWith("05") && !mode.startsWith("07"))) {
            return null;
        }

        Object tagsObj = inputs.get(KEY_EMV_TAGS);
        if (tagsObj instanceof Map) {
            return buildEmvString((Map<String, String>) tagsObj);
        }
        return null;
    }

    private String resolveDE60(Map<String, Object> inputs) {
        return "610300000000" + "000100000000000";
    }

    private String buildEmvString(Map<String, String> tags) {
        if (tags == null || tags.isEmpty())
            return null;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : tags.entrySet()) {
            String tag = e.getKey();
            String val = e.getValue();
            if (val == null)
                continue;

            sb.append(tag);
            int len = val.length() / 2;
            sb.append(String.format("%02X", len));
            sb.append(val);
        }
        return sb.toString();
    }
}
