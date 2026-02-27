package com.example.mysoftpos.iso8583.emv;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EMV TLV Codec for ISO 8583 DE 55 — NAPAS Domestic CHIP (NFC Contactless).
 *
 * Implements the complete DE 55 processing pipeline per NAPAS Part II Message Format:
 *
 * <pre>
 * DE 55 Wire Format:  [3-byte ASCII LLLVAR length prefix] + [binary TLV stream]
 * Example prefix:     "124" means 124 bytes of TLV data follow.
 * Max:                255 bytes of TLV content.
 * </pre>
 *
 * AID for NAPAS Domestic CHIP: F0000007040001 (NOT UPI/UnionPay).
 *
 * This class provides:
 *   (1) buildDE55Request  — Encode EMV tags → TLV stream (with LLLVAR prefix)
 *   (2) parseDE55Response — Decode TLV stream → EMV tag map
 *   (3) encodeTag57       — Build Track 2 Equivalent Data for tag 57
 *   (4) buildDE35FromTag57— Convert tag 57 → DE 35 string
 *   (5) Mandatory tag validation before build
 *   (6) BCD / Binary encoding helpers for each tag type
 */
public final class EmvTlvCodec {

    private EmvTlvCodec() {
    }

    // =====================================================================
    // EMV Tag Constants — NAPAS Domestic CHIP
    // =====================================================================

    // --- Request Mandatory Tags (Acquirer → NAPAS) ---

    /** Tag 9F02 — Amount Authorized (cn 6 bytes, BCD) */
    public static final int TAG_AMOUNT_AUTHORIZED    = 0x9F02;
    /** Tag 9F03 — Amount Other (cn 6 bytes, BCD) */
    public static final int TAG_AMOUNT_OTHER         = 0x9F03;
    /** Tag 9F1A — Terminal Country Code (cn 2 bytes, BCD: VN = 0704) */
    public static final int TAG_TERMINAL_COUNTRY     = 0x9F1A;
    /** Tag 95 — Terminal Verification Results (b 5 bytes) */
    public static final int TAG_TVR                  = 0x95;
    /** Tag 5F2A — Transaction Currency Code (cn 2 bytes, BCD: VND = 0704) */
    public static final int TAG_TXN_CURRENCY_CODE    = 0x5F2A;
    /** Tag 9A — Transaction Date (cn 3 bytes, BCD YYMMDD) */
    public static final int TAG_TXN_DATE             = 0x9A;
    /** Tag 9C — Transaction Type (cn 1 byte: 00=Purchase, 01=Cash, 30=Balance) */
    public static final int TAG_TXN_TYPE             = 0x9C;
    /** Tag 9F37 — Unpredictable Number (b 4 bytes, terminal-generated) */
    public static final int TAG_UNPREDICTABLE_NUMBER = 0x9F37;
    /** Tag 82 — Application Interchange Profile (b 2 bytes, from card) */
    public static final int TAG_AIP                  = 0x82;
    /** Tag 9F36 — Application Transaction Counter ATC (b 2 bytes, from card) */
    public static final int TAG_ATC                  = 0x9F36;
    /** Tag 9F26 — Application Cryptogram ARQC (b 8 bytes, from card) */
    public static final int TAG_APP_CRYPTOGRAM       = 0x9F26;
    /** Tag 9F10 — Issuer Application Data (b max 32 bytes, from card) */
    public static final int TAG_ISSUER_APP_DATA      = 0x9F10;
    /** Tag 9F33 — Terminal Capabilities (b 3 bytes) */
    public static final int TAG_TERMINAL_CAPABILITIES = 0x9F33;
    /** Tag 9F34 — CVM Results (b 3 bytes) */
    public static final int TAG_CVM_RESULTS          = 0x9F34;
    /** Tag 9F35 — Terminal Type (cn 1 byte) */
    public static final int TAG_TERMINAL_TYPE        = 0x9F35;
    /** Tag 9F09 — Application Version Number Terminal (b 2 bytes) */
    public static final int TAG_APP_VERSION_TERMINAL = 0x9F09;
    /** Tag 9F27 — Cryptogram Information Data (b 1 byte: 0x80 = ARQC) */
    public static final int TAG_CRYPTOGRAM_INFO      = 0x9F27;

    // --- Request Conditional Tags ---

