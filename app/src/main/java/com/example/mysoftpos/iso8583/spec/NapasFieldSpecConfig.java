package com.example.mysoftpos.iso8583.spec;

import com.example.mysoftpos.iso8583.spec.NapasFieldSpecConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class NapasFieldSpecConfig {

    private NapasFieldSpecConfig() {
    }

    public static final class FieldSpec {
        public final int field;
        public final int minLen;
        public final int maxLen;
        public final Pattern pattern;
        public final String errorCode;
        public final String description;

        public FieldSpec(int field, int minLen, int maxLen, String regex, String errorCode, String description) {
            this.field = field;
            this.minLen = minLen;
            this.maxLen = maxLen;
            this.pattern = regex == null ? null : Pattern.compile(regex);
            this.errorCode = errorCode;
            this.description = description;
        }
    }

    public static final Map<Integer, FieldSpec> SPECS = build();

    private static Map<Integer, FieldSpec> build() {
        Map<Integer, FieldSpec> m = new HashMap<>();

        // MTI (Message Type Identifier) - n4
        m.put(0, new FieldSpec(0, 4, 4, "\\d{4}", "NPS-000", "MTI Error: Must be 4 numeric digits"));

        // Secondary Bitmap - n16 (Hex)
        m.put(1, new FieldSpec(1, 16, 16, "[0-9A-Fa-f]{16}", "NPS-001",
                "Secondary Bitmap Error: Must be 16 Hex characters"));

        // DE 2: Primary Account Number (PAN) - an..19; LLVAR
        m.put(2, new FieldSpec(2, 1, 19, "\\d{1,19}", "NPS-002", "PAN Error: Must be numeric, length 1-19 (LLVAR)"));

        // DE 3: Processing Code - n6
        m.put(3, new FieldSpec(3, 6, 6, "\\d{6}", "NPS-003", "Processing Code Error: Must be 6 numeric digits"));

        // DE 4: Transaction Amount - n12
        m.put(4, new FieldSpec(4, 12, 12, "\\d{12}", "NPS-004", "Amount Error: Must be 12 numeric digits"));

        // DE 5: Settlement Amount - n12
        m.put(5, new FieldSpec(5, 12, 12, "\\d{12}", "NPS-005", "Settlement Amount Error: Must be 12 numeric digits"));

        // DE 6: Cardholder Billing Amount - n12
        m.put(6, new FieldSpec(6, 12, 12, "\\d{12}", "NPS-006", "Billing Amount Error: Must be 12 numeric digits"));

        // DE 7: Transmission Date & Time - n10 (MMDDhhmmss)
        m.put(7, new FieldSpec(7, 10, 10, "\\d{10}", "NPS-007",
                "Transmission Date/Time Error: Must be 10 numeric digits (MMDDhhmmss)"));

        // DE 9: Settlement Conversion Rate - n8
        m.put(9, new FieldSpec(9, 8, 8, "\\d{8}", "NPS-009", "Settlement Rate Error: Must be 8 numeric digits"));

        // DE 10: Cardholder Billing Conversion Rate - n8
        m.put(10, new FieldSpec(10, 8, 8, "\\d{8}", "NPS-010", "Billing Rate Error: Must be 8 numeric digits"));

        // DE 11: System Trace Audit Number (STAN) - n6
        m.put(11, new FieldSpec(11, 6, 6, "\\d{6}", "NPS-011", "STAN Error: Must be 6 numeric digits"));

        // DE 12: Local Transaction Time - n6 (hhmmss)
        m.put(12, new FieldSpec(12, 6, 6, "\\d{6}", "NPS-012", "Local Time Error: Must be 6 numeric digits (hhmmss)"));

        // DE 13: Local Transaction Date - n4 (MMDD)
        m.put(13, new FieldSpec(13, 4, 4, "\\d{4}", "NPS-013", "Local Date Error: Must be 4 numeric digits (MMDD)"));

        // DE 14: Expiration Date - n4 (YYMM)
        m.put(14,
                new FieldSpec(14, 4, 4, "\\d{4}", "NPS-014", "Expiration Date Error: Must be 4 numeric digits (YYMM)"));

        // DE 15: Settlement Date - n4 (MMDD)
        m.put(15,
                new FieldSpec(15, 4, 4, "\\d{4}", "NPS-015", "Settlement Date Error: Must be 4 numeric digits (MMDD)"));

        // DE 18: Merchant Category Code - n4
        m.put(18, new FieldSpec(18, 4, 4, "\\d{4}", "NPS-018", "MCC Error: Must be 4 numeric digits"));

        // DE 19: Acquiring Institution Country Code - n3
        m.put(19, new FieldSpec(19, 3, 3, "\\d{3}", "NPS-019", "Country Code Error: Must be 3 numeric digits"));

        // DE 22: POS Entry Mode - n3
        m.put(22, new FieldSpec(22, 3, 3, "\\d{3}", "NPS-022", "POS Entry Mode Error: Must be 3 numeric digits"));

        // DE 23: Card Sequence Number - n3
        m.put(23, new FieldSpec(23, 3, 3, "\\d{3}", "NPS-023", "Card Sequence Number Error: Must be 3 numeric digits"));

        // DE 25: POS Condition Code - n2
        m.put(25, new FieldSpec(25, 2, 2, "\\d{2}", "NPS-025", "POS Condition Code Error: Must be 2 numeric digits"));

        // DE 32: Acquiring Institution ID Code - n..11; LLVAR
        m.put(32, new FieldSpec(32, 1, 11, "\\d{1,11}", "NPS-032",
                "Acquirer ID Error: Must be numeric, max length 11 (LLVAR)"));

        // DE 35: Track 2 Data - z..37; LLVAR
        m.put(35, new FieldSpec(35, 1, 37, "[0-9=D]{1,37}", "NPS-035",
                "Track 2 Error: Invalid format or length > 37 (LLVAR)"));

        // DE 36: Track 3 Data - z..104; LLVAR
        m.put(36, new FieldSpec(36, 1, 104, "[0-9=D]{1,104}", "NPS-036",
                "Track 3 Error: Invalid format or length > 104 (LLVAR)"));

        // DE 37: Retrieval Reference Number (RRN) - an12
        m.put(37, new FieldSpec(37, 12, 12, "[a-zA-Z0-9]{12}", "NPS-037",
                "RRN Error: Must be 12 alphanumeric characters"));

        // DE 38: Auth Identification Response - ans6
        m.put(38, new FieldSpec(38, 6, 6, "[ -~]{6}", "NPS-038", "Auth Code Error: Must be 6 characters"));

        // DE 39: Response Code - an2
        m.put(39, new FieldSpec(39, 2, 2, "[a-zA-Z0-9]{2}", "NPS-039",
                "Response Code Error: Must be 2 alphanumeric characters"));

        // DE 41: Card Acceptor Terminal ID (TID) - ans8
        m.put(41, new FieldSpec(41, 8, 8, "[ -~]{8}", "NPS-041", "TID Error: Must be 8 characters"));

        // DE 42: Card Acceptor ID (MID) - ans15
        m.put(42, new FieldSpec(42, 15, 15, "[ -~]{15}", "NPS-042", "Merchant ID Error: Must be 15 characters"));

        // DE 43: Card Acceptor Name/Location - ans40
        m.put(43, new FieldSpec(43, 40, 40, "[ -~]{40}", "NPS-043", "Location Error: Must be 40 characters"));

        // DE 45: Track-1 Data - ans...79, LLVAR
        m.put(45, new FieldSpec(45, 1, 79, "[ -~]{1,79}", "NPS-045", "Track 1 Error: Max length 79 (LLVAR)"));

        // DE 48: Additional Private Data - ans..999; LLLVAR
        m.put(48, new FieldSpec(48, 1, 999, "[ -~]{1,999}", "NPS-048", "Private Data Error: Max length 999 (LLLVAR)"));

        // DE 49: Transaction Currency Code - n3
        m.put(49, new FieldSpec(49, 3, 3, "\\d{3}", "NPS-049", "Currency Code Error: Must be 3 numeric digits"));

        // DE 50: Settlement Currency Code - n3
        m.put(50, new FieldSpec(50, 3, 3, "\\d{3}", "NPS-050", "Settlement Currency Error: Must be 3 numeric digits"));

        // DE 51: Cardholder Billing Currency Code - n3
        m.put(51, new FieldSpec(51, 3, 3, "\\d{3}", "NPS-051", "Billing Currency Error: Must be 3 numeric digits"));

        // DE 52: PIN Data - an16 (Hex)
        m.put(52,
                new FieldSpec(52, 16, 16, "[0-9A-Fa-f]{16}", "NPS-052", "PIN Block Error: Must be 16 Hex characters"));

        // DE 54: Additional Amounts - ans..120; LLLVAR
        m.put(54, new FieldSpec(54, 1, 120, "[ -~]{1,120}", "NPS-054",
                "Additional Amounts Error: Max length 120 (LLLVAR)"));

        // DE 60: Self-Defined Field - ans..60; LLLVAR
        m.put(60, new FieldSpec(60, 1, 60, "[ -~]{1,60}", "NPS-060", "Field 60 Error: Max length 60 (LLLVAR)"));

        // DE 62: Service Code - ans..10; LLVAR
        m.put(62, new FieldSpec(62, 1, 10, "[ -~]{1,10}", "NPS-062", "Service Code Error: Max length 10 (LLVAR)"));

        // DE 63: Transaction Reference Number (TRN) - ans..16; LLLVAR
        m.put(63, new FieldSpec(63, 1, 16, "[ -~]{1,16}", "NPS-063", "TRN Error: Max length 16 (LLLVAR)"));

        // DE 70: Network Management Info Code - n3
        m.put(70, new FieldSpec(70, 3, 3, "\\d{3}", "NPS-070", "Net Mgmt Code Error: Must be 3 numeric digits"));

        // DE 90: Original Data Elements - n42
        m.put(90, new FieldSpec(90, 42, 42, "\\d{42}", "NPS-090", "Original Data Error: Must be 42 numeric digits"));

        // DE 100: Receiving Institution Country Code - n11, LLVAR
        m.put(100,
                new FieldSpec(100, 1, 11, "\\d{1,11}", "NPS-100", "Receiving Inst. Code Error: Max length 11 (LLVAR)"));

        // DE 102: From Account Identification - an...28, LLVAR
        m.put(102, new FieldSpec(102, 1, 28, "[a-zA-Z0-9]{1,28}", "NPS-102",
                "From Account Error: Alphanumeric, max length 28 (LLVAR)"));

        // DE 103: To Account Identification - an...28, LLVAR
        m.put(103, new FieldSpec(103, 1, 28, "[a-zA-Z0-9]{1,28}", "NPS-103",
                "To Account Error: Alphanumeric, max length 28 (LLVAR)"));

        // DE 104: Content Transfer - ans...210, LLLVAR
        m.put(104, new FieldSpec(104, 1, 210, "[ -~]{1,210}", "NPS-104",
                "Content Transfer Error: Max length 210 (LLLVAR)"));

        // DE 105: New PIN Block - ans...999, LLLVAR
        m.put(105,
                new FieldSpec(105, 1, 999, "[ -~]{1,999}", "NPS-105", "New PIN Block Error: Max length 999 (LLLVAR)"));

        // DE 120: Beneficial Card holder Info - ans...70, LLLVAR
        m.put(120,
                new FieldSpec(120, 1, 70, "[ -~]{1,70}", "NPS-120", "Beneficial Info Error: Max length 70 (LLLVAR)"));

        // DE 128: Message Authentication Code - an16
        m.put(128, new FieldSpec(128, 16, 16, "[a-zA-Z0-9]{16}", "NPS-128",
                "MAC Error: Must be 16 alphanumeric characters"));

        return Collections.unmodifiableMap(m);
    }

    public static FieldSpec get(int field) {
        return SPECS.get(field);
    }
}
