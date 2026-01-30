package com.example.mysoftpos.testsuite;

import com.example.mysoftpos.testsuite.model.TestScenario;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Senior Developer Implementation of TestDataProvider.
 * Generates comprehensive test cases strictly following NAPAS ISO 8583
 * Specification.
 */
public class TestDataProvider {

    private static int stanCounter = 1;

    public static List<TestScenario> generateAllScenarios() {
        List<TestScenario> scenarios = new ArrayList<>();

        // Group A: Chip/NFC (DE 22 = 051, 052, 071, 072)
        scenarios.add(createScenario("051", "Chip + PIN", true, true));
        scenarios.add(createScenario("052", "Chip No-PIN", true, false));
        scenarios.add(createScenario("071", "NFC + PIN", true, true));
        scenarios.add(createScenario("072", "NFC No-PIN", true, false));

        // Group B: Swipe/MSD (DE 22 = 901, 902, 911, 912)
        scenarios.add(createScenario("901", "Swipe MSD + PIN", false, true));
        scenarios.add(createScenario("902", "Swipe MSD No-PIN", false, false));
        scenarios.add(createScenario("911", "Swipe Mode 91 + PIN", false, true));
        scenarios.add(createScenario("912", "Swipe Mode 91 No-PIN", false, false));

        // Group C: Fallback (DE 22 = 801, 802)
        scenarios.add(createScenario("801", "Fallback + PIN", false, true, "201"));
        scenarios.add(createScenario("802", "Fallback No-PIN", false, false, "201"));

        // Group D: Manual (DE 22 = 012, 102)
        scenarios.add(createManualScenario("012", "Manual Entry MOTO"));
        scenarios.add(createManualScenario("102", "Electronic Commerce"));

        return scenarios;
    }

    private static TestScenario createScenario(String de22, String desc, boolean isChip, boolean hasPin) {
        // Use default service code 201 for chip, 101 for swipe
        String svcCode = isChip ? "201" : "101";
        return createScenario(de22, desc, isChip, hasPin, svcCode);
    }

    private static TestScenario createScenario(String de22, String desc, boolean isChip, boolean hasPin,
            String svcCode) {
        TestScenario ts = new TestScenario("0200", desc);
        Date now = new Date();
        String stan = String.format(Locale.US, "%06d", stanCounter++);

        ts.setField(2, "9704189991010867647");
        ts.setField(3, "000000");
        ts.setField(4, "000000010000"); // 100.00 VND
        ts.setField(7, new SimpleDateFormat("MMddHHmmss", Locale.US).format(now));
        ts.setField(11, stan);
        ts.setField(12, new SimpleDateFormat("HHmmss", Locale.US).format(now));
        ts.setField(13, new SimpleDateFormat("MMdd", Locale.US).format(now));
        ts.setField(14, "3101"); // Jan 2031
        ts.setField(15, new SimpleDateFormat("MMdd", Locale.US).format(now));
        ts.setField(18, "5411");
        ts.setField(22, de22);
        ts.setField(25, "00");
        ts.setField(32, "970406");
        ts.setField(35, "9704189991010867647=" + svcCode + "6010000000123");
        ts.setField(37, calculateRrn(stan));
        ts.setField(41, "AUTO0001");
        ts.setField(42, "MYSOFTPOSSHOP01");
        ts.setField(43, formatDE43("MYSOFTPOS BANK", "HA NOI", "704"));
        ts.setField(49, "704");

        if (hasPin) {
            ts.setField(52, "BF49E3..."); // Placeholder binary hex
        }

        if (isChip) {
            ts.setField(55, "9F2608E293...9F36020209"); // Placeholder TLV
        }

        return ts;
    }

