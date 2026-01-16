package com.example.mysoftpos.iso8583;

import org.junit.Test;

import static org.junit.Assert.*;

public class IsoHeaderTest {

    @Test
    public void lengthPrefix2_isBigEndianPayloadLen() {
        byte[] payload = new byte[300];
        byte[] framed = IsoHeader.withLengthPrefix2(payload);
        assertEquals(302, framed.length);
        assertEquals((byte) 0x01, framed[0]);
        assertEquals((byte) 0x2C, framed[1]);
    }
}

