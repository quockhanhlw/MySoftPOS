package com.example.mysoftpos.iso8583.parser;
import com.example.mysoftpos.iso8583.message.IsoHeader;
import com.example.mysoftpos.iso8583.parser.IsoUnpacker;

import com.example.mysoftpos.iso8583.message.IsoMessage;
import com.example.mysoftpos.iso8583.spec.IsoSpec;
import com.example.mysoftpos.iso8583.util.IsoBitmapUtil;
import com.example.mysoftpos.iso8583.util.HexUtil;

import java.nio.charset.Charset;
import java.util.Set;
import java.util.TreeSet;

/**
 * Minimal unpacker for this project's pack format:
 * MTI (4 ASCII) + bitmap (8/16 binary) + fields encoded per {@link IsoSpec}.
 *
 * Notes:
 * - This is intended for simulator/testing and local debug.
 * - It does NOT handle character sets beyond US-ASCII.
 * - It assumes the incoming message uses the same field definitions as {@link IsoSpec#def(int)}.
 */
public final class IsoUnpacker {

    private IsoUnpacker() {}

    /**
     * Unpack a message that is framed by {@link IsoHeader#withLengthPrefix2(byte[])}.
     *
     * @param framed [lenHi][lenLo] + payload
     */
    public static IsoMessage unpackFramed(byte[] framed) {
        if (framed == null || framed.length < 2) {
            throw new IllegalArgumentException("framed too short");
        }
        int len = ((framed[0] & 0xFF) << 8) | (framed[1] & 0xFF);
        if (len < 0) {
            throw new IllegalArgumentException("invalid length prefix: " + len);
        }
        if (framed.length - 2 < len) {
            throw new IllegalArgumentException("framed length mismatch. prefix=" + len + " actualPayload=" + (framed.length - 2));
        }

        byte[] payload = new byte[len];
        System.arraycopy(framed, 2, payload, 0, len);
        return unpack(payload);
    }

    /**
     * Unpack a raw ISO8583 payload (no length prefix): MTI + bitmap + fields.
     */
    public static IsoMessage unpack(byte[] payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload == null");
        }
        if (payload.length < 4 + 8) {
            throw new IllegalArgumentException("payload too short for MTI+bitmap");
        }

        Charset cs = IsoSpec.CHARSET;
        int pos = 0;

        // MTI
        String mti = new String(payload, pos, 4, cs);
        if (!mti.matches("\\d{4}")) {
            throw new IllegalArgumentException("Invalid MTI: '" + mti + "'");
        }
        pos += 4;

        // Bitmap primary 8 bytes
        if (payload.length < pos + 8) {
            throw new IllegalArgumentException("payload too short for primary bitmap");
        }
        byte[] primary = new byte[8];
        System.arraycopy(payload, pos, primary, 0, 8);
        pos += 8;

        boolean hasSecondary = (primary[0] & 0x80) != 0;
        byte[] secondary = null;
        if (hasSecondary) {
            if (payload.length < pos + 8) {
                throw new IllegalArgumentException("payload too short for secondary bitmap");
            }
            secondary = new byte[8];
            System.arraycopy(payload, pos, secondary, 0, 8);
            pos += 8;
        }

        Set<Integer> fields = parseBitmapFields(primary, secondary);

        IsoMessage msg = new IsoMessage(mti);

        // Parse fields in ascending order
        for (int f : new TreeSet<>(fields)) {
            IsoSpec.FieldDef def = IsoSpec.def(f);
            ParsedField pf = decodeField(payload, pos, def);
            pos = pf.nextPos;
            msg.setField(f, pf.value);
        }

        // If extra trailing bytes exist, treat as error (helps catch spec mismatch)
        if (pos != payload.length) {
            throw new IllegalArgumentException("Trailing bytes after last field. pos=" + pos + " len=" + payload.length);
        }

        return msg;
    }

    private static final class ParsedField {
        final String value;
        final int nextPos;

        ParsedField(String value, int nextPos) {
            this.value = value;
            this.nextPos = nextPos;
        }
    }

    private static ParsedField decodeField(byte[] payload, int pos, IsoSpec.FieldDef def) {
        Charset cs = IsoSpec.CHARSET;

        int dataLen;
        if (def.lenType == IsoSpec.LenType.FIXED) {
            dataLen = def.maxLen;
        } else if (def.lenType == IsoSpec.LenType.LLVAR) {
            if (payload.length < pos + 2) {
                throw new IllegalArgumentException("Field " + def.field + " missing LLVAR length");
            }
            String sLen = new String(payload, pos, 2, cs);
            if (!sLen.matches("\\d{2}")) {
                throw new IllegalArgumentException("Field " + def.field + " invalid LLVAR length: '" + sLen + "'");
            }
            dataLen = Integer.parseInt(sLen);
            pos += 2;
        } else if (def.lenType == IsoSpec.LenType.LLLVAR) {
            if (payload.length < pos + 3) {
                throw new IllegalArgumentException("Field " + def.field + " missing LLLVAR length");
            }
            String sLen = new String(payload, pos, 3, cs);
            if (!sLen.matches("\\d{3}")) {
                throw new IllegalArgumentException("Field " + def.field + " invalid LLLVAR length: '" + sLen + "'");
            }
            dataLen = Integer.parseInt(sLen);
            pos += 3;
        } else {
            throw new IllegalStateException("Unknown lenType: " + def.lenType);
        }

        if (dataLen < 0 || dataLen > def.maxLen) {
            throw new IllegalArgumentException("Field " + def.field + " length out of range: " + dataLen + " (max=" + def.maxLen + ")");
        }

        if (payload.length < pos + dataLen) {
            throw new IllegalArgumentException("Field " + def.field + " truncated. Need " + dataLen + " bytes at pos=" + pos);
        }

        byte[] raw = new byte[dataLen];
        System.arraycopy(payload, pos, raw, 0, dataLen);
        int nextPos = pos + dataLen;

        String value;
        if (def.contentType == IsoSpec.ContentType.ASCII) {
            value = new String(raw, cs);
        } else if (def.contentType == IsoSpec.ContentType.HEX_BYTES) {
            value = HexUtil.bytesToHex(raw);
        } else {
            throw new IllegalStateException("Unsupported contentType: " + def.contentType);
        }

        return new ParsedField(value, nextPos);
    }

    private static Set<Integer> parseBitmapFields(byte[] primary, byte[] secondary) {
        Set<Integer> out = new TreeSet<>();

        // Bits 1..64
        for (int bit = 1; bit <= 64; bit++) {
            if (bit == 1) {
                // bit 1 indicates secondary bitmap, not a real data element
                continue;
            }
            if (isBitSet(primary, bit)) {
                out.add(bit);
            }
        }

        if (secondary != null) {
            for (int bit = 65; bit <= 128; bit++) {
                if (isBitSet(secondary, bit - 64)) {
                    out.add(bit);
                }
            }
        }

        return out;
    }

    private static boolean isBitSet(byte[] bitmap, int fieldNumber1To64) {
        int bitIndex = fieldNumber1To64 - 1;
        int byteIndex = bitIndex / 8;
        int bitInByte = bitIndex % 8;
        int mask = 1 << (7 - bitInByte);
        return (bitmap[byteIndex] & mask) != 0;
    }
}