    /** Tag 57 — Track 2 Equivalent Data (b max 19 bytes, semi-packed BCD) */
    public static final int TAG_TRACK2_EQUIVALENT    = 0x57;
    /** Tag 84 — Dedicated File Name / AID (b max 16 bytes) */
    public static final int TAG_DF_NAME              = 0x84;
    /** Tag 9F41 — Transaction Sequence Counter (cn max 4 bytes) */
    public static final int TAG_TXN_SEQ_COUNTER      = 0x9F41;
    /** Tag 5F34 — Application PAN Sequence Number (cn 1 byte) → maps to DE 23 */
    public static final int TAG_CARD_SEQ_NUM         = 0x5F34;
    /** Tag 9F63 — Card Production ID (conditional, from card) */
    public static final int TAG_CARD_PRODUCTION_ID   = 0x9F63;
    /** Tag 9F1E — Interface Device Serial Number (ans 8 bytes) */
    public static final int TAG_IFD_SERIAL_NUMBER    = 0x9F1E;

    // --- Response Tags (NAPAS/Issuer → Terminal) ---

    /** Tag 91 — Issuer Authentication Data (b max 8 bytes) */
    public static final int TAG_ISSUER_AUTH_DATA     = 0x91;
    /** Tag 71 — Issuer Script Template 1 (constructed, max 128 bytes) */
    public static final int TAG_ISSUER_SCRIPT_1      = 0x71;
    /** Tag 72 — Issuer Script Template 2 (constructed, max 128 bytes) */
    public static final int TAG_ISSUER_SCRIPT_2      = 0x72;
    /** Tag DF31 — Issuer Script Result (reversal only) */
    public static final int TAG_ISSUER_SCRIPT_RESULT = 0xDF31;

    /** NAPAS Domestic CHIP AID prefix */
    public static final String NAPAS_DOMESTIC_AID    = "F0000007040001";

    // List of mandatory tags for request validation
    private static final int[] MANDATORY_REQUEST_TAGS = {
            TAG_AMOUNT_AUTHORIZED,   // 9F02
            TAG_AMOUNT_OTHER,        // 9F03
            TAG_TERMINAL_COUNTRY,    // 9F1A
            TAG_TVR,                 // 95
            TAG_TXN_CURRENCY_CODE,   // 5F2A
            TAG_TXN_DATE,            // 9A
            TAG_TXN_TYPE,            // 9C
            TAG_UNPREDICTABLE_NUMBER,// 9F37
            TAG_AIP,                 // 82
            TAG_ATC,                 // 9F36
            TAG_APP_CRYPTOGRAM,      // 9F26
            TAG_ISSUER_APP_DATA,     // 9F10
            TAG_TERMINAL_CAPABILITIES,// 9F33
            TAG_CVM_RESULTS,         // 9F34
            TAG_TERMINAL_TYPE,       // 9F35
            TAG_APP_VERSION_TERMINAL // 9F09
    };

    // =====================================================================
    // (1) buildDE55Request — Encode EMV tags → DE 55 wire bytes
    // =====================================================================

    /**
     * Build DE 55 request content from a map of EMV tags.
     *
     * <pre>
     * Steps:
     *   1. Validate all mandatory tags are present.
     *   2. For each tag, encode as BER-TLV (Tag + Length + Value).
     *   3. Concatenate all TLVs into a single binary stream.
     *   4. Return the TLV stream as a hex string for IsoMessage.setField(55, ...).
     *
     * The StandardIsoPacker handles LLLVAR prefix + hex→bytes conversion
     * when packing DE 55 (FieldType.LLLVAR in SCHEMA).
     * </pre>
     *
     * @param tags Map of EMV tag number → raw byte[] value
     * @return Hex string of the TLV stream
     * @throws IllegalArgumentException if any mandatory tag is missing
     */
    public static String buildDE55Request(Map<Integer, byte[]> tags) {
        validateMandatoryTags(tags);
        return buildTlvStreamHex(tags);
    }

    /**
     * Build DE 55 without mandatory tag validation (for test/reversal scenarios).
     */
    public static String buildDE55(Map<Integer, byte[]> tags) {
        return buildTlvStreamHex(tags);
    }

    /**
     * Build DE 55 as raw bytes (for scenarios needing byte[] directly).
     */
    public static byte[] buildDE55Bytes(Map<Integer, byte[]> tags) {
        return buildTlvStreamBytes(tags);
    }

