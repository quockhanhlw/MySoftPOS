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
        
        // ─────────────────────────────────────────────────────────────────
        // DE 22 = 011: Manual Entry + PIN Capable
        // ─────────────────────────────────────────────────────────────────
        cases.add(new TestCase(
            "pos_manual_pin",
            "Manual Entry + PIN",
            "Nhập tay số thẻ + Thiết bị CÓ bàn phím PIN",
            "0200",
            "000000",
            "011",  // DE 22
            "POS",
            true,
            true
        ));
        
        // ─────────────────────────────────────────────────────────────────
        // DE 22 = 012: Manual Entry + No PIN (MOTO)
        // ─────────────────────────────────────────────────────────────────
        cases.add(new TestCase(
            "pos_manual_nopin",
            "Manual Entry (MOTO)",
            "Nhập tay số thẻ + Thiết bị KHÔNG có bàn phím PIN (Voice Auth)",
            "0200",
            "000000",
            "012",  // DE 22
            "POS",
            true,
            false
        ));
        
        // ─────────────────────────────────────────────────────────────────
        // DE 22 = 021: Magnetic Stripe + PIN Capable
        // ─────────────────────────────────────────────────────────────────
        cases.add(new TestCase(
            "pos_swipe_pin",
            "Swipe + PIN",
            "Quẹt băng từ + Thiết bị CÓ bàn phím PIN (POS truyền thống)",
            "0200",
            "000000",
            "021",  // DE 22
            "POS",
            true,
            true
        ));
        
        // ─────────────────────────────────────────────────────────────────
        // DE 22 = 022: Magnetic Stripe + No PIN
        // ─────────────────────────────────────────────────────────────────
        cases.add(new TestCase(
            "pos_swipe_nopin",
            "Swipe Only",
            "Quẹt băng từ + Thiết bị KHÔNG có bàn phím PIN",
            "0200",
            "000000",
            "022",  // DE 22
            "POS",
            true,
            false
        ));
        
        // ─────────────────────────────────────────────────────────────────
        // DE 22 = 051: Chip Contact + PIN Capable
        // ─────────────────────────────────────────────────────────────────
        cases.add(new TestCase(
            "pos_chip_pin",
            "Chip + PIN",
            "Đọc qua Chip + Thiết bị CÓ bàn phím PIN (Giao dịch Chip chuẩn)",
            "0200",
            "000000",
            "051",  // DE 22
            "POS",
            true,
            true
        ));
        
        // ─────────────────────────────────────────────────────────────────
        // DE 22 = 052: Chip Contact + No PIN (Bypass)
        // ─────────────────────────────────────────────────────────────────
        cases.add(new TestCase(
            "pos_chip_nopin",
            "Chip (PIN Bypass)",
            "Đọc qua Chip + Thiết bị KHÔNG có bàn phím PIN (PIN Bypass)",
            "0200",
            "000000",
            "052",  // DE 22
            "POS",
            true,
            false
        ));
        
        // ─────────────────────────────────────────────────────────────────
        // DE 22 = 071: Contactless Chip + PIN Capable (SoftPOS Standard)
        // ─────────────────────────────────────────────────────────────────
        cases.add(new TestCase(
            "pos_nfc_pin",
            "NFC + PIN (SoftPOS)",
            "Chạm thẻ Chip (Contactless) + Thiết bị CÓ bàn phím PIN",
            "0200",
            "000000",
            "071",  // DE 22
            "POS",
            true,
            true
        ));
        
        // ─────────────────────────────────────────────────────────────────
        // DE 22 = 072: Contactless Chip + No PIN (Small Value)
        // ─────────────────────────────────────────────────────────────────
        cases.add(new TestCase(
            "pos_nfc_nopin",
            "NFC No-PIN",
            "Chạm thẻ Chip + Thiết bị KHÔNG có bàn phím PIN (Giao dịch giá trị nhỏ)",
            "0200",
            "000000",
            "072",  // DE 22
            "POS",
            true,
            false
        ));
        
        // ─────────────────────────────────────────────────────────────────
        // DE 22 = 081: Fallback (Chip→Mag) + PIN Capable
        // ─────────────────────────────────────────────────────────────────
        cases.add(new TestCase(
            "pos_fallback_pin",
            "Fallback + PIN",
            "Fallback từ Chip sang Từ + Thiết bị CÓ bàn phím PIN",
            "0200",
            "000000",
            "081",  // DE 22
            "POS",
            true,
            true
        ));
        
        // ─────────────────────────────────────────────────────────────────
        // DE 22 = 082: Fallback (Chip→Mag) + No PIN
        // ─────────────────────────────────────────────────────────────────
        cases.add(new TestCase(
            "pos_fallback_nopin",
            "Fallback No-PIN",
            "Fallback từ Chip sang Từ + Thiết bị KHÔNG có bàn phím PIN",
            "0200",
            "000000",
            "082",  // DE 22
            "POS",
            true,
            false
        ));
        
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
        schemes.add("JCB");
        return schemes;
    }
}
