package com.example.mysoftpos.iso8583.message;
import com.example.mysoftpos.iso8583.spec.IsoSpec;
import com.example.mysoftpos.iso8583.message.IsoHeader;
import com.example.mysoftpos.iso8583.util.HexUtil;

import java.nio.charset.Charset;

/**
 * Adds a simple header to ISO8583 payload.
 *
 * Default style (common in many switches):
 * - 2-byte big-endian length prefix (payload length)
 * - optional TPDU (5 bytes) + optional header bytes.
 *
 * This class keeps it configurable so later you can match WAY4 exactly.
 */
public final class IsoHeader {
    private IsoHeader() {}

    /**
     * Prepend 2-byte big-endian length prefix of payload.
     * Output: [LEN_H][LEN_L] + payload
     */
    public static byte[] withLengthPrefix2(byte[] payload) {
        if (payload == null) payload = new byte[0];
        int len = payload.length;
        if (len > 0xFFFF) {
            throw new IllegalArgumentException("Payload too long: " + len);
        }

        byte[] out = new byte[2 + len];
        out[0] = (byte) ((len >> 8) & 0xFF);
        out[1] = (byte) (len & 0xFF);
        System.arraycopy(payload, 0, out, 2, len);
        return out;
    }

    /**
     * Prepend TPDU (hex string, e.g. "6000030000") before payload.
     */
    public static byte[] withTpduHex(byte[] payload, String tpduHex) {
        byte[] tpdu = HexUtil.hexToBytes(tpduHex);
        int payloadLen = payload == null ? 0 : payload.length;
        byte[] out = new byte[tpdu.length + payloadLen];
        System.arraycopy(tpdu, 0, out, 0, tpdu.length);
        if (payload != null) {
            System.arraycopy(payload, 0, out, tpdu.length, payload.length);
        }
        return out;
    }

    public static byte[] withAsciiHeader(byte[] payload, String headerAscii, Charset charset) {
        if (charset == null) charset = IsoSpec.CHARSET;
        byte[] head = headerAscii == null ? new byte[0] : headerAscii.getBytes(charset);
        int payloadLen = payload == null ? 0 : payload.length;
        byte[] out = new byte[head.length + payloadLen];
        System.arraycopy(head, 0, out, 0, head.length);
        if (payload != null) {
            System.arraycopy(payload, 0, out, head.length, payload.length);
        }
        return out;
    }
}






