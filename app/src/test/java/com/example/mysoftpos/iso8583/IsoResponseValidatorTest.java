package com.example.mysoftpos.iso8583;

import org.junit.Test;

public class IsoResponseValidatorTest {

    @Test(expected = IllegalStateException.class)
    public void purchaseResponse_wrongMti_shouldThrow() {
        IsoMessage resp = new IsoMessage("0430")
                .setField(39, "00");
        IsoValidator.validatePurchaseResponse(resp);
    }

    @Test(expected = IllegalStateException.class)
    public void adviceResponse_missingF39_shouldThrow() {
        IsoMessage resp = new IsoMessage("0430");
        IsoValidator.validateAdviceResponse(resp);
    }

    @Test
    public void adviceResponse_ok_shouldPass() {
        IsoMessage resp = new IsoMessage("0430")
                .setField(39, "00")
                .setField(IsoField.STAN_11, "000001")
                .setField(IsoField.RRN_37, "123456789012");
        IsoValidator.validateAdviceResponse(resp);
    }
}

