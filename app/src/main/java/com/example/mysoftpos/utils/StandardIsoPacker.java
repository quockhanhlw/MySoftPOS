package com.example.mysoftpos.utils;

import com.example.mysoftpos.iso8583.IsoMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Standard ISO8583 Packer (Packed BCD Format).
 * - MTI: 4 chars (ASCII 0200)
 * - Bitmap: 8 bytes (Binary)
 * - Numeric Fields: Packed BCD (n/2 bytes)
 * - LLVAR/LLLVAR: Length BCD, Value BCD(if Num) or ASCII
 */
public class StandardIsoPacker {

    private enum FieldType {
        NUMERIC,        // n - Fixed length, BCD
        ALPHA,          // a/an/ans - Fixed length, ASCII
        LLVAR,          // Variable length 2 digits (Length BCD)
        LLLVAR          // Variable length 3 digits (Length BCD 2 bytes)
    }

    private static class FieldDef {
        final FieldType type;
        final int length; 
        
        FieldDef(FieldType type, int length) {
            this.type = type;
            this.length = length;
        }
    }

    private static final Map<Integer, FieldDef> SCHEMA = new HashMap<>();

    static {
        // Define common fields for Purchase (0200)
        SCHEMA.put(2, new FieldDef(FieldType.LLVAR, 19));  // PAN
        SCHEMA.put(3, new FieldDef(FieldType.NUMERIC, 6)); // Processing Code
        SCHEMA.put(4, new FieldDef(FieldType.NUMERIC, 12)); // Amount
        SCHEMA.put(7, new FieldDef(FieldType.NUMERIC, 10)); // Transmission Date/Time
        SCHEMA.put(11, new FieldDef(FieldType.NUMERIC, 6)); // STAN
        SCHEMA.put(12, new FieldDef(FieldType.NUMERIC, 6)); // Local Time
        SCHEMA.put(13, new FieldDef(FieldType.NUMERIC, 4)); // Local Date
        SCHEMA.put(14, new FieldDef(FieldType.NUMERIC, 4)); // Expiry Date
        SCHEMA.put(18, new FieldDef(FieldType.NUMERIC, 4)); // MCC
        SCHEMA.put(22, new FieldDef(FieldType.NUMERIC, 3)); // POS Entry Mode
        SCHEMA.put(25, new FieldDef(FieldType.NUMERIC, 2)); // POS Condition Code
        SCHEMA.put(32, new FieldDef(FieldType.LLVAR, 11)); // Acquirer ID
        SCHEMA.put(35, new FieldDef(FieldType.LLVAR, 37)); // Track 2
        SCHEMA.put(37, new FieldDef(FieldType.ALPHA, 12)); // RRN (an12)
        SCHEMA.put(38, new FieldDef(FieldType.ALPHA, 6));  // Auth Code (an6)
        SCHEMA.put(39, new FieldDef(FieldType.ALPHA, 2));  // Response Code (an2)
        SCHEMA.put(41, new FieldDef(FieldType.ALPHA, 8));  // TID (ans8)
        SCHEMA.put(42, new FieldDef(FieldType.ALPHA, 15)); // MID (ans15)
        SCHEMA.put(43, new FieldDef(FieldType.ALPHA, 40)); // Name/Location
        SCHEMA.put(49, new FieldDef(FieldType.NUMERIC, 3)); // Currency
        SCHEMA.put(54, new FieldDef(FieldType.LLLVAR, 120)); // Additional Amounts
        SCHEMA.put(55, new FieldDef(FieldType.LLLVAR, 999)); // ICC Data
    }

    public static byte[] pack(IsoMessage msg) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // 1. MTI (ASCII 4 bytes)
        baos.write(msg.getMti().getBytes(StandardCharsets.UTF_8));

        // 2. Bitmap Generation (Binary 8 bytes)
        Set<Integer> presentFields = msg.getFieldNumbers();
        long bitmap = 0;
        for (int field : presentFields) {
            if (field < 1 || field > 64) continue;
            bitmap |= (1L << (64 - field));
        }
        