    private static TestScenario createManualScenario(String de22, String desc) {
        TestScenario ts = new TestScenario("0200", desc);
        Date now = new Date();
        String stan = String.format(Locale.US, "%06d", stanCounter++);

        ts.setField(2, "9704189991010867647");
        ts.setField(3, "000000");
        ts.setField(4, "000000010000");
        ts.setField(7, new SimpleDateFormat("MMddHHmmss", Locale.US).format(now));
        ts.setField(11, stan);
        ts.setField(12, new SimpleDateFormat("HHmmss", Locale.US).format(now));
        ts.setField(13, new SimpleDateFormat("MMdd", Locale.US).format(now));
        ts.setField(14, "3101");
        ts.setField(18, "5411");
        ts.setField(22, de22);
        ts.setField(25, "00");
        ts.setField(32, "970406");
        ts.setField(37, calculateRrn(stan));
        ts.setField(41, "AUTO0001");
        ts.setField(42, "MYSOFTPOSSHOP01");
        ts.setField(43, formatDE43("MYSOFTPOS BANK", "HA NOI", "704"));
        ts.setField(49, "704");

        return ts;
    }

    private static String calculateRrn(String stan) {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR) % 10;
        int julianDate = cal.get(Calendar.DAY_OF_YEAR);
        String serverId = "01";
        return String.format(Locale.US, "%d%03d%s%6s", year, julianDate, serverId, stan);
    }

    private static String formatDE43(String name, String city, String country) {
        // Name (22) + Space + City (13) + Space + Country (3)
        return String.format(Locale.US, "%-22s %-13s %3s", name, city, country);
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEGACY SUPPORT (BACKWARD COMPATIBILITY)
    // ═══════════════════════════════════════════════════════════════════

    public static List<com.example.mysoftpos.testsuite.model.CardProfile> getTestCards() {
        List<com.example.mysoftpos.testsuite.model.CardProfile> cards = new ArrayList<>();
        cards.add(new com.example.mysoftpos.testsuite.model.CardProfile("napas_chip_01", "NAPAS Chip Card 01",
                "9704189991010867647", "3101", "9704189991010867647=31016010000000123", "CHIP", "NAPAS"));
        cards.add(new com.example.mysoftpos.testsuite.model.CardProfile("bidv_chip_01", "BIDV Chip Card",
                "9704180000000001234", "2512", "9704180000000001234=25126010000000001", "CHIP", "BIDV"));
        return cards;
    }

    public static List<com.example.mysoftpos.testsuite.model.TestCase> getPosTestCases() {
        List<com.example.mysoftpos.testsuite.model.TestCase> cases = new ArrayList<>();
        cases.add(new com.example.mysoftpos.testsuite.model.TestCase("pos_chip_pin", "Chip + PIN",
                "Insert Chip + PIN", "0200", "000000", "051", "POS", true, true));
        cases.add(new com.example.mysoftpos.testsuite.model.TestCase("pos_nfc_nopin", "NFC No-PIN",
                "Tap Card (No PIN)", "0200", "000000", "072", "POS", true, false));
        return cases;
    }

    public static List<com.example.mysoftpos.testsuite.model.TestCase> getAtmTestCases() {
        List<com.example.mysoftpos.testsuite.model.TestCase> cases = new ArrayList<>();
        cases.add(new com.example.mysoftpos.testsuite.model.TestCase("atm_withdrawal", "Cash Withdrawal",
                "ATM Cash withdrawal", "0200", "010000", "051", "ATM", true, true));
        return cases;
    }

    public static List<com.example.mysoftpos.testsuite.model.TestCase> getQrcTestCases() {
        List<com.example.mysoftpos.testsuite.model.TestCase> cases = new ArrayList<>();
        cases.add(new com.example.mysoftpos.testsuite.model.TestCase("qrc_purchase", "QR Purchase",
                "QR Code based purchase", "0200", "000000", "010", "QRC", true, false));
        return cases;
    }

    public static List<String> getSchemes() {
        List<String> schemes = new ArrayList<>();
        schemes.add("NAPAS");
        schemes.add("VISA");
        return schemes;
    }
}
