package com.example.mysoftpos.iso8583;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.TreeSet;

/**
 * Packs an IsoMessage to raw bytes: MTI + bitmap + fields.
 *
 * Assumptions:
 * - No TPDU, no length header
 * - Bitmap: binary
 * - Field encodings driven by IsoSpec.def()
 */
public final class IsoPacker {
    private IsoPacker() {}

    public static byte[] pack(IsoMessage msg) {
        if (msg == null) throw new IllegalArgumentException("msg == null");

        Charset cs = IsoSpec.CHARSET;
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // MTI
        out.writeBytes(msg.getMti().getBytes(cs));

        // Bitmap
        byte[] bitmap = IsoBitmapUtil.buildBitmap(msg.getFieldNumbers());
        out.writeBytes(bitmap);

        // Fields in ascending order
        Set<Integer> ordered = new TreeSet<>(msg.getFieldNumbers());
        for (int f : ordered) {
            IsoSpec.FieldDef def = IsoSpec.def(f);
            String value = msg.getField(f);
            if (value == null) {
                continue;
            }
            out.writeBytes(encodeField(def, value, cs));
        }

        return out.toByteArray();
    }

    private static byte[] encodeField(IsoSpec.FieldDef def, String value, Charset cs) {
        byte[] raw;
        switch (def.contentType) {
            case ASCII:
                raw = value.getBytes(cs);
                break;
            case HEX_BYTES:
                raw = HexUtil.hexToBytes(value);
                break;
            default:
                throw new IllegalStateException("Unsupported contentType: " + def.contentType);
        }

        if (def.lenType == IsoSpec.LenType.FIXED) {
            if (raw.length != def.maxLen) {
                throw new IllegalArgumentException("Field " + def.field + " length=" + raw.length + ", expected fixed=" + def.maxLen);
            }
            return raw;
        }

        if (raw.length > def.maxLen) {
            throw new IllegalArgumentException("Field " + def.field + " too long: " + raw.length + " > " + def.maxLen);
        }

        int len = raw.length;
        String prefix;
        if (def.lenType == IsoSpec.LenType.LLVAR) {
            if (len > 99) {
                throw new IllegalArgumentException("Field " + def.field + " LLVAR length > 99: " + len);
            }
            prefix = String.format("%02d", len);
        } else if (def.lenType == IsoSpec.LenType.LLLVAR) {
            if (len > 999) {
                throw new IllegalArgumentException("Field " + def.field + " LLLVAR length > 999: " + len);
            }
            prefix = String.format("%03d", len);
        } else {
            throw new IllegalStateException("Unknown lenType: " + def.lenType);
        }

        byte[] p = prefix.getBytes(cs);
        byte[] result = new byte[p.length + raw.length];
        System.arraycopy(p, 0, result, 0, p.length);
        System.arraycopy(raw, 0, result, p.length, raw.length);
        return result;
    }
}

