package com.example.mysoftpos.iso8583;

import org.junit.Test;

import static org.junit.Assert.*;

public class NapasField60And90Test {

    @Test
    public void field60_upiChipCaseA_length27() {
        String f60 = TransactionContext.buildField60UpiChipCaseA(
                '6', '1', "03", "000", '1');
        assertEquals(27, f60.length());
    }
}
