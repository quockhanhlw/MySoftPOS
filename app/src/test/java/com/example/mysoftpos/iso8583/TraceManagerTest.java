package com.example.mysoftpos.iso8583;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Local unit test: verify STAN padding helper without Android Context.
 *
 * Note: Context-based persistence is covered by instrumentation tests.
 */
public class TraceManagerTest {

    @Test
    public void pad6_leftZeros() {
        assertEquals("000001", TraceManager.pad6(1));
        assertEquals("000010", TraceManager.pad6(10));
        assertEquals("999999", TraceManager.pad6(999999));
    }
}
