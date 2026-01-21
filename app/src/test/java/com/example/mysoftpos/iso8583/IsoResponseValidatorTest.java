package com.example.mysoftpos.iso8583;

import org.junit.Test;

public class IsoResponseValidatorTest {

    @Test(expected = IllegalStateException.class)
    public void purchaseResponse_wrongMti_shouldThrow() {
        IsoMessage resp = new IsoMessage("0430")
                .setField(39, "00");
        IsoValidator.validatePurchaseResponse(resp);
    }
}
