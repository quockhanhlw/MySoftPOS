package com.example.mysoftpos.iso8583.util;

/**
 * Utility class to generate "Clear PIN Block" according to ISO 9564-1 Format 0.
 * 
 * Logic:
 * 1. Block A: 0 + L + PIN + Padding(F)
 * 2. Block B: 0000 + PAN(Rightmost 12 digits excluding check digit)
 * 3. Result: Block A XOR Block B
 */
public class PinBlockGenerator {

    private PinBlockGenerator() {
        // Utility class
    }

    /**
     * Calculates the Clear PIN Block (Format 0).
     * 
     * @param pin The PIN (e.g., "123456")
     * @param pan The PAN (Full card number, e.g., "97041800001234567")
     * @return The 16-character Hex String representing the Clear PIN Block.
     */
    public static String calculateClearBlock(String pin, String pan) {
        if (pin == null || pan == null) {
            throw new IllegalArgumentException("PIN and PAN must not be null");
        }

        // Step 1: Block A
        String blockA = constructBlockA(pin);

        // Step 2: Block B
        String blockB = constructBlockB(pan);

        // Step 3: XOR
        String clearBlock = xorHex(blockA, blockB);

        return clearBlock;
    }

    /**
     * Construct Block A: 0 + L + PIN + Padding('F')
     */
    private static String constructBlockA(String pin) {
        int len = pin.length();
        if (len < 4 || len > 12) {
            // ISO 9564 usually implies 4-12 digits.
            // But we will process as is, assuming valid usage.
            // Warn or throw if strictly enforcing.
        }

        StringBuilder sb = new StringBuilder();
        sb.append("0"); // Format 0
        sb.append(Integer.toHexString(len).toUpperCase()); // L (0-C)
        sb.append(pin); // PIN Digits

        // Pad with 'F' to 16 chars
        while (sb.length() < 16) {
            sb.append("F");
        }

        return sb.toString();
    }

    /**
     * Construct Block B: 0000 + 12 Rightmost digits of PAN (excluding check digit)
     */
    private static String constructBlockB(String pan) {
        // 1. Exclude Check Digit (last char)
        if (pan.length() < 13) {
            throw new IllegalArgumentException("PAN must be at least 13 digits (12 body + 1 check)");
        }
        String panNoCheck = pan.substring(0, pan.length() - 1);

        // 2. Take Rightmost 12 digits
        String right12 = panNoCheck.substring(panNoCheck.length() - 12);

        // 3. Pad with 0000
        return "0000" + right12;
    }

    /**
     * XOR two Hex Strings.
     */
    private static String xorHex(String hexA, String hexB) {
        byte[] a = hexStringToByteArray(hexA);
        byte[] b = hexStringToByteArray(hexB);

        // Since both are 16 hex chars -> 8 bytes
        byte[] result = new byte[8];
        for (int i = 0; i < 8; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }

        return bytesToHex(result);
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF; // Unsigned
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Main method for verification (as requested).
     */
    public static void main(String[] args) {
        // Test Case
        String pin = "123456";
        String pan = "97041800001234567";

        System.out.println("--- ISO 9564 Format 0 Calculation ---");
        System.out.println("Input PIN: " + pin);
        System.out.println("Input PAN: " + pan);

        String blockA = constructBlockA(pin);
        String blockB = constructBlockB(pan);

        System.out.println("Block A: " + blockA);
        System.out.println("Block B: " + blockB);

        String result = xorHex(blockA, blockB);
        System.out.println("Clear PIN Block: " + result);
    }
}
