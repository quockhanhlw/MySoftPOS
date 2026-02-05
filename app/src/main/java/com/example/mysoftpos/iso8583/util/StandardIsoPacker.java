package com.example.mysoftpos.iso8583.util;

import com.example.mysoftpos.iso8583.util.StandardIsoPacker;

import com.example.mysoftpos.iso8583.message.IsoMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Standard ISO8583 Packer (Packed BCD Format).
 * - MTI: 4 chars (ASCII 0200)
 * - Bitmap: 8 bytes (Primary) + Optional 8 bytes (Secondary)
 * - Numeric Fields: Packed BCD (n/2 bytes) or ASCII (schema dependent)
 * - LLVAR/LLLVAR: Length BCD/ASCII, Value BCD/ASCII
 */
public class StandardIsoPacker {

    private enum FieldType {
        NUMERIC, // n - Fixed length, ASCII digits (or BCD if implemented)
        ALPHA, // a/an/ans - Fixed length, ASCII
        LLVAR, // Variable length 2 digits
        LLLVAR, // Variable length 3 digits
        BINARY // b - Fixed length binary (Raw bytes)
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
        // Define common fields for Purchase (0200) & Balance (0200)
        SCHEMA.put(2, new FieldDef(FieldType.LLVAR, 19)); // PAN
        SCHEMA.put(3, new FieldDef(FieldType.NUMERIC, 6)); // Processing Code
        SCHEMA.put(4, new FieldDef(FieldType.NUMERIC, 12)); // Amount
        SCHEMA.put(7, new FieldDef(FieldType.NUMERIC, 10)); // Transmission Date/Time
        SCHEMA.put(11, new FieldDef(FieldType.NUMERIC, 6)); // STAN
        SCHEMA.put(12, new FieldDef(FieldType.NUMERIC, 6)); // Local Time
        SCHEMA.put(13, new FieldDef(FieldType.NUMERIC, 4)); // Local Date
        SCHEMA.put(14, new FieldDef(FieldType.NUMERIC, 4)); // Expiry Date
        SCHEMA.put(15, new FieldDef(FieldType.NUMERIC, 4)); // Settlement Date (MMdd)
        SCHEMA.put(18, new FieldDef(FieldType.NUMERIC, 4)); // MCC
        SCHEMA.put(19, new FieldDef(FieldType.NUMERIC, 3)); // Country Code
        SCHEMA.put(22, new FieldDef(FieldType.NUMERIC, 3)); // POS Entry Mode
        SCHEMA.put(23, new FieldDef(FieldType.NUMERIC, 3)); // Card Seq
        SCHEMA.put(25, new FieldDef(FieldType.NUMERIC, 2)); // POS Condition Code
        SCHEMA.put(32, new FieldDef(FieldType.LLVAR, 11)); // Acquirer ID
        SCHEMA.put(33, new FieldDef(FieldType.LLVAR, 11)); // Forwarding ID
        SCHEMA.put(35, new FieldDef(FieldType.LLVAR, 37)); // Track 2
        SCHEMA.put(37, new FieldDef(FieldType.ALPHA, 12)); // RRN (an12)
        SCHEMA.put(38, new FieldDef(FieldType.ALPHA, 6)); // Auth Code (an6)
        SCHEMA.put(39, new FieldDef(FieldType.ALPHA, 2)); // Response Code (an2)
        SCHEMA.put(41, new FieldDef(FieldType.ALPHA, 8)); // TID (ans8)
        SCHEMA.put(42, new FieldDef(FieldType.ALPHA, 15)); // MID (ans15)
        SCHEMA.put(43, new FieldDef(FieldType.ALPHA, 40)); // Name/Location
        SCHEMA.put(49, new FieldDef(FieldType.NUMERIC, 3)); // Currency
        SCHEMA.put(52, new FieldDef(FieldType.BINARY, 8)); // PIN Block (64 bits)
        SCHEMA.put(54, new FieldDef(FieldType.LLLVAR, 120)); // Additional Amounts
        SCHEMA.put(55, new FieldDef(FieldType.LLLVAR, 510)); // ICC Data (Hex String)
        SCHEMA.put(60, new FieldDef(FieldType.LLLVAR, 60)); // Reserved Private (De 60)
        SCHEMA.put(62, new FieldDef(FieldType.LLLVAR, 999)); // Reserved (Private)
        SCHEMA.put(63, new FieldDef(FieldType.LLLVAR, 999)); // Reserved Private (Token/Data)

        // Secondary Bitmap Fields
        SCHEMA.put(90, new FieldDef(FieldType.NUMERIC, 42)); // Original Data Elements (Reversal)
        SCHEMA.put(128, new FieldDef(FieldType.BINARY, 8)); // MAC (64 bits)
    }

