package com.example.mysoftpos.iso8583;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Validates required and conditional fields presence. */
public final class IsoValidator {
    private IsoValidator() {}

    public static void validatePurchase(IsoMessage msg) {
        validateRequired(msg, FieldRules.PURCHASE_REQUIRED, "PURCHASE");
        validatePurchaseConditional(msg);
    }

    public static void validateReversal(IsoMessage msg) {
        validateRequired(msg, FieldRules.REVERSAL_REQUIRED, "VOID/REVERSAL");
        validateReversalConditional(msg);
    }

    private static void validateRequired(IsoMessage msg, Set<Integer> required, String name) {
        List<Integer> missing = new ArrayList<>();
        for (int f : required) {
            if (!msg.hasField(f) || isBlank(msg.getField(f))) {
                missing.add(f);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(name + " missing required fields: " + missing);
        }
    }

    /**
     * Purchase conditional rules (common practical rules):
     * - If POS Entry Mode indicates magstripe/manual => require Track2 (35) or PAN(2) already mandatory.
     * - If chip/ICC => require field 55.
     * - If encrypt PIN => require 52.
     * - Country(19) and CardSeq(23) if provided by kernel.
     * - Field60 when host requires proprietary data; validate only if present requirement triggered by caller.
     */
    private static void validatePurchaseConditional(IsoMessage msg) {
        // PIN block
        if (msg.hasField(IsoField.PIN_BLOCK_52) && isBlank(msg.getField(IsoField.PIN_BLOCK_52))) {
            throw new IllegalStateException("PURCHASE field 52 is blank");
        }

        // If entry mode starts with 05/07 (ICC) -> require 55
        String entryMode = msg.getField(IsoField.POS_ENTRY_MODE_22);
        if (!isBlank(entryMode)) {
            if (entryMode.startsWith("05") || entryMode.startsWith("07")) {
                if (!msg.hasField(IsoField.ICC_DATA_55) || isBlank(msg.getField(IsoField.ICC_DATA_55))) {
                    throw new IllegalStateException("PURCHASE requires field 55 for ICC entry mode (F22=" + entryMode + ")");
                }
            }
        }
    }

    private static void validateReversalConditional(IsoMessage msg) {
        // For reversals, if ICC reversal keep 55
        String entryMode = msg.getField(IsoField.POS_ENTRY_MODE_22);
        if (!isBlank(entryMode)) {
            if (entryMode.startsWith("05") || entryMode.startsWith("07")) {
                if (!msg.hasField(IsoField.ICC_DATA_55) || isBlank(msg.getField(IsoField.ICC_DATA_55))) {
                    throw new IllegalStateException("REVERSAL requires field 55 for ICC entry mode (F22=" + entryMode + ")");
                }
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