    /**
     * Build DE 55 with the 3-byte ASCII LLLVAR prefix prepended.
     * Returns raw bytes: [3-byte ASCII length] + [TLV binary stream].
     *
     * Example: TLV stream is 124 bytes → output = "124" (3 ASCII bytes) + 124 TLV bytes.
     *
     * @param tags Map of EMV tag number → raw byte[] value
     * @return Complete DE 55 wire bytes with LLLVAR prefix
     */
    public static byte[] buildDE55WithPrefix(Map<Integer, byte[]> tags) {
        byte[] tlvStream = buildTlvStreamBytes(tags);
        int len = tlvStream.length;
        if (len > 255) {
            throw new IllegalArgumentException("DE 55 TLV stream exceeds 255 bytes: " + len);
        }

        String prefix = String.format(java.util.Locale.US, "%03d", len);
        byte[] prefixBytes = prefix.getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        byte[] result = new byte[3 + len];
        System.arraycopy(prefixBytes, 0, result, 0, 3);
        System.arraycopy(tlvStream, 0, result, 3, len);
        return result;
    }

    // =====================================================================
    // (2) parseDE55Response — Decode TLV stream → EMV tag map
    // =====================================================================

    /**
     * Parse DE 55 response content (hex string from IsoMessage) into EMV tag map.
     *
     * Supports:
     *   - Single-byte tags (57, 82, 91, 95, 9A, 9C, 71, 72)
     *   - Two-byte tags (9F26, 5F2A, 9F10, DF31, etc.)
     *   - Constructed TLV tags (71, 72) — returned as-is (the value contains
     *     the inner TLV structure for Issuer Script processing)
     *   - Multi-byte BER-TLV length encoding
     *
     * @param de55Hex Hex string of DE 55 TLV stream (without LLLVAR prefix)
     * @return Map of tag number → raw byte[] value
     */
    public static Map<Integer, byte[]> parseDE55Response(String de55Hex) {
        if (de55Hex == null || de55Hex.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return parseTlvStream(hexToBytes(de55Hex));
    }

    /**
     * Parse DE 55 raw bytes (without LLLVAR prefix) into EMV tag map.
     */
    public static Map<Integer, byte[]> parseDE55Bytes(byte[] data) {
        return parseTlvStream(data);
    }

    /**
     * Parse DE 55 raw wire bytes WITH the 3-byte LLLVAR prefix.
     * Strips the prefix and parses the remaining TLV stream.
     */
    public static Map<Integer, byte[]> parseDE55WithPrefix(byte[] wireData) {
        if (wireData == null || wireData.length < 3) {
            return new LinkedHashMap<>();
        }

        // Read 3-byte ASCII length prefix
        String lenStr = new String(wireData, 0, 3, java.nio.charset.StandardCharsets.US_ASCII);
        int len;
        try {
            len = Integer.parseInt(lenStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid LLLVAR prefix: '" + lenStr + "'");
        }

        if (wireData.length < 3 + len) {
            throw new IllegalArgumentException("DE 55 truncated: prefix=" + len
                    + " but only " + (wireData.length - 3) + " bytes available");
        }

        byte[] tlvData = Arrays.copyOfRange(wireData, 3, 3 + len);
        return parseTlvStream(tlvData);
    }

    // =====================================================================
    // (3) encodeTag57 — Build Track 2 Equivalent Data
    // =====================================================================

    /**
     * Data class for parsed Track 2 Equivalent Data.
     */
    public static class Track2Data {
        public final String pan;
        public final String expiryYYMM;
        public final String serviceCode;
        public final String discretionaryData;
        public final String fullTrack2Hex;

        public Track2Data(String pan, String expiryYYMM, String serviceCode,
                          String discretionaryData, String fullTrack2Hex) {
            this.pan = pan;
            this.expiryYYMM = expiryYYMM;
            this.serviceCode = serviceCode;
            this.discretionaryData = discretionaryData;
            this.fullTrack2Hex = fullTrack2Hex;
        }

        @Override
        public String toString() {
            return "Track2Data{pan=" + maskPan(pan)
                    + ", expiry=" + expiryYYMM
                    + ", svc=" + serviceCode + "}";
        }

        private static String maskPan(String p) {
            if (p == null || p.length() < 8) return "****";
            return p.substring(0, 6) + "****" + p.substring(p.length() - 4);
        }
    }

    /**
     * Encode Track 2 Equivalent Data for EMV tag 57.
     *
     * <pre>
     * Format (semi-packed BCD):
     *   PAN + 'D' (hex nibble) + YYMM + ServiceCode + DiscretionaryData + padding 'F'
     *
     * NAPAS rules:
     *   - Service Code first digit MUST be '2' or '6' (chip card indicator).
     *   - Trailing 'F' padding to round to even number of nibbles.
     *
     * Example:
     *   PAN=9704360000000001, Expiry=2812, SvcCode=201, Disc=0000000000
     *   → "9704360000000001D28122010000000000F"
     *   → packed to 18 bytes
     * </pre>
     *
     * @param pan           Primary Account Number (13–19 digits)
     * @param expiryYYMM    Expiry date YYMM (4 digits)
     * @param serviceCode   Service code (3 digits; first MUST be '2' or '6')
     * @param discretionary Discretionary data hex string (may be null/empty)
     * @return Raw byte array for tag 57 value
     * @throws IllegalArgumentException on validation failure
     */
    public static byte[] encodeTag57(String pan, String expiryYYMM,
                                     String serviceCode, String discretionary) {
        // --- Validation ---
        if (pan == null || pan.isEmpty()) {
            throw new IllegalArgumentException("PAN must not be null or empty");
        }
        if (!pan.matches("\\d{13,19}")) {
            throw new IllegalArgumentException(
                    "PAN must be 13–19 digits, got: " + pan.length() + " chars");
        }
        if (expiryYYMM == null || !expiryYYMM.matches("\\d{4}")) {
            throw new IllegalArgumentException(
                    "Expiry must be exactly 4 digits (YYMM), got: " + expiryYYMM);
        }
        if (serviceCode == null || !serviceCode.matches("\\d{3}")) {
            throw new IllegalArgumentException(
                    "Service code must be exactly 3 digits, got: " + serviceCode);
        }
        char svcFirst = serviceCode.charAt(0);
        if (svcFirst != '2' && svcFirst != '6') {
            throw new IllegalArgumentException(
                    "NAPAS CHIP: Service code first digit must be '2' or '6' "
                            + "(chip card indicator), got: '" + svcFirst + "'");
        }
        if (discretionary == null) {
            discretionary = "";
        }

        // --- Build hex string ---
        StringBuilder sb = new StringBuilder();
        sb.append(pan);
        sb.append('D');
        sb.append(expiryYYMM);
        sb.append(serviceCode);
        sb.append(discretionary);

        // Pad to even number of nibbles with trailing 'F'
        String hexStr = sb.toString().toUpperCase();
        if (hexStr.length() % 2 != 0) {
            hexStr += "F";
        }

        return hexToBytes(hexStr);
    }

    /**
     * Decode Track 2 Equivalent Data from tag 57 raw bytes.
     *
     * @param tag57 Raw byte array of tag 57 value
     * @return Parsed Track2Data, or null if format is invalid
     */
    public static Track2Data decodeTag57(byte[] tag57) {
        if (tag57 == null || tag57.length == 0) {
            return null;
        }

        String hex = bytesToHex(tag57).toUpperCase();
        int dPos = hex.indexOf('D');
        if (dPos < 0) return null;

        String pan = hex.substring(0, dPos);
        String afterD = hex.substring(dPos + 1);

        if (afterD.length() < 7) return null; // YYMM(4) + SVC(3) minimum

        String expiryYYMM = afterD.substring(0, 4);
        String serviceCode = afterD.substring(4, 7);
        String disc = afterD.substring(7);

        // Strip trailing 'F' padding
        while (disc.endsWith("F")) {
            disc = disc.substring(0, disc.length() - 1);
        }

        return new Track2Data(pan, expiryYYMM, serviceCode, disc, hex);
    }

    // =====================================================================
    // (4) buildDE35FromTag57 — Convert tag 57 → DE 35 string
    // =====================================================================

    /**
     * Build DE 35 (Track-2 Data) string from tag 57 raw bytes.
     *
     * <pre>
     * NAPAS spec:
     *   DE 35 for NFC CHIP must contain the same Track 2 data as tag 57,
     *   with separator 'D' (not '=' like magstripe), and trailing 'F' padding removed.
     *
     * Example:
     *   Tag 57 bytes → hex "9704360000000001D28122010000000000F"
     *   DE 35 string → "9704360000000001D2812201000000000"
     *   (trailing 'F' stripped)
     * </pre>
     *
     * @param tag57 Raw byte array of tag 57 value
     * @return DE 35 string with 'D' separator and no padding
     * @throws IllegalArgumentException if tag57 is null/empty or invalid format
     */
    public static String buildDE35FromTag57(byte[] tag57) {
        if (tag57 == null || tag57.length == 0) {
            throw new IllegalArgumentException("Tag 57 data must not be null or empty");
        }

        String hex = bytesToHex(tag57).toUpperCase();

        // Verify separator exists
        if (hex.indexOf('D') < 0) {
            throw new IllegalArgumentException("Tag 57 missing 'D' separator: " + hex);
        }

        // Strip ALL trailing 'F' padding
        while (hex.endsWith("F")) {
            hex = hex.substring(0, hex.length() - 1);
        }

        return hex;
    }

    // =====================================================================
    // (5) Mandatory Tag Validation
    // =====================================================================

    /**
     * Validate that all mandatory NAPAS request tags are present in the map.
     *
     * @param tags Tag map to validate
     * @throws IllegalArgumentException with clear message listing missing tags
     */
    public static void validateMandatoryTags(Map<Integer, byte[]> tags) {
        if (tags == null || tags.isEmpty()) {
            throw new IllegalArgumentException(
                    "DE 55 tag map is null or empty — no EMV data available. "
                            + "NFC CHIP transactions require EMV tags from card.");
        }

        StringBuilder missing = new StringBuilder();
        for (int reqTag : MANDATORY_REQUEST_TAGS) {
            byte[] val = tags.get(reqTag);
            if (val == null || val.length == 0) {
                if (missing.length() > 0) missing.append(", ");
                missing.append(String.format("0x%04X (%s)", reqTag, describeTag(reqTag)));
            }
        }

        if (missing.length() > 0) {
            throw new IllegalArgumentException(
                    "DE 55 mandatory tags missing: [" + missing + "]. "
                            + "Ensure all EMV tags are read from chip card before building request.");
        }
    }

    /**
     * Validate tag 57 service code — first digit must be '2' or '6'.
     *
     * @param tags Tag map containing tag 57
     * @throws IllegalArgumentException if service code is invalid for CHIP
     */
    public static void validateTag57ServiceCode(Map<Integer, byte[]> tags) {
        byte[] t57 = tags.get(TAG_TRACK2_EQUIVALENT);
        if (t57 == null) return; // Tag 57 is conditional

        Track2Data t2 = decodeTag57(t57);
        if (t2 == null) {
            throw new IllegalArgumentException("Tag 57 has invalid format");
        }

        char first = t2.serviceCode.charAt(0);
        if (first != '2' && first != '6') {
            throw new IllegalArgumentException(
                    "NAPAS CHIP: Tag 57 service code first digit must be '2' or '6', "
                            + "got: '" + first + "' — this card may be magstripe-only.");
        }
    }

    // =====================================================================
    // BCD / Binary Encoding Helpers
    // =====================================================================

    /**
     * Encode numeric string to BCD (compressed numeric) bytes.
     * For tags: 9F02, 9F03, 9F1A, 5F2A, 9A, 9C, 9F41, 5F34.
     */
    public static byte[] encodeBcd(String numericStr) {
        if (numericStr == null || numericStr.isEmpty()) return new byte[0];
        String s = numericStr;
        if (s.length() % 2 != 0) s = "0" + s;
        return hexToBytes(s);
    }

    /**
     * Encode BCD with fixed byte length, left-padded with zeros.
     */
    public static byte[] encodeBcdFixed(String numericStr, int byteLen) {
        if (numericStr == null) numericStr = "";
        int requiredNibbles = byteLen * 2;
        while (numericStr.length() < requiredNibbles) {
            numericStr = "0" + numericStr;
        }
        if (numericStr.length() > requiredNibbles) {
            numericStr = numericStr.substring(numericStr.length() - requiredNibbles);
        }
        return hexToBytes(numericStr);
    }

    /** Decode BCD bytes to numeric string. */
    public static String decodeBcd(byte[] bcd) {
        if (bcd == null || bcd.length == 0) return "";
        return bytesToHex(bcd);
    }

    // =====================================================================
    // Terminal EMV Data Generation
    // =====================================================================

    /** Generate Unpredictable Number (tag 9F37) — 4 random bytes. */
    public static byte[] generateUnpredictableNumber() {
        byte[] un = new byte[4];
        new java.security.SecureRandom().nextBytes(un);
        return un;
    }

    /** Encode Transaction Date for tag 9A (BCD YYMMDD, 3 bytes). */
    public static byte[] encodeTransactionDate() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                "yyMMdd", java.util.Locale.US);
        return encodeBcdFixed(sdf.format(new java.util.Date()), 3);
    }