    public static byte[] pack(IsoMessage msg) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // 1. MTI (ASCII 4 bytes)
        baos.write(msg.getMti().getBytes(StandardCharsets.UTF_8));

        // 2. Bitmap Generation
        Set<Integer> presentFields = msg.getFieldNumbers();
        boolean hasSecondary = false;

        // Check if we need Secondary Bitmap
        for (int f : presentFields) {
            if (f > 64) {
                hasSecondary = true;
                break;
            }
        }

        // Primary Bitmap (Fields 1-64)
        long primaryBitmap = 0;
        if (hasSecondary) {
            primaryBitmap |= (1L << 63); // Bit 1 is strictly for Secondary Bitmap Presence
        }
        for (int field : presentFields) {
            if (field > 1 && field <= 64) {
                primaryBitmap |= (1L << (64 - field));
            }
        }

        for (int i = 7; i >= 0; i--) {
            baos.write((int) (primaryBitmap >> (8 * i)) & 0xFF);
        }

        // Secondary Bitmap (Fields 65-128)
        if (hasSecondary) {
            long secondaryBitmap = 0;
            for (int field : presentFields) {
                if (field > 64 && field <= 128) {
                    secondaryBitmap |= (1L << (128 - field));
                }
            }
            for (int i = 7; i >= 0; i--) {
                baos.write((int) (secondaryBitmap >> (8 * i)) & 0xFF);
            }
        }

        // 3. Fields Loop
        int maxField = hasSecondary ? 128 : 64;
        for (int i = 2; i <= maxField; i++) {
            if (presentFields.contains(i)) {
                String val = msg.getField(i);
                FieldDef def = SCHEMA.get(i);
                if (def == null)
                    continue;

                // FORCE LLVAR for DE 32 (Maestro Fix)
                if (i == 32) {
                    int len = val.length();
                    String lenStr = String.format(Locale.ROOT, "%02d", len);
                    baos.write(lenStr.getBytes(StandardCharsets.US_ASCII));
                    baos.write(val.getBytes(StandardCharsets.US_ASCII));
                    continue; // Done for DE 32
                }

                if (def.type == FieldType.NUMERIC) {
                    // ASCII Numeric
                    String padded = leftPadZero(val, def.length);
                    baos.write(padded.getBytes(StandardCharsets.US_ASCII));
                } else if (def.type == FieldType.ALPHA) {
                    // ASCII Alpha
                    baos.write(formatAlpha(val, def.length).getBytes(StandardCharsets.US_ASCII));
                } else if (def.type == FieldType.BINARY) {
                    // BINARY (Raw Bytes)
                    // Input 'val' assumed to be Hex String for ease of use in Builder
                    byte[] raw = hexToBytes(val);
                    if (raw.length != def.length) {
                        // Padding or Error?
                        // For safety, dry run pad
                        byte[] padded = new byte[def.length];
                        System.arraycopy(raw, 0, padded, 0, Math.min(raw.length, def.length));
                        baos.write(padded);
                    } else {
                        baos.write(raw);
                    }
                } else if (def.type == FieldType.LLVAR) {
                    int len = val.length();
                    String lenStr = String.format(Locale.ROOT, "%02d", len);
                    baos.write(lenStr.getBytes(StandardCharsets.US_ASCII));
                    baos.write(val.getBytes(StandardCharsets.US_ASCII));
                    if (i == 32) {
                        System.out.println("DEBUG_ISO_PACKER_32: Len=" + lenStr + " Val=" + val);
                        android.util.Log.d("ISO_DEBUG", "DE 32 Packed: " + lenStr + val);
                    }
                } else if (def.type == FieldType.LLLVAR) {
                    int len = val.length();
                    String lenStr = String.format(Locale.ROOT, "%03d", len);
                    baos.write(lenStr.getBytes(StandardCharsets.US_ASCII));
                    baos.write(val.getBytes(StandardCharsets.US_ASCII));
                }
            }
        }

