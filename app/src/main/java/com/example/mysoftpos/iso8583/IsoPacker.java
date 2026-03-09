package com.example.mysoftpos.iso8583;

/** Backward-compatible packer facade for older unit tests. */
public final class IsoPacker {
    private IsoPacker() {
    }

    public static byte[] pack(IsoMessage message) {
        try {
            return com.example.mysoftpos.iso8583.util.StandardIsoPacker.pack(message.unwrap());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to pack ISO message", e);
        }
    }
}

