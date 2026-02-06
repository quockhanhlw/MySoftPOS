package com.example.mysoftpos.testsuite;

import com.example.mysoftpos.utils.config.ConfigManager;

import com.example.mysoftpos.testsuite.model.TestScenario;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates ISO 8583 Test Cases based on strict Napas & OpenWay specifications.
 * Focuses on correct Service Codes (Track 2) and DE 55 presence.
 */
public class TestDataProvider {

    private static final String PAN = "9704189991010867647"; // Updated per User Request
    private static final String EXP_DATE = "3101"; // YYMM

    // Track 2 Templates (PAN + 'D' + ExpDate + ServiceCode + Discretionary)
    // 022 Magstripe: 9704189991010867647=31016010000000123
    private static final String TRACK2_SC_101 = "9704189991010867647D31016010000000123";

    public static List<TestScenario> generateAllScenarios(android.content.Context context) {
        return generateNapasTestCases(context);
    }

    /**
     * Generates strict Napas test cases based on DE 22 input.
     */
    public static List<TestScenario> generateNapasTestCases(android.content.Context context) {
        List<TestScenario> list = new ArrayList<>();

        String[] codes = {
                "022", "011", "012", "021"
        };

        for (String code : codes) {
            String modeStr = getModeName(code);
            // User Request: 021 should NOT have PIN code (even though it ends in 1)
            boolean isPin = code.endsWith("1") && !"021".equals(code);
            String desc = String.format("%s (%s)", modeStr, code);

            String mti = "0200";

            TestScenario s = new TestScenario(mti, desc);

            // Common Fields
            s.setField(3, "000000"); // Processing Code
            s.setField(22, code); // DE 22 (Entry Mode)

            // RULES: DE 35 (Track 2) & DE 55 (Chip Data) & DE 52 (PIN) Logic
            configureFieldsForCode(s, code, isPin, context);

            // Resolve Bank Name from PAN/Track 2
            String pan = s.getField(2);
            String track2 = s.getField(35);
            if (track2 != null && !track2.isEmpty()) {
                // Extract PAN from Track 2 (Separator D or =)
                String[] parts = track2.split("[D=]");
                if (parts.length > 0) {
                    pan = parts[0];
                }
            }
            String bankName = com.example.mysoftpos.utils.card.BinResolver.getBankName(pan);
            // Append Bank Name simply
            if (!bankName.equals("Unknown")) {
                s.setDescription(s.getDescription() + " - " + bankName);
            }

            list.add(s);
        }
        return list;
    }

    private static void configureFieldsForCode(TestScenario s, String code, boolean isPin,
            android.content.Context context) {
        // --- Rule 1: Load Data from ConfigManager (JSON) ---
        // Includes: Track 2, PAN (Manual), Expiry (Manual)
        com.example.mysoftpos.utils.config.ConfigManager config = com.example.mysoftpos.utils.config.ConfigManager
                .getInstance(context);

        com.example.mysoftpos.utils.config.ConfigManager.TestCaseConfig tcConfig = config.getTestCaseConfig(code);

        if (tcConfig != null) {
            // Set Track 2 if present
            if (tcConfig.track2 != null) {
                s.setField(35, tcConfig.track2);
            }

            // Set PAN/Expiry if present (Manual Entry)
            if (tcConfig.pan != null) {
                s.setField(2, tcConfig.pan);
            }
            if (tcConfig.expiry != null) {
                s.setField(14, tcConfig.expiry);
            }

            // Append Config Description
            if (tcConfig.description != null && !tcConfig.description.isEmpty()) {
                s.setDescription(s.getDescription() + " [" + tcConfig.description + "]");
            }
        } else {
            // Fallback for codes not in JSON (should not happen if JSON is complete)
            // Or handle legacy hardcoded defaults if needed?
            // User requested "use JSON", so strict JSON usage is preferred.
        }

        // --- Rule 2: DE 55 Presense ---
        // COMPLETELY REMOVED per User Request

        // --- Rule 3: PIN Block (DE 52) ---
        if (isPin) {
            s.setField(52, "PIN_BLOCK_PRESENT");
        }
    }

    private static String getModeName(String code) {
        if (code.startsWith("02"))
            return "Magstripe (Swipe)";
        if (code.startsWith("01"))
            return "Manual Key-in";
        if (code.startsWith("03"))
            return "QR Code";
        // 901/902 and Chip/NFC Removed as per user request
        return "Unknown";
    }
}
