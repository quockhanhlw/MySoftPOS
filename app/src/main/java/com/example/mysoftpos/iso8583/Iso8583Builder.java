package com.example.mysoftpos.iso8583;

import com.example.mysoftpos.domain.model.CardInputData;
import java.util.Map;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.iso8583.IsoField;

/**
 * ISO8583 Builder - Production Grade.
 * Adheres to Napas Specifications.
 */
public class Iso8583Builder {

    private Iso8583Builder() {
    }

    /**
     * Build Purchase Request (0200).
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
        m.setField(IsoField.EXPIRATION_DATE_14, card.getExpiryDate());
        m.setField(IsoField.MERCHANT_TYPE_18, ctx.mcc18);

        // DE 22: POS Entry Mode
        String de22 = card.getPosEntryMode();
        m.setField(IsoField.POS_ENTRY_MODE_22, de22);

        m.setField(IsoField.POS_CONDITION_CODE_25, ctx.posCondition25);
        m.setField(IsoField.ACQUIRER_ID_32, ctx.acquirerId32);

        m.setField(IsoField.RRN_37, ctx.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, formatTerminalId(ctx.terminalId41));
        m.setField(IsoField.MERCHANT_ID_42, formatMerchantId(ctx.merchantId42));

        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, ctx.merchantNameLocation43);
        m.setField(IsoField.CURRENCY_CODE_49, ctx.currency49);

        // Logic Rule: If Track 2 is present in Input, use it.
        if (card.getTrack2() != null && !card.getTrack2().isEmpty()) {
            m.setField(IsoField.TRACK2_35, card.getTrack2().replace('=', 'D'));

            if (ctx.encryptPin && ctx.pinBlock52 != null) {
                m.setField(IsoField.PIN_BLOCK_52, ctx.pinBlock52);
            }
        } else {
            if (ctx.encryptPin && ctx.pinBlock52 != null) {
                m.setField(IsoField.PIN_BLOCK_52, ctx.pinBlock52);
            }
        }

        // Fix: Add DE 55 for Chip/Contactless Purchase
        if (card.isContactless()) {
            if (card.getRawIccData() != null) {
                m.setField(IsoField.ICC_DATA_55, card.getRawIccData());
            } else {
                String emv = buildEmvString(card.getEmvTags());
                if (emv != null) {
                    m.setField(IsoField.ICC_DATA_55, emv);
                } else {
                    // Fallback: Generate Mock for Simulator
                    m.setField(IsoField.ICC_DATA_55,
                            MockEmvFactory.generateMockDe55(ctx.localDate13, ctx.currency49));
                }
            }
        }

        // Fix: Correct mapping for DE 60
        if (ctx.field60 != null) {
            m.setField(60, ctx.field60);
        }

        return m;
    }

    /**
     * Build Balance Inquiry Request (0200).
     */
    public static IsoMessage buildBalanceMsg(TransactionContext ctx, CardInputData card) {
        IsoMessage m = new IsoMessage("0200"); // Strictly 0200

        m.setField(IsoField.PAN_2, card.getPan());
        m.setField(IsoField.PROCESSING_CODE_3, ctx.processingCode3); // Balance Code
        m.setField(IsoField.AMOUNT_4, "000000000000");
        m.setField(IsoField.TRANSMISSION_DATETIME_7, ctx.transmissionDt7);
        m.setField(IsoField.STAN_11, ctx.stan11);
        m.setField(IsoField.LOCAL_TIME_12, ctx.localTime12);
        m.setField(IsoField.LOCAL_DATE_13, ctx.localDate13);
        m.setField(IsoField.MERCHANT_TYPE_18, ctx.mcc18); // Balance MCC

        m.setField(IsoField.POS_ENTRY_MODE_22, card.getPosEntryMode());

        m.setField(IsoField.POS_CONDITION_CODE_25, ctx.posCondition25);
        m.setField(IsoField.ACQUIRER_ID_32, ctx.acquirerId32);
        // DE 33 removed for Balance
        m.setField(IsoField.RRN_37, ctx.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, ctx.terminalId41);
        m.setField(IsoField.MERCHANT_ID_42, ctx.merchantId42);
        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, ctx.merchantNameLocation43);
        m.setField(IsoField.CURRENCY_CODE_49, ctx.currency49);