        // Write 8 bytes of bitmap
        for (int i = 7; i >= 0; i--) {
            baos.write((int) (bitmap >> (8 * i)) & 0xFF);
        }

        // 3. Fields Loop
        for (int i = 2; i <= 64; i++) {
            if (presentFields.contains(i)) {
                String val = msg.getField(i);
                FieldDef def = SCHEMA.get(i);
                if (def == null) continue;
                
                if (def.type == FieldType.NUMERIC) {
                    // ASCII Numeric: Fixed Length, Left Pad Zero
                    // e.g. F4 Amount "123" (n12) -> "000000000123" -> ASCII Bytes
                    String padded = leftPadZero(val, def.length);
                    baos.write(padded.getBytes(StandardCharsets.US_ASCII));
                } else if (def.type == FieldType.ALPHA) {
                    // ASCII Alpha: Fixed Length, Right Pad Space
                    baos.write(formatAlpha(val, def.length).getBytes(StandardCharsets.US_ASCII));
                } else if (def.type == FieldType.LLVAR) {
                    // Length: 2 ASCII Digits (e.g. "19" -> 0x31 0x39)
                    int len = val.length();
                    String lenStr = String.format("%02d", len);
                    baos.write(lenStr.getBytes(StandardCharsets.US_ASCII));
                    
                    // Value: ASCII
                    baos.write(val.getBytes(StandardCharsets.US_ASCII));
                } else if (def.type == FieldType.LLLVAR) {
                    // Length: 3 ASCII Digits (e.g. "012")
                    int len = val.length();
                     if (i == 55) {
                        // DE55 is special. If input is Hex String, we might need to send BINARY bytes?
                        // Or ASCII Hex?
                        // "Pure digits" prompt suggests ASCII.
                        // Standard EMV often binary.
                        // User prompt "send those fields as pure digits".
                        // Let's assume DE55 is also ASCII Hex String for now OR Raw bytes if LLLVAR is binary.
                        // Given errors were on F2, F3, F4, F11, F13...
                        // If I send DE55 as pure Hex String (ASCII), it matches "Alphanumeric".
                        
                        // BUT: Usually DE55 is binary raw data.
                        // Let's check if SCHEMA defines it as LLLVAR.
                        // If it's pure standard, LLLVAR is usually binary data with ASCII/BCD length?
                        // Let's stick to ASCII String bytes for now.
                        String lenStr = String.format("%03d", len);
                        baos.write(lenStr.getBytes(StandardCharsets.US_ASCII));
                        baos.write(val.getBytes(StandardCharsets.US_ASCII));
                    } else {
                        String lenStr = String.format("%03d", len);
                        baos.write(lenStr.getBytes(StandardCharsets.US_ASCII));
                        baos.write(val.getBytes(StandardCharsets.US_ASCII));
                    }
                }
            }
        }

