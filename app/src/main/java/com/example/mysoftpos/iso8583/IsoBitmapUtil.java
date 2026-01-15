package com.example.mysoftpos.iso8583;

import java.util.Set;

/** Builds ISO8583 bitmap (binary 8/16 bytes). */
public final class IsoBitmapUtil {
    private IsoBitmapUtil() {}

    public static byte[] buildBitmap(Set<Integer> fields) {
        boolean hasSecondary = false;
        for (Integer f : fields) {
            if (f != null && f > 64) {
                hasSecondary = true;
                break;
            }
        }

        int bytes = hasSecondary ? 16 : 8;
        byte[] bitmap = new byte[bytes];

        // if secondary present, set bit-1
        if (hasSecondary) {
            setBit(bitmap, 1);
        }

        for (Integer f : fields) {
            if (f == null) continue;
            if (f == 1) continue; // reserved for secondary bitmap indicator
            setBit(bitmap, f);
        }

        return bitmap;
    }

    private static void setBit(byte[] bitmap, int fieldNumber) {
        if (fieldNumber < 1 || fieldNumber > 128) {
            throw new IllegalArgumentException("Invalid field number: " + fieldNumber);
        }
        int bitIndex = fieldNumber - 1; // 0-based
        int byteIndex = bitIndex / 8;
        int bitInByte = bitIndex % 8;
        bitmap[byteIndex] |= (byte) (1 << (7 - bitInByte));
    }
}