        if (card.isContactless()) {
            if (card.getTrack2() != null) {
                m.setField(IsoField.TRACK2_35, card.getTrack2().replace('D', '='));
            }
            String emv = buildEmvString(card.getEmvTags());
            if (emv != null)
                m.setField(IsoField.ICC_DATA_55, emv);
        }

        if (ctx.encryptPin && ctx.pinBlock52 != null) {
            m.setField(IsoField.PIN_BLOCK_52, ctx.pinBlock52);
        }

        return m;
    }

    /**
     * Build Reversal Advice (0420).
     *
     * @param originalCtx The context of the original FAILED transaction.
     * @param card        Card Data.
     * @param newTrace    The NEW Trace Number (DE 11).
     */
    public static IsoMessage buildReversalAdvice(TransactionContext originalCtx, CardInputData card, String newTrace) {
        IsoMessage m = new IsoMessage("0420");

        // Copy fields
        m.setField(IsoField.PAN_2, card.getPan());

        String proc = (originalCtx.txnType == TxnType.BALANCE_INQUIRY) ? "300000" : "000000";
        m.setField(IsoField.PROCESSING_CODE_3, proc);

        m.setField(IsoField.AMOUNT_4, originalCtx.amount4);
        m.setField(IsoField.TRANSMISSION_DATETIME_7, originalCtx.transmissionDt7);

        // DE 11: MUST be New Trace
        m.setField(IsoField.STAN_11, newTrace);

        m.setField(IsoField.LOCAL_TIME_12, originalCtx.localTime12);
        m.setField(IsoField.LOCAL_DATE_13, originalCtx.localDate13);
        m.setField(IsoField.MERCHANT_TYPE_18, originalCtx.mcc18 != null ? originalCtx.mcc18
                : (originalCtx.txnType == TxnType.BALANCE_INQUIRY ? "6011" : "5411"));

        m.setField(IsoField.POS_ENTRY_MODE_22, card.isContactless() ? "071" : "011");
        m.setField(IsoField.ACQUIRER_ID_32, originalCtx.acquirerId32 != null ? originalCtx.acquirerId32 : "970406");
        m.setField(IsoField.RRN_37, originalCtx.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, originalCtx.terminalId41);
        m.setField(IsoField.MERCHANT_ID_42, originalCtx.merchantId42);
        m.setField(IsoField.CURRENCY_CODE_49, "704");

        // DE 90: Original Data
        // Format: MTI(4) + STAN(6) + Date(4) + Time(6) + ...
        String orgMti = "0200";
        String orgTrace = originalCtx.stan11;
        String orgDate = originalCtx.localDate13 != null ? originalCtx.localDate13 : "0000";
        String orgTime = originalCtx.localTime12 != null ? originalCtx.localTime12 : "000000";

        // Safety: ensure lengths
        if (orgTrace.length() > 6)
            orgTrace = orgTrace.substring(orgTrace.length() - 6);
        while (orgTrace.length() < 6)
            orgTrace = "0" + orgTrace;

        // FwdInst(11) + 0000...(11)
        String de90 = orgMti + orgTrace + orgDate + orgTime + "00970406000" + "00000000000";
        m.setField(90, de90);

        // EXCLUDE DE 52

        // Include DE 55 if NFC
        if (card.isContactless()) {
            String emv = buildEmvString(card.getEmvTags());
            if (emv != null)
                m.setField(IsoField.ICC_DATA_55, emv);
        }

        return m;
    }

    private static String buildEmvString(Map<String, String> tags) {
        if (tags == null || tags.isEmpty())
            return null;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : tags.entrySet()) {
            sb.append(e.getKey());
            // Length in hex? Simple byte length calculation
            int len = e.getValue().length() / 2;
            sb.append(String.format("%02X", len));
            sb.append(e.getValue());
        }
        return sb.toString();
    }

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