        return baos.toByteArray();
    }
    
    // Unpacker logic for Response (BCD Support)
    public static String unpackField(byte[] responseData, int fieldId) {
        try {
            if (responseData == null || responseData.length < 12) return null;
            
            // Bitmap
            long bitmap = 0;
            for (int i = 0; i < 8; i++) {
                bitmap = (bitmap << 8) | (responseData[4 + i] & 0xFF);
            }
            
            int offset = 12; 
            
            for (int i = 2; i <= 64; i++) {
                boolean isPresent = ((bitmap >> (64 - i)) & 1) == 1;
                
                if (isPresent) {
                    FieldDef def = SCHEMA.get(i);
                    if (def == null) break; 
                    
                    String extracted = null;
                    int consumed = 0;
                    
                    if (offset >= responseData.length) break;

                    if (def.type == FieldType.NUMERIC) {
                        // ASCII Numeric: Fixed Bytes = Length
                        int byteLen = def.length;
                        if (offset + byteLen > responseData.length) return null;
                        extracted = new String(responseData, offset, byteLen, StandardCharsets.US_ASCII);
                        consumed = byteLen;
                    } else if (def.type == FieldType.ALPHA) {
                        // ASCII Alpha
                        int byteLen = def.length;
                        if (offset + byteLen > responseData.length) return null;
                        extracted = new String(responseData, offset, byteLen, StandardCharsets.US_ASCII);
                        consumed = byteLen;
                    } else if (def.type == FieldType.LLVAR) {
                        // Length: 2 bytes ASCII (e.g. "19" -> 0x31 0x39)
                        if (offset + 2 > responseData.length) return null;
                        String lenStr = new String(responseData, offset, 2, StandardCharsets.US_ASCII);
                        int len;
                        try {
                             len = Integer.parseInt(lenStr);
                        } catch (NumberFormatException e) {
                             return null; // Invalid length
                        }
                        consumed = 2; // Length bytes
                        
                        // Value: ASCII
                        if (offset + 2 + len > responseData.length) return null;
                        extracted = new String(responseData, offset + 2, len, StandardCharsets.US_ASCII);
                        consumed += len;
                    } else if (def.type == FieldType.LLLVAR) {
                        // Length: 3 bytes ASCII
                        if (offset + 3 > responseData.length) return null;
                        String lenStr = new String(responseData, offset, 3, StandardCharsets.US_ASCII);
                        int len;
                        try {
                             len = Integer.parseInt(lenStr);
                        } catch (NumberFormatException e) {
                             return null;
                        }
                        consumed = 3; 

                        if (offset + 3 + len > responseData.length) return null;
                        extracted = new String(responseData, offset + 3, len, StandardCharsets.US_ASCII);
                        consumed += len;
                    }
                    
                    if (i == fieldId) return extracted;
                    
                    offset += consumed;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }
    
    // --- Helpers ---

    private static boolean isNumericContent(String val) {
        if (val == null) return false;
        return val.matches("[0-9=]*");
    }

    private static String formatAlpha(String val, int len) {
        if (val == null) val = "";
        if (val.length() > len) return val.substring(0, len);
        return String.format("%-" + len + "s", val);
    }

    private static String leftPadZero(String val, int len) {
        if (val == null) val = "";
        if (val.length() > len) {
            // Should error ideally, but for safety truncate left or throw?
            // Usually if valid is longer than Schema, it's a bug.
            return val.substring(0, len); 
        }
        StringBuilder sb = new StringBuilder();
        while (sb.length() + val.length() < len) {
            sb.append('0');
        }
        sb.append(val);
        return sb.toString();
    }
    
    public static byte[] str2bcd(String str, boolean paddingLeft) {
        if (str == null) str = "";
        String s = str.toUpperCase().replace('=', 'D');
        if (s.length() % 2 != 0) {
            if (paddingLeft) s = "0" + s;
            else s = s + "F"; 
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int high = char2nibble(s.charAt(i * 2));
            int low = char2nibble(s.charAt(i * 2 + 1));
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }
    
    public static String bcd2str(byte[] data, int offset, int len, boolean padLeft) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            byte b = data[offset + i];
            sb.append(nibble2char((b >> 4) & 0xF));
            sb.append(nibble2char(b & 0xF));
        }
        return sb.toString();
    }
    
    private static int char2nibble(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return 0;
    }
    
    private static char nibble2char(int n) {
        if (n >= 0 && n <= 9) return (char) ('0' + n);
        if (n >= 10 && n <= 15) return (char) ('A' + n - 10);
        return '?';
    }
    
    private static byte intToBcd(int val) {
        int tens = val / 10;
        int ones = val % 10;
        return (byte) ((tens << 4) | ones);
    }
    
    private static int bcd2Int(byte b) {
        int high = (b >> 4) & 0xF;
        int low = b & 0xF;
        return high * 10 + low;
    }
    
    private static byte[] intToBcd2Bytes(int val) {
         byte[] b = new byte[2];
         b[0] = intToBcd(val / 100);
         b[1] = intToBcd(val % 100);
         return b;
    }

    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
    
    public static String unpackResponseCode(byte[] responseData) {
        // Quick extraction
        String rc = unpackField(responseData, 39);
        return rc == null ? "XX" : rc; 
    }
}
