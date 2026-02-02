package com.example.mysoftpos.testsuite;

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
    private static final String TRACK2_SC_201 = PAN + "D" + EXP_DATE + "201000000000"; // Chip
    private static final String TRACK2_SC_601 = PAN + "D" + EXP_DATE + "601000000000"; // Contactless

    // DE 55 Raw Data (Static Mock for Chip/NFC)
    private static final String DE55_MOCK = "9F2608ARQC12349F2701809F100A06011103A000009F3704RND19F36020001950500000080009A032301019C01005F2A02070482025800";

    public static List<TestScenario> generateAllScenarios() {
        return generateNapasTestCases();
    }

    /**
     * Generates strict Napas test cases based on DE 22 input.
     */
    public static List<TestScenario> generateNapasTestCases() {
        List<TestScenario> list = new ArrayList<>();

        String[] codes = {
                "022", "012", "031", "032",
                "051", "052", "071", "072",
                "102", "792", "801", "802",
                "901", "902", "911", "912"
        };

        for (String code : codes) {
            String modeStr = getModeName(code);
            boolean isPin = code.endsWith("1"); // 3rd digit '1' = PIN, '2' = No PIN
            String type = isPin ? "PIN" : "No-PIN";

            // Description based on Mode + Code + Type
            String desc = String.format("%s (%s) - %s", modeStr, code, type);
            String mti = "0200";

            TestScenario s = new TestScenario(mti, desc);

            // Common Fields
            s.setField(3, "000000"); // Processing Code
            s.setField(22, code); // DE 22 (Entry Mode)

            // RULES: DE 35 (Track 2) & DE 55 (Chip Data) & DE 52 (PIN) Logic
            configureFieldsForCode(s, code, isPin);

            list.add(s);
        }
        return list;
    }

    private static void configureFieldsForCode(TestScenario s, String code, boolean isPin) {
        // --- Rule 1: Service Code (Track 2) ---
        // Group A: Magstripe (022, 901, 902, 911, 912) -> SC 101
        if (code.equals("022") || code.startsWith("9")) {
            s.setField(35, TRACK2_SC_101);
        }
        // Group B: Chip Contact (051, 052) -> SC 201
        else if (code.startsWith("05")) {
            s.setField(35, TRACK2_SC_201);
        }
        // Group C: Contactless (071, 072) -> SC 601
        else if (code.startsWith("07")) {
            s.setField(35, TRACK2_SC_601);
        }
        // Group D: Fallback (801, 802) -> SC 201 (Chip card swiped)
        else if (code.startsWith("80")) {
            s.setField(35, TRACK2_SC_201);
        }
        // Group E: Barcode/QR (031, 032) -> SC 201
        else if (code.startsWith("03")) {
            s.setField(35, TRACK2_SC_201);
        }
        // Group F: Manual (012, 102, 792) -> No Track 2, set PAN/Exp explicitly
        else if (code.equals("012") || code.equals("102") || code.equals("792")) {
            s.setField(2, PAN);
            s.setField(14, EXP_DATE);
            // DE 35 is NULL (not set)
        }

        // --- Rule 2: DE 55 Presense ---
        // Include ONLY if DE 22 starts with "05" (Contact) or "07" (Contactless)
        if (code.startsWith("05") || code.startsWith("07")) {
            s.setField(55, DE55_MOCK);
        }
        // Explicitly EXCLUDE for 80x, 9xx, etc. (Already null by default, but enforced
        // logically)

        // --- Rule 3: PIN Block (DE 52) ---
        if (isPin) {
            s.setField(52, "PIN_BLOCK_PRESENT");
        }
    }

    private static String getModeName(String code) {
        if (code.startsWith("02"))
            return "Magstripe (Swipe)";
        if (code.startsWith("05"))
            return "EMV Chip";
        if (code.startsWith("07"))
            return "Contactless (NFC)";
        if (code.startsWith("01"))
            return "Manual Key-in";
        if (code.startsWith("03"))
            return "QR Code";
        if (code.startsWith("10"))
            return "Credential on File"; // 102
        if (code.startsWith("79"))
            return "Fallback > Manual"; // 792
        if (code.startsWith("80"))
            return "Fallback > Magstripe (Tech)"; // 801
        if (code.startsWith("90"))
            return "Fallback > Magstripe (Empty)"; // 901
        if (code.startsWith("91"))
            return "Fallback > Magstripe (Ctls)"; // 911
        return "Unknown";
    }
}
