package com.example.mysoftpos.iso8583;

/** Backward-compatible header helpers for the root iso8583 package. */
public final class IsoHeader {
    private IsoHeader() {
    }

    public static byte[] withLengthPrefix2(byte[] payload) {
        return com.example.mysoftpos.iso8583.message.IsoHeader.withLengthPrefix2(payload);
    }
}

