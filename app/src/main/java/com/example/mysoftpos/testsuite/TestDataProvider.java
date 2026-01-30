package com.example.mysoftpos.testsuite;

import com.example.mysoftpos.testsuite.model.CardProfile;
import com.example.mysoftpos.testsuite.model.TestCase;
import java.util.ArrayList;
import java.util.List;

/**
 * Static provider for test data: cards and test scenarios.
 * 
 * DE 22 (POS Entry Mode) Reference:
 * ═══════════════════════════════════════════════════════════════════════════
 * | DE 22 | PAN Entry Mode           | PIN Capability                       |
 * |-------|--------------------------|--------------------------------------|
 * | 011   | Manual Entry             | Terminal CAN accept PIN              |
 * | 012   | Manual Entry             | Terminal CANNOT accept PIN (MOTO)    |
 * | 021   | Magnetic Stripe          | Terminal CAN accept PIN              |
 * | 022   | Magnetic Stripe          | Terminal CANNOT accept PIN           |
 * | 051   | Chip Read (Contact)      | Terminal CAN accept PIN              |
 * | 052   | Chip Read (Contact)      | Terminal CANNOT accept PIN (Bypass)  |
 * | 071   | Contactless Chip (NFC)   | Terminal CAN accept PIN (SoftPOS)    |
 * | 072   | Contactless Chip (NFC)   | Terminal CANNOT accept PIN (No-PIN)  |
 * | 081   | Fallback (Chip→Mag)      | Terminal CAN accept PIN              |
 * | 082   | Fallback (Chip→Mag)      | Terminal CANNOT accept PIN           |
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class TestDataProvider {

    // ═══════════════════════════════════════════════════════════════════
    //                         TEST CARDS
    // ═══════════════════════════════════════════════════════════════════
    
    public static List<CardProfile> getTestCards() {
        List<CardProfile> cards = new ArrayList<>();
        
        // NAPAS Test Cards
        cards.add(new CardProfile(
            "napas_chip_01",
            "NAPAS Chip Card 01",
            "9704189991010867647",
            "3101",
            "9704189991010867647=31016010000000123",
            "CHIP",
            "NAPAS"
        ));
        
        cards.add(new CardProfile(
            "bidv_chip_01",
            "BIDV Chip Card",
            "9704180000000001234",
            "2512",
            "9704180000000001234=25126010000000001",
            "CHIP",
            "BIDV"
        ));
        
        cards.add(new CardProfile(
            "agribank_mag_01",
            "Agribank Magstripe",
            "9704060000000009876",
            "2612",
            "9704060000000009876=26126010000000002",
            "MAGSTRIPE",
            "Agribank"
        ));
        
        cards.add(new CardProfile(
            "vietcombank_nfc_01",
            "VCB Contactless",
            "9704360000000005678",
            "2712",
            "9704360000000005678=27126010000000003",
            "NFC",
            "Vietcombank"
        ));
        
        cards.add(new CardProfile(
            "techcombank_chip_01",
            "TCB Chip Card",
            "9704380000000004321",
            "2812",
            "9704380000000004321=28126010000000004",
            "CHIP",
            "Techcombank"
        ));
        
        return cards;
    }

    // ═══════════════════════════════════════════════════════════════════
    //                         POS TEST CASES (DE 22 Based)
    // ═══════════════════════════════════════════════════════════════════
    
    public static List<TestCase> getPosTestCases() {
        List<TestCase> cases = new ArrayList<>();
        
        // 011: Key-in + PIN
        cases.add(new TestCase("pos_manual_pin", "Manual + PIN", "Key-in Card + PIN", "0200", "000000", "011", "POS", true, true));
        
        // 012: Key-in
        cases.add(new TestCase("pos_manual_nopin", "Manual No-PIN", "Key-in Card (No PIN)", "0200", "000000", "012", "POS", true, false));

        // 021: Stripe reader + PIN
        cases.add(new TestCase("pos_swipe_pin", "Magstripe + PIN", "Swipe Card + PIN", "0200", "000000", "021", "POS", true, true));

        // 022: Stripe reader
        cases.add(new TestCase("pos_swipe_nopin", "Magstripe No-PIN", "Swipe Card (No PIN)", "0200", "000000", "022", "POS", true, false));

        // 051: ICC + PIN
        cases.add(new TestCase("pos_chip_pin", "Chip + PIN", "Insert Chip + PIN", "0200", "000000", "051", "POS", true, true));

        // 052: ICC
        cases.add(new TestCase("pos_chip_nopin", "Chip No-PIN", "Insert Chip (No PIN)", "0200", "000000", "052", "POS", true, false));

        // 071: Contactless Chip + PIN
        cases.add(new TestCase("pos_nfc_pin", "NFC + PIN", "Tap Card + PIN", "0200", "000000", "071", "POS", true, true));

        // 072: Contactless Chip
        cases.add(new TestCase("pos_nfc_nopin", "NFC No-PIN", "Tap Card (No PIN)", "0200", "000000", "072", "POS", true, false));

        // 081: Fallback Chip -> magnetic stripe-read + PIN
        cases.add(new TestCase("pos_fallback_pin", "Fallback + PIN", "Fallback (Chip->Swipe) + PIN", "0200", "000000", "081", "POS", true, true));

        // 082: Fallback Chip -> magnetic stripe-read
        cases.add(new TestCase("pos_fallback_nopin", "Fallback No-PIN", "Fallback (Chip->Swipe) No PIN", "0200", "000000", "082", "POS", true, false));

        return cases;
    }
    
    public static List<TestCase> getAtmTestCases() {
        List<TestCase> cases = new ArrayList<>();
        
        cases.add(new TestCase(
            "atm_withdrawal",
            "Cash Withdrawal",
            "ATM Cash withdrawal",
            "0200",
            "010000",
            "051",  // DE 22: Chip + PIN
            "ATM",
            true,
            true
        ));
        
        cases.add(new TestCase(
            "atm_balance",
            "Balance Inquiry",
            "ATM Balance check",
            "0200",
            "310000",
            "051",  // DE 22: Chip + PIN
            "ATM",
            false,
            true
        ));
        
        return cases;
    }
    
    public static List<TestCase> getQrcTestCases() {
        List<TestCase> cases = new ArrayList<>();
        
        cases.add(new TestCase(
            "qrc_purchase",
            "QR Purchase",
            "QR Code based purchase",
            "0200",
            "000000",
            "010",  // DE 22: QR specific
            "QRC",
            true,
            false
        ));
        
        return cases;
    }

    // ═══════════════════════════════════════════════════════════════════
    //                         SCHEMES
    // ═══════════════════════════════════════════════════════════════════
    
    public static List<String> getSchemes() {
        List<String> schemes = new ArrayList<>();
        schemes.add("NAPAS");
        schemes.add("VISA");
        schemes.add("MASTERCARD");
        return schemes;
    }
}
