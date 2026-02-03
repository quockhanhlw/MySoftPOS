package com.example.mysoftpos.iso8583.emv;

import com.example.mysoftpos.R;
import com.example.mysoftpos.iso8583.emv.EmvDataFactory;
import com.example.mysoftpos.iso8583.emv.EmvTags;
import com.example.mysoftpos.iso8583.util.HexUtil;

import com.example.mysoftpos.iso8583.spec.IsoField;

import com.example.mysoftpos.iso8583.emv.EmvUtils;
import java.util.Random;

/**
 * Factory for generating binary DE 55 (ICC Data) payloads.
 * Implements strict logic for when to send DE 55 based on POS Entry Mode.
 */
public class EmvDataFactory {

    /**
     * Determines if DE 55 should be present based on DE 22 (Point of Service Entry
     * Mode).
     * Rule:
     * - Include for Contact Chip (05x) and Contactless Chip (07x).
     * - Exclude for Magstripe (02x, 90x), Fallback (80x), Manual (01x), and QR
     * (03x).
     *
     * @param de22 POS Entry Mode (e.g., "051", "022")
     * @return true if DE 55 is mandatory.
     */
    public static boolean shouldIncludeDe55(String de22) {
        if (de22 == null || de22.length() < 2)
            return false;

        // Check first 2 digits
        if (de22.startsWith("05"))
            return true; // Contact Chip
        if (de22.startsWith("07"))
            return true; // Contactless Chip

        // Explicitly exclude 80x (Fallback rule: treat as magstripe for simulator per
        // Napas)
        // Explicitly exclude 02x, 90x, etc.
        return false;
    }

    /**
     * Generates the DE 55 binary payload.
     * 
     * @param de22                  POS Entry Mode code.
     * @param transactionDateYYMMDD Date string (YYMMDD), e.g., "251201".
     * @return Byte array of the TLV data, or null if filtered out.
     */
    public static byte[] generate(String de22, String transactionDateYYMMDD) {
        if (!shouldIncludeDe55(de22)) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        // 1. 9F26 - ARQC (8 bytes) - Mock
        sb.append(EmvUtils.buildTlv(EmvTags.AC, "4E69485123961805"));

        // 2. 9F27 - CID (1 byte)
        sb.append(EmvUtils.buildTlv(EmvTags.CID, "80"));

        // 3. 9F10 - IAD (Variable)
        sb.append(EmvUtils.buildTlv(EmvTags.IAD, "06010A03000000"));

        // 4. 9F37 - Unpredictable Number (4 bytes Random)
        sb.append(EmvUtils.buildTlv(EmvTags.UNPREDICTABLE_NUM, generateRandomHex(4)));

        // 5. 9F36 - ATC (2 bytes)
        sb.append(EmvUtils.buildTlv(EmvTags.ATC, "0001"));

        // 6. 95 - TVR (5 bytes)
        sb.append(EmvUtils.buildTlv(EmvTags.TVR, "0000008000"));

        // 7. 9A - Transaction Date (YYMMDD)
        String date = (transactionDateYYMMDD != null) ? transactionDateYYMMDD : "250101";
        sb.append(EmvUtils.buildTlv(EmvTags.TXN_DATE, date));

        // 8. 9C - Transaction Type (1 byte) - Purchase "00"
        sb.append(EmvUtils.buildTlv(EmvTags.TXN_TYPE, "00"));

        // 9. 5F2A - Currency Code (2 bytes) - VND 0704
        sb.append(EmvUtils.buildTlv(EmvTags.TXN_CURRENCY, "0704"));

        // 10. 82 - AIP (2 bytes)
        sb.append(EmvUtils.buildTlv(EmvTags.AIP, "1800"));

        // 11. 5F34 - PAN Sequence (1 byte) - Optional but good practice
        sb.append(EmvUtils.buildTlv(EmvTags.PAN_SEQ_NUM, "01"));

        // Convert the full Hex String to Binary
        return HexUtil.hexToBytes(sb.toString());
    }

    private static String generateRandomHex(int byteLength) {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < byteLength; i++) {
            sb.append(String.format("%02X", r.nextInt(256)));
        }
        return sb.toString();
    }
}








