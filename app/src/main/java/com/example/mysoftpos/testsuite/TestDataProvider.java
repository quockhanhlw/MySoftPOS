package com.example.mysoftpos.testsuite;

import com.example.mysoftpos.iso8583.emv.EmvTlvCodec;
import com.example.mysoftpos.R;
import com.example.mysoftpos.testsuite.model.TestScenario;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates ISO 8583 Test Cases based on strict Napas & OpenWay specifications.
 * Focuses on correct Service Codes (Track 2) and DE 55 presence.
 */
public class TestDataProvider {

    // Test Card Data - Hardcoded
    private static final String PAN_1 = "9704189991010867647";
    private static final String EXP_1 = "3101";
    private static final String TRACK2_1 = "9704189991010867647=31016010000000123";

    private static final String PAN_2 = "9704186870000505297";
    private static final String EXP_2 = "2808";
    private static final String TRACK2_2 = "9704186870000505297=28086010000000456";

    private static final String PAN_3 = "9704180000000001";
    private static final String EXP_3 = "2512";
    private static final String TRACK2_3 = "9704180000000001=25126010000000789";

    public static List<TestScenario> generateAllScenarios(android.content.Context context) {
        return generateNapasTestCases(context);
    }

    /**
     * Generates test cases based on scheme.
     * Only Napas has preset test cases; others return empty list.
     */
    public static List<TestScenario> generateScenarios(android.content.Context context, String scheme) {
        if (scheme == null)
            return new ArrayList<>();
        switch (scheme.toLowerCase()) {
            case "napas":
                return generateNapasTestCases(context);
            default:
                // Visa, Mastercard, custom schemes: no preset test cases
                return new ArrayList<>();
        }
    }

    /**
     * Generates strict Napas test cases based on DE 22 input.
     */
    public static List<TestScenario> generateNapasTestCases(android.content.Context context) {
        List<TestScenario> list = new ArrayList<>();

        String[] codes = {
                "022", "011", "012", "021", "071", "072"
        };

        for (String code : codes) {
            String modeStr = getModeName(context, code);
            boolean isPin = code.endsWith("1") && !"021".equals(code);
            String desc;
            if ("071".equals(code)) {
                desc = context.getString(R.string.testsuite_desc_contactless_pin_071);
            } else if ("072".equals(code)) {
                desc = context.getString(R.string.testsuite_desc_contactless_no_pin_072);
            } else {
                desc = context.getString(R.string.testsuite_desc_mode_de22_format, modeStr, code);
            }

            TestScenario s = new TestScenario("0200", desc);

            // Common Fields
            // Do not hardcode DE3 here.
            // Processing code must be set by runtime txn type mapping
            // (PURCHASE -> 000000, BALANCE -> 300000).
            s.setField(22, code);

            // Configure fields based on code
            configureFieldsForCode(s, code, isPin);

            list.add(s);
        }
        return list;
    }

    private static void configureFieldsForCode(TestScenario s, String code, boolean isPin) {
        // Set Track 2 / PAN / Expiry based on DE 22 code
        switch (code) {
            case "011": // Manual Key-in with PIN
                s.setField(2, PAN_1);
                s.setField(14, EXP_1);
                break;
            case "012": // Manual Key-in without PIN
                s.setField(2, PAN_2);
                s.setField(14, EXP_2);
                break;
            case "021": // Magstripe with PIN
                s.setField(35, TRACK2_1);
                s.setField(14, EXP_1);
                break;
            case "022": // Magstripe without PIN
                s.setField(35, TRACK2_1);
                s.setField(14, EXP_1);
                break;
            case "071": // Contactless with PIN
                s.setField(2, PAN_2);
                s.setField(14, EXP_2);
                s.setField(35, null);
                s.setField(55, buildTag57OnlyDe55(PAN_2, EXP_2, "601", "0000000456"));
                break;
            case "072": // Contactless without PIN
                s.setField(2, PAN_2);
                s.setField(14, EXP_2);
                s.setField(35, null);
                s.setField(55, buildTag57OnlyDe55(PAN_2, EXP_2, "601", "0000000456"));
                break;
            default:
                s.setField(35, TRACK2_1);
                s.setField(14, EXP_1);
                break;
        }

        // PIN Block (DE 52)
        if (isPin) {
            s.setField(52, "PIN_BLOCK_PRESENT");
        }
    }

    private static String buildTag57OnlyDe55(String pan, String expiryYymm, String serviceCode, String discretionary) {
        Map<Integer, byte[]> tags = new LinkedHashMap<>();
        tags.put(
                EmvTlvCodec.TAG_TRACK2_EQUIVALENT,
                EmvTlvCodec.encodeTag57(pan, expiryYymm, serviceCode, discretionary));
        return EmvTlvCodec.buildDE55(tags);
    }

    private static String getModeName(android.content.Context context, String code) {
        if (code.startsWith("02"))
            return context.getString(R.string.testsuite_mode_magstripe_swipe);
        if (code.startsWith("01"))
            return context.getString(R.string.testsuite_mode_manual_keyin);
        if (code.startsWith("07"))
            return context.getString(R.string.testsuite_mode_contactless);
        if (code.startsWith("03"))
            return context.getString(R.string.testsuite_mode_qr_code);
        return context.getString(R.string.testsuite_mode_unknown);
    }
}
