package com.example.mysoftpos.iso8583.message;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.iso8583.spec.IsoField;
import com.example.mysoftpos.iso8583.message.IsoMessageStore;
import com.example.mysoftpos.iso8583.message.IsoMessage;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores last built/sent ISO message info for troubleshooting.
 *
 * Saved items (minimal):
 * - txnType
 * - mti
 * - payloadHex
 * - framedHex
 * - stan
 * - rrn
 */
public final class IsoMessageStore {
    private IsoMessageStore() {}

    private static final String PREFS = "SoftPOSLastMsg";

    public static void saveLast(Context ctx, TxnType type, IsoMessage msg, String payloadHex, String framedHex) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit()
                .putString("txnType", type == null ? null : type.name())
                .putString("mti", msg == null ? null : msg.getMti())
                .putString("stan11", msg == null ? null : msg.getField(IsoField.STAN_11))
                .putString("rrn37", msg == null ? null : msg.getField(IsoField.RRN_37))
                .putString("payloadHex", payloadHex)
                .putString("framedHex", framedHex)
                .apply();
    }

    public static String getLastPayloadHex(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("payloadHex", "");
    }

    public static String getLastFramedHex(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("framedHex", "");
    }
}