        return baos.toByteArray();
    }

    public IsoMessage unpack(byte[] responseData) throws Exception {
        if (responseData == null || responseData.length < 12)
            throw new Exception("Invalid response length");

        // 1. MTI
        String mti = new String(responseData, 0, 4, StandardCharsets.US_ASCII);
        IsoMessage msg = new IsoMessage(mti);

        int offset = 4;

        // 2. Primary Bitmap
        long primaryBitmap = 0;
        for (int i = 0; i < 8; i++) {
            primaryBitmap = (primaryBitmap << 8) | (responseData[offset++] & 0xFF);
        }

        // 3. Check for Secondary Bitmap (Bit 1 aka 0x80 of first byte)
        boolean hasSecondary = (primaryBitmap & (1L << 63)) != 0;
        long secondaryBitmap = 0;

        if (hasSecondary) {
            for (int i = 0; i < 8; i++) {
                secondaryBitmap = (secondaryBitmap << 8) | (responseData[offset++] & 0xFF);
            }
        }

        // 4. Fields
        int maxField = hasSecondary ? 128 : 64;

        for (int i = 2; i <= maxField; i++) {
            boolean isPresent;
            if (i <= 64) {
                isPresent = ((primaryBitmap >> (64 - i)) & 1) == 1;
            } else {
                isPresent = ((secondaryBitmap >> (128 - i)) & 1) == 1;
            }

            if (isPresent) {
                FieldDef def = SCHEMA.get(i);
                if (def == null)
                    continue;

                String extracted = null;
                int consumed = 0;

                if (offset >= responseData.length)
                    break;

                if (def.type == FieldType.NUMERIC || def.type == FieldType.ALPHA) {
                    int byteLen = def.length;
                    if (offset + byteLen > responseData.length)
                        break;
                    extracted = new String(responseData, offset, byteLen, StandardCharsets.US_ASCII);
                    consumed = byteLen;
                } else if (def.type == FieldType.BINARY) {
                    int byteLen = def.length;
                    if (offset + byteLen > responseData.length)
                        break;
                    // Extract as Hex String for Builder compatibility
                    byte[] raw = new byte[byteLen];
                    System.arraycopy(responseData, offset, raw, 0, byteLen);
                    extracted = bytesToHex(raw);
                    consumed = byteLen;
                } else if (def.type == FieldType.LLVAR) {
                    if (offset + 2 > responseData.length)
                        break;
                    String lenStr = new String(responseData, offset, 2, StandardCharsets.US_ASCII);
                    int len = Integer.parseInt(lenStr);
                    consumed = 2;
                    if (offset + 2 + len > responseData.length)
                        break;
                    extracted = new String(responseData, offset + 2, len, StandardCharsets.US_ASCII);
                    consumed += len;
                } else if (def.type == FieldType.LLLVAR) {
                    if (offset + 3 > responseData.length)
                        break;
                    String lenStr = new String(responseData, offset, 3, StandardCharsets.US_ASCII);
                    int len = Integer.parseInt(lenStr);
                    consumed = 3;
                    if (offset + 3 + len > responseData.length)
                        break;
                    extracted = new String(responseData, offset + 3, len, StandardCharsets.US_ASCII);
                    consumed += len;
                }

                if (extracted != null) {
                    msg.setField(i, extracted);
                }
                offset += consumed;
            }
        }
        return msg;
    }

    // --- Helpers ---
    private static String formatAlpha(String val, int len) {
        if (val == null)
            val = "";
        if (val.length() > len)
            return val.substring(0, len);
        return String.format("%-" + len + "s", val);
    }

    private static String leftPadZero(String val, int len) {
        if (val == null)
            val = "";
        if (val.length() > len) {
            return val.substring(0, len);
        }
        StringBuilder sb = new StringBuilder();
        while (sb.length() + val.length() < len) {
            sb.append('0');
        }
        sb.append(val);
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null)
            return new byte[0];
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null)
            return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    // Unpacker logic for Response (BCD Support)
    public static String unpackField(byte[] responseData, int fieldId) {
        try {
            if (responseData == null || responseData.length < 12)
                return null;

            IsoMessage msg = new StandardIsoPacker().unpack(responseData);
            return msg.getField(fieldId);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Kept for compatibility if used elsewhere, but ideally redundant
    public static String unpackResponseCode(byte[] responseData) {
        try {
            return new StandardIsoPacker().unpack(responseData).getField(39);
        } catch (Exception e) {
            return "XX";
        }
    }

    public static String logIsoMessage(IsoMessage msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("MTI: ").append(msg.getMti()).append("\n");

        // Calculate and Log Primary Bitmap (DE 001) for visibility
        java.util.Set<Integer> fields = msg.getFieldNumbers();
        long primaryBitmap = 0;
        boolean hasSecondary = false;
        for (int f : fields) {
            if (f > 64)
                hasSecondary = true;
        }
        if (hasSecondary)
            primaryBitmap |= (1L << 63);
        for (int f : fields) {
            if (f > 1 && f <= 64)
                primaryBitmap |= (1L << (64 - f));
        }
        sb.append("DE 001: ").append(String.format(Locale.ROOT, "%016X", primaryBitmap)).append("\n");

        for (int field : new java.util.TreeSet<>(msg.getFieldNumbers())) {
            sb.append("DE ").append(String.format(Locale.ROOT, "%03d", field)).append(": ").append(msg.getField(field))
                    .append("\n");
        }
        return sb.toString();
    }
}
