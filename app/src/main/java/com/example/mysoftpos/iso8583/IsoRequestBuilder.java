package com.example.mysoftpos.iso8583;

import com.example.mysoftpos.domain.model.CardInputData;

/** Backward-compatible request builder facade for older unit tests. */
public final class IsoRequestBuilder {
    private IsoRequestBuilder() {
    }

    public static IsoMessage buildPurchase(TransactionContext ctx) {
        CardInputData card = new CardInputData(ctx.pan2, ctx.expiry14, ctx.posEntryMode22, ctx.track2_35);
        if (ctx.pinBlock52 != null && !ctx.pinBlock52.isEmpty()) {
            card.setPinBlock(ctx.pinBlock52);
        }
        return IsoMessage.fromInternal(
                com.example.mysoftpos.iso8583.builder.Iso8583Builder.buildPurchaseMsg(ctx, card));
    }
}

