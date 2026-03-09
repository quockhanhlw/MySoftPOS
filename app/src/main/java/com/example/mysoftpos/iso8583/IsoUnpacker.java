package com.example.mysoftpos.iso8583;

/** Backward-compatible unpacker facade for older unit tests. */
public final class IsoUnpacker {
    private IsoUnpacker() {
    }

    public static IsoMessage unpack(byte[] payload) {
        return IsoMessage.fromInternal(com.example.mysoftpos.iso8583.parser.IsoUnpacker.unpack(payload));
    }

    public static IsoMessage unpackFramed(byte[] framed) {
        return IsoMessage.fromInternal(com.example.mysoftpos.iso8583.parser.IsoUnpacker.unpackFramed(framed));
    }
}

