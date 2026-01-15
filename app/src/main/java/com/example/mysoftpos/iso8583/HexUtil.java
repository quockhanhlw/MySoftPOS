package com.example.mysoftpos.iso8583;

/** Small hex helpers (no external dependency). */
public final class HexUtil {
    private HexUtil() {}

    public static byte[] hexToBytes(String hex) {
        if (hex == null) return new byte[0];
        String s = hex.trim();
        if (s.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex must have even length: " + s.length());
        }
        int len = s.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex at index " + (i * 2));
            }
            out[i] = (byte) ((hi << 4) + lo);
        }
        return out;
    }

    public static String bytesToHex(byte[] data) {
        if (data == null) return "";
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