    /** Encode Amount Authorized for tag 9F02 (BCD 6 bytes). */
    public static byte[] encodeAmountAuthorized(String amountMinorUnits) {
        if (amountMinorUnits == null || amountMinorUnits.isEmpty()) amountMinorUnits = "0";
        return encodeBcdFixed(amountMinorUnits, 6);
    }

    /** Encode Transaction Currency Code for tag 5F2A (BCD 2 bytes: VND=0704). */
    public static byte[] encodeCurrencyCode(String currencyCode) {
        if (currencyCode == null || currencyCode.isEmpty()) currencyCode = "704";
        return encodeBcdFixed(currencyCode, 2);
    }

    /** Encode Terminal Country Code for tag 9F1A (BCD 2 bytes: VN=0704). */
    public static byte[] encodeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isEmpty()) countryCode = "704";
        return encodeBcdFixed(countryCode, 2);
    }

    /**
     * Default Terminal Capabilities (tag 9F33) — 3 bytes.
     * Byte1=E0 (Manual+Magstripe+IC), Byte2=F0 (All CVM), Byte3=C8 (SDA+DDA+CDA).
     */
    public static byte[] getDefaultTerminalCapabilities() {
        return new byte[]{(byte) 0xE0, (byte) 0xF0, (byte) 0xC8};
    }

    /** Default Terminal Type (tag 9F35) — 1 byte: 0x22 (Attended, Online, Merchant). */
    public static byte[] getDefaultTerminalType() {
        return new byte[]{0x22};
    }

    /** Default Application Version Number Terminal (tag 9F09) — 2 bytes. */
    public static byte[] getDefaultAppVersionTerminal() {
        return new byte[]{0x00, (byte) 0x8C};
    }

    /**
     * Encode CVM Results (tag 9F34) — 3 bytes.
     *
     * @param pinVerified true → Online PIN (DE 22=071); false → No CVM (DE 22=072)
     */
    public static byte[] encodeCvmResults(boolean pinVerified) {
        if (pinVerified) {
            // Method=02 (Enciphered PIN online), Condition=00, Result=02 (Successful)
            return new byte[]{0x02, 0x00, 0x02};
        } else {
            // Method=1F (No CVM), Condition=00, Result=02 (Successful)
            return new byte[]{0x1F, 0x00, 0x02};
        }
    }

    /**
     * Build default TVR (tag 95) — 5 bytes, all zeros = no exceptions.
     * Real terminal should populate based on actual verification results.
     */
    public static byte[] getDefaultTvr() {
        return new byte[]{0x00, 0x00, 0x00, 0x00, 0x00};
    }

    // =====================================================================
    // Complete Tag Map Builder for NFC Purchase
    // =====================================================================

    /**
     * Build a complete EMV tag map for NFC CHIP Purchase request DE 55.
     *
     * <pre>
     * cardTags — Tags read directly from chip via APDU:
     *   57 (Track2), 82 (AIP), 84 (AID/DF Name), 9F26 (ARQC), 9F27 (CID),
     *   9F10 (IAD), 9F36 (ATC), 95 (TVR), 5F34 (PAN Seq), 9F63 (if present)
     *
     * This method adds terminal-generated tags:
     *   9F02, 9F03, 5F2A, 9F1A, 9A, 9C, 9F37, 9F33, 9F34, 9F35, 9F09
     * </pre>
     *
     * @param cardTags     Tags from chip card
     * @param amountStr    Amount in minor units (e.g., "50000" for 50,000 VND)
     * @param currencyCode ISO currency code (e.g., "704")
     * @param countryCode  ISO country code (e.g., "704")
     * @param txnType      Transaction type: "00"=Purchase, "01"=Cash, "30"=Balance
     * @param pinVerified  true if Online PIN (DE 22=071), false if No CVM (072)
     * @return Complete tag map ready for buildDE55Request()
     */
    public static Map<Integer, byte[]> buildNfcPurchaseTags(
            Map<Integer, byte[]> cardTags,
            String amountStr, String currencyCode, String countryCode,
            String txnType, boolean pinVerified) {

        Map<Integer, byte[]> all = new LinkedHashMap<>();

        // Terminal-generated tags (in typical NAPAS field order)
        all.put(TAG_AMOUNT_AUTHORIZED, encodeAmountAuthorized(amountStr));
        all.put(TAG_AMOUNT_OTHER, encodeBcdFixed("0", 6));  // Always zeros
        all.put(TAG_TERMINAL_COUNTRY, encodeCountryCode(countryCode));
        all.put(TAG_TXN_CURRENCY_CODE, encodeCurrencyCode(currencyCode));
        all.put(TAG_TXN_DATE, encodeTransactionDate());
        all.put(TAG_TXN_TYPE, encodeBcdFixed(txnType != null ? txnType : "00", 1));
        all.put(TAG_UNPREDICTABLE_NUMBER, generateUnpredictableNumber());
        all.put(TAG_TERMINAL_CAPABILITIES, getDefaultTerminalCapabilities());
        all.put(TAG_CVM_RESULTS, encodeCvmResults(pinVerified));
        all.put(TAG_TERMINAL_TYPE, getDefaultTerminalType());
        all.put(TAG_APP_VERSION_TERMINAL, getDefaultAppVersionTerminal());

        // If card did not provide TVR, use default
        if (cardTags == null || !cardTags.containsKey(TAG_TVR)) {
            all.put(TAG_TVR, getDefaultTvr());
        }

        // Card-read tags (override terminal defaults — card data takes priority)
        if (cardTags != null) {
            all.putAll(cardTags);
        }

        return all;
    }

    // =====================================================================
    // Card Sequence Number (DE 23) Helper
    // =====================================================================

    /**
     * Extract Card Sequence Number from EMV tags for DE 23.
     *
     * <pre>
     * Tag 5F34 value = BCD 1 byte (e.g., 0x01 → "001").
     * If tag 5F34 absent → DE 23 = "000" per NAPAS spec.
     * </pre>
     *
     * @param emvTags Map containing (possibly) tag 5F34
     * @return 3-digit DE 23 string with leading zeros (e.g., "001")
     */
    public static String extractCardSequenceNumber(Map<Integer, byte[]> emvTags) {
        if (emvTags == null) return "000";
        byte[] csn = emvTags.get(TAG_CARD_SEQ_NUM);
        if (csn == null || csn.length == 0) return "000";

        // BCD decode: 0x01 → "01" → integer 1 → "001"
        int value = csn[0] & 0xFF;
        return String.format(java.util.Locale.US, "%03d", value);
    }

    // =====================================================================
    // Issuer Response Extraction
    // =====================================================================

    /** Extract tag 91 (Issuer Authentication Data) from response tags. */
    public static byte[] getIssuerAuthData(Map<Integer, byte[]> responseTags) {
        return responseTags != null ? responseTags.get(TAG_ISSUER_AUTH_DATA) : null;
    }

    /** Extract tag 71 (Issuer Script Template 1) from response tags. */
    public static byte[] getIssuerScript1(Map<Integer, byte[]> responseTags) {
        return responseTags != null ? responseTags.get(TAG_ISSUER_SCRIPT_1) : null;
    }

    /** Extract tag 72 (Issuer Script Template 2) from response tags. */
    public static byte[] getIssuerScript2(Map<Integer, byte[]> responseTags) {
        return responseTags != null ? responseTags.get(TAG_ISSUER_SCRIPT_2) : null;
    }

    // =====================================================================
    // Internal — TLV Stream Build/Parse
    // =====================================================================

    private static String buildTlvStreamHex(Map<Integer, byte[]> tags) {
        if (tags == null || tags.isEmpty()) return "";
        return bytesToHex(buildTlvStreamBytes(tags));
    }

    private static byte[] buildTlvStreamBytes(Map<Integer, byte[]> tags) {
        if (tags == null || tags.isEmpty()) return new byte[0];

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (Map.Entry<Integer, byte[]> entry : tags.entrySet()) {
            int tag = entry.getKey();
            byte[] value = entry.getValue();
            if (value == null) continue;

            byte[] tagBytes = encodeTag(tag);
            baos.write(tagBytes, 0, tagBytes.length);

            byte[] lenBytes = encodeLength(value.length);
            baos.write(lenBytes, 0, lenBytes.length);

            baos.write(value, 0, value.length);
        }
        return baos.toByteArray();
    }

    private static Map<Integer, byte[]> parseTlvStream(byte[] data) {
        Map<Integer, byte[]> result = new LinkedHashMap<>();
        if (data == null || data.length == 0) return result;

        int offset = 0;
        while (offset < data.length) {
            // Skip padding bytes
            if (data[offset] == 0x00 || (data[offset] & 0xFF) == 0xFF) {
                offset++;
                continue;
            }

            // --- Read Tag ---
            int firstByte = data[offset++] & 0xFF;
            int tag;

            if ((firstByte & 0x1F) == 0x1F) {
                // Multi-byte tag
                tag = firstByte;
                if (offset >= data.length) break;
                do {
                    int nextByte = data[offset++] & 0xFF;
                    tag = (tag << 8) | nextByte;
                    if ((nextByte & 0x80) == 0) break;
                } while (offset < data.length);
            } else {
                tag = firstByte;
            }

            // --- Read Length ---
            if (offset >= data.length) break;
            int length = data[offset++] & 0xFF;

            if ((length & 0x80) != 0) {
                int numLenBytes = length & 0x7F;
                if (numLenBytes == 0 || numLenBytes > 3) break;
                length = 0;
                for (int i = 0; i < numLenBytes; i++) {
                    if (offset >= data.length) break;
                    length = (length << 8) | (data[offset++] & 0xFF);
                }
            }

            // --- Read Value ---
            if (length < 0 || offset + length > data.length) break;
            byte[] value = Arrays.copyOfRange(data, offset, offset + length);
            offset += length;

            result.put(tag, value);
        }

        return result;
    }

    // =====================================================================
    // Internal — BER-TLV Tag/Length Encoding
    // =====================================================================

    /** Encode EMV tag number to bytes. Supports 1, 2, and 3-byte tags. */
    static byte[] encodeTag(int tag) {
        if (tag <= 0xFF) {
            return new byte[]{(byte) tag};
        } else if (tag <= 0xFFFF) {
            return new byte[]{(byte) (tag >> 8), (byte) (tag & 0xFF)};
        } else {
            return new byte[]{
                    (byte) (tag >> 16), (byte) ((tag >> 8) & 0xFF), (byte) (tag & 0xFF)
            };
        }
    }

    /** Encode BER-TLV length: 0–127 = 1 byte, 128–255 = 0x81+byte, 256+ = 0x82+2 bytes. */
    static byte[] encodeLength(int length) {
        if (length < 0) throw new IllegalArgumentException("Negative length: " + length);
        if (length <= 127) return new byte[]{(byte) length};
        if (length <= 255) return new byte[]{(byte) 0x81, (byte) length};
        return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) (length & 0xFF)};
    }

    // =====================================================================
    // Tag Description (for error messages)
    // =====================================================================

    private static String describeTag(int tag) {
        switch (tag) {
            case TAG_AMOUNT_AUTHORIZED:    return "Amount Authorized";
            case TAG_AMOUNT_OTHER:         return "Amount Other";
            case TAG_TERMINAL_COUNTRY:     return "Terminal Country Code";
            case TAG_TVR:                  return "Terminal Verification Results";
            case TAG_TXN_CURRENCY_CODE:    return "Transaction Currency Code";
            case TAG_TXN_DATE:             return "Transaction Date";
            case TAG_TXN_TYPE:             return "Transaction Type";
            case TAG_UNPREDICTABLE_NUMBER: return "Unpredictable Number";
            case TAG_AIP:                  return "Application Interchange Profile";
            case TAG_ATC:                  return "Application Transaction Counter";
            case TAG_APP_CRYPTOGRAM:       return "Application Cryptogram (ARQC)";
            case TAG_ISSUER_APP_DATA:      return "Issuer Application Data";
            case TAG_TERMINAL_CAPABILITIES:return "Terminal Capabilities";
            case TAG_CVM_RESULTS:          return "CVM Results";
            case TAG_TERMINAL_TYPE:        return "Terminal Type";
            case TAG_APP_VERSION_TERMINAL: return "App Version Number (Terminal)";
            case TAG_TRACK2_EQUIVALENT:    return "Track 2 Equivalent Data";
            case TAG_DF_NAME:              return "Dedicated File Name (AID)";
            case TAG_CARD_SEQ_NUM:         return "PAN Sequence Number";
            case TAG_CRYPTOGRAM_INFO:      return "Cryptogram Information Data";
            default: return "Unknown";
        }
    }

    // =====================================================================
    // Hex Conversion Utilities
    // =====================================================================

    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        String s = hex.trim();
        if (s.length() % 2 != 0) s = "0" + s;
        int len = s.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException(
                        "Invalid hex char at index " + (i * 2) + " in: " + s);
            }
            out[i] = (byte) ((hi << 4) + lo);
        }
        return out;
    }

    public static String bytesToHex(byte[] data) {
        if (data == null || data.length == 0) return "";
        char[] hexChars = "0123456789ABCDEF".toCharArray();
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int v = data[i] & 0xFF;
            out[i * 2] = hexChars[v >>> 4];
            out[i * 2 + 1] = hexChars[v & 0x0F];
        }
        return new String(out);
    }
}

