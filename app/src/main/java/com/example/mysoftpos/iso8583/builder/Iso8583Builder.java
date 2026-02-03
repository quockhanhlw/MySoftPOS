package com.example.mysoftpos.iso8583.builder;
import com.example.mysoftpos.iso8583.builder.Iso8583Builder;

import com.example.mysoftpos.iso8583.message.IsoMessage;
import com.example.mysoftpos.iso8583.spec.IsoField;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.iso8583.TransactionContext;

import com.example.mysoftpos.domain.model.CardInputData;
import java.util.Map;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.iso8583.spec.IsoField;

/**
 * ISO8583 Builder - Production Grade.
 * Adheres to Napas Specifications.
 */
public class Iso8583Builder {

    private Iso8583Builder() {
    }

    /**
     * Build Purchase Request (0200).
     * 
     * DE Conditional Logic:
     * - DE 22 = 011, 012, 022 (Manual/Magstripe): Include DE 14 (Expiry), Exclude
     * DE 35
     * - DE 22 = 901, 902 (Contactless): Include DE 35 (Track 2), Exclude DE 14
     */
    public static IsoMessage buildPurchaseMsg(TransactionContext ctx, CardInputData card) {
        IsoMessage m = new IsoMessage("0200");

        // 1. Mandatory Fields
        m.setField(IsoField.PAN_2, card.getPan());
        m.setField(IsoField.PROCESSING_CODE_3, ctx.processingCode3);
        m.setField(IsoField.AMOUNT_4, ctx.amount4);
        m.setField(IsoField.TRANSMISSION_DATETIME_7, ctx.transmissionDt7);
        m.setField(IsoField.STAN_11, ctx.stan11);
        m.setField(IsoField.LOCAL_TIME_12, ctx.localTime12);
        m.setField(IsoField.LOCAL_DATE_13, ctx.localDate13);
        m.setField(IsoField.MERCHANT_TYPE_18, ctx.mcc18);

        // DE 22: POS Entry Mode
        String de22 = card.getPosEntryMode();
        m.setField(IsoField.POS_ENTRY_MODE_22, de22);

        // Conditional DE 14 / DE 35
        if ("011".equals(de22) || "012".equals(de22) || "022".equals(de22)) {
            // Manual/Magstripe → Include Expiry (DE 14), Exclude Track 2 (DE 35)
            m.setField(IsoField.EXPIRATION_DATE_14, card.getExpiryDate());
        } else if ("901".equals(de22) || "902".equals(de22)) {
            // Contactless → Include Track 2 (DE 35), Exclude Expiry (DE 14)
            if (card.getTrack2() != null && !card.getTrack2().isEmpty()) {
                m.setField(IsoField.TRACK2_35, card.getTrack2().replace('=', 'D'));
            }
        }

        m.setField(IsoField.POS_CONDITION_CODE_25, ctx.posCondition25);
        m.setField(IsoField.ACQUIRER_ID_32, ctx.acquirerId32);

        m.setField(IsoField.RRN_37, ctx.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, formatTerminalId(ctx.terminalId41));
        m.setField(IsoField.MERCHANT_ID_42, formatMerchantId(ctx.merchantId42));

        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, ctx.merchantNameLocation43);
        m.setField(IsoField.CURRENCY_CODE_49, ctx.currency49);

        // PIN Block (only for 011, 901 cases with PIN)
        if (ctx.encryptPin && ctx.pinBlock52 != null) {
            m.setField(IsoField.PIN_BLOCK_52, ctx.pinBlock52);
        }

        // Fix: Correct mapping for DE 60
        if (ctx.field60 != null) {
            m.setField(60, ctx.field60);
        }

