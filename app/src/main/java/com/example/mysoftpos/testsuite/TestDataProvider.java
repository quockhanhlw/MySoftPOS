package com.example.mysoftpos.testsuite;

import com.example.mysoftpos.testsuite.model.TestScenario;
import java.util.ArrayList;
import java.util.List;

public class TestDataProvider {

    /**
     * Generate Test Scenarios based on specific DE 22 codes request.
     * Codes: 022, 012, 031, 032, 051, 052, 071, 072, 102, 792, 801, 802, 901, 902,
     * 911, 912.
     * Logic:
     * - Ends with 1 -> PIN Required.
     * - Ends with 2 -> No PIN.
     * - "022", "012", "072" (No PIN) -> Logic same as original 022.
     */
    public static List<TestScenario> generateAllScenarios() {
        List<TestScenario> list = new ArrayList<>();

        // List of requested DE 22 codes
        String[] codes = {
                "022", "012", "031", "032",
                "051", "052", "071", "072",
                "102", "792", "801", "802",
                "901", "902", "911", "912"
        };

        for (String code : codes) {
            boolean isPin = code.endsWith("1");
            String type = isPin ? "PIN" : "No-PIN";
            String modeStr = getModeName(code);

            // Description format: "[Mode] [Code] - [Type]"
            String desc = String.format("%s (%s) - %s", modeStr, code, type);
            String mti = "0200"; // Default Purchase

            TestScenario s = new TestScenario(mti, desc);
            s.setField(3, "000000"); // Processing Code
            s.setField(22, code); // THE CRITICAL FIELD

            if (isPin) {
                s.setField(52, "PIN_BLOCK_PRESENT"); // Marker for PIN requirement
            }

            list.add(s);
        }
        return list;
    }

    private static String getModeName(String code) {
        if (code.startsWith("02"))
            return "Magstripe";
        if (code.startsWith("05"))
            return "Chip";
        if (code.startsWith("07"))
            return "NFC";
        if (code.startsWith("01"))
            return "Manual";
        if (code.startsWith("91"))
            return "FallBack";
        return "Unknown Mode";
    }
}