        return m;
    }

    /**
     * Build Balance Inquiry Request (0200).
     * 
     * DE Conditional Logic:
     * - DE 22 = 011, 012 (Manual): Include DE 14 (Expiry), Exclude DE 35
     * - DE 22 = 901, 902 (Contactless): Include DE 35 (Track 2), Exclude DE 14
     */
    public static IsoMessage buildBalanceMsg(TransactionContext ctx, CardInputData card) {
        IsoMessage m = new IsoMessage("0200");

        m.setField(IsoField.PAN_2, card.getPan());
        m.setField(IsoField.PROCESSING_CODE_3, ctx.processingCode3); // Balance = 300000
        m.setField(IsoField.AMOUNT_4, "000000000000"); // Zero-filled
        m.setField(IsoField.TRANSMISSION_DATETIME_7, ctx.transmissionDt7);
        m.setField(IsoField.STAN_11, ctx.stan11);
        m.setField(IsoField.LOCAL_TIME_12, ctx.localTime12);
        m.setField(IsoField.LOCAL_DATE_13, ctx.localDate13); // GMT+7

        // DE 22: POS Entry Mode
        String de22 = card.getPosEntryMode();
        m.setField(IsoField.POS_ENTRY_MODE_22, de22);

        // Conditional DE 14 / DE 35
        if ("011".equals(de22) || "012".equals(de22) || "022".equals(de22)) {
            // Manual/Magstripe Entry → Include Expiry (DE 14), Exclude Track 2 (DE 35)
            m.setField(IsoField.EXPIRATION_DATE_14, card.getExpiryDate());
        } else if ("901".equals(de22) || "902".equals(de22)) {
            // Contactless → Include Track 2 (DE 35), Exclude Expiry (DE 14)
            if (card.getTrack2() != null && !card.getTrack2().isEmpty()) {
                m.setField(IsoField.TRACK2_35, card.getTrack2().replace('=', 'D'));
            }
        }

        m.setField(IsoField.MERCHANT_TYPE_18, ctx.mcc18); // 6011 for Balance
        m.setField(IsoField.POS_CONDITION_CODE_25, ctx.posCondition25);
        m.setField(IsoField.ACQUIRER_ID_32, ctx.acquirerId32);
        m.setField(IsoField.RRN_37, ctx.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, formatTerminalId(ctx.terminalId41));
        m.setField(IsoField.MERCHANT_ID_42, formatMerchantId(ctx.merchantId42));
        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, ctx.merchantNameLocation43);
        m.setField(IsoField.CURRENCY_CODE_49, ctx.currency49);

        // No PIN Block for Balance Inquiry per user spec

        return m;
    }

    /**
     * Build Reversal Advice (0420).
     * 
     * DEs from 0200: 2, 3, 4, 12, 13, 18, 32, 37, 41, 42, 43, 49
     * DEs new: 7 (new), 11 (new trace), 90 (original data)
     * 
     * DE 90 Format (42 bytes):
     * - Position 1-4 (n-4): Original MTI
     * - Position 5-10 (n-6): Original Trace Number (DE 11)
     * - Position 11-20 (n-10): Original Transmission DateTime (DE 07) - MMDDHHmmss
     * - Position 21-31 (n-11): Original Acquiring Institution ID (DE 32)
     * - Position 32-42 (n-11): Original Forwarding Institution ID (DE 33) - Fill
     * with '0'
     *
     * @param originalCtx The context of the original FAILED transaction.
     * @param card        Card Data.
     * @param newTrace    The NEW Trace Number (DE 11).
     */
    public static IsoMessage buildReversalAdvice(TransactionContext originalCtx, CardInputData card, String newTrace) {
        IsoMessage m = new IsoMessage("0420");

        // DE 2: PAN (from 0200)
        m.setField(IsoField.PAN_2, card.getPan());

        // DE 3: Processing Code (from 0200)
        String proc = (originalCtx.txnType == TxnType.BALANCE_INQUIRY) ? "300000" : "000000";
        m.setField(IsoField.PROCESSING_CODE_3, proc);

        // DE 4: Amount (from 0200)
        m.setField(IsoField.AMOUNT_4, originalCtx.amount4);

        // DE 7: Transmission DateTime (NEW for reversal)
        m.setField(IsoField.TRANSMISSION_DATETIME_7, originalCtx.transmissionDt7);

        // DE 11: STAN (NEW trace number for reversal)
        m.setField(IsoField.STAN_11, newTrace);

        // DE 12: Local Time (from 0200)
        m.setField(IsoField.LOCAL_TIME_12, originalCtx.localTime12);

        // DE 13: Local Date (from 0200)
        m.setField(IsoField.LOCAL_DATE_13, originalCtx.localDate13);

        // DE 18: Merchant Type (from 0200)
        m.setField(IsoField.MERCHANT_TYPE_18, originalCtx.mcc18 != null ? originalCtx.mcc18
                : (originalCtx.txnType == TxnType.BALANCE_INQUIRY ? "6011" : "5411"));

        // DE 32: Acquirer ID (from 0200)
        String acquirerId = originalCtx.acquirerId32 != null ? originalCtx.acquirerId32 : "970406";
        m.setField(IsoField.ACQUIRER_ID_32, acquirerId);

        // DE 37: RRN (from 0200)
        m.setField(IsoField.RRN_37, originalCtx.rrn37);

        // DE 41: Terminal ID (from 0200)
        m.setField(IsoField.TERMINAL_ID_41, formatTerminalId(originalCtx.terminalId41));

        // DE 42: Merchant ID (from 0200)
        m.setField(IsoField.MERCHANT_ID_42, formatMerchantId(originalCtx.merchantId42));

        // DE 43: Merchant Name Location (from 0200)
        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, originalCtx.merchantNameLocation43);

        // DE 49: Currency Code (from 0200)
        m.setField(IsoField.CURRENCY_CODE_49, originalCtx.currency49 != null ? originalCtx.currency49 : "704");

        // DE 90: Original Data Element (42 bytes)
        // Format: MTI(4) + STAN(6) + DateTime(10) + AcquirerID(11) + ForwardingID(11)
        String de90 = buildDE90(
                "0200", // Original MTI
                originalCtx.stan11, // Original STAN (DE 11)
                originalCtx.transmissionDt7, // Original DateTime (DE 07)
                acquirerId // Original Acquirer ID (DE 32)
        );
        m.setField(90, de90);

        // EXCLUDE: DE 14, DE 22, DE 35, DE 52 (not in reversal spec)

        return m;
    }

    /**
     * Build DE 90 (Original Data Element) - 42 bytes.
     * 
     * Format:
     * - Position 1-4: Original MTI (n-4)
     * - Position 5-10: Original Trace Number (n-6)
     * - Position 11-20: Original Transmission DateTime (n-10, MMDDHHmmss)
     * - Position 21-31: Original Acquiring Institution ID (n-11)
     * - Position 32-42: Forwarding Institution ID (n-11, fill with '0')
     */
    private static String buildDE90(String orgMti, String orgStan, String orgDateTime, String orgAcquirerId) {
        StringBuilder de90 = new StringBuilder();

        // Sub-element 1: Original MTI (4 bytes)
        de90.append(padLeft(orgMti, 4, '0'));

        // Sub-element 2: Original STAN (6 bytes)
        de90.append(padLeft(orgStan, 6, '0'));

        // Sub-element 3: Original DateTime from DE 07 (10 bytes - MMDDHHmmss)
        // DE 07 format is MMDDHHmmss (10 chars)
        String dateTime = orgDateTime != null ? orgDateTime : "0000000000";
        if (dateTime.length() > 10) {
            dateTime = dateTime.substring(0, 10);
        }
        de90.append(padLeft(dateTime, 10, '0'));

        // Sub-element 4: Original Acquiring Institution ID (11 bytes)
        de90.append(padLeft(orgAcquirerId, 11, '0'));

        // Sub-element 5: Forwarding Institution ID (11 bytes, filled with '0')
        de90.append("00000000000");

        return de90.toString();
    }

    /**
     * Left-pad string to specified length with given character.
     */
    private static String padLeft(String s, int length, char padChar) {
        if (s == null)
            s = "";
        if (s.length() >= length) {
            return s.substring(s.length() - length);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < length; i++) {
            sb.append(padChar);
        }
        sb.append(s);
        return sb.toString();
    }

    // buildEmvString removed

    /**
     * Format DE41 (Terminal ID) to exactly 8 characters.
     * Right-padded with spaces.
     */
    private static String formatTerminalId(String tid) {
        if (tid == null)
            tid = "";
        if (tid.length() > 8) {
            return tid.substring(0, 8);
        }
        return String.format("%-8s", tid);
    }

    /**
     * Format DE42 (Merchant ID) to exactly 15 characters.
     * Right-padded with spaces.
     */
    private static String formatMerchantId(String mid) {
        if (mid == null)
            mid = "";
        if (mid.length() > 15) {
            return mid.substring(0, 15);
        }
        return String.format("%-15s", mid);
    }

    // REMOVED formatMerchantNameLocation logic.
    // We assume the caller (Context) provides the correct 40-char string.
}







