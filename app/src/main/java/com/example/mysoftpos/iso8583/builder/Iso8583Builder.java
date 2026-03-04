package com.example.mysoftpos.iso8583.builder;

import com.example.mysoftpos.iso8583.message.IsoMessage;
import com.example.mysoftpos.iso8583.spec.IsoField;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.iso8583.emv.EmvTlvCodec;
import com.example.mysoftpos.domain.model.CardInputData;

import java.util.Map;

/**
 * ISO 8583 Message Builder — NAPAS Production Grade.
 *
 * Supports three card entry modes:
 *
 * 1. Manual Entry (DE 22 = 011/012):
 * → DE 14 (Expiry), no DE 35, no DE 55
 *
 * 2. Magstripe (DE 22 = 021/022):
 * → DE 35 (Track 2), DE 14 (Expiry), no DE 55
 *
 * 3. NFC Contactless CHIP (DE 22 = 071/072):
 * → DE 55 (ICC Data — mandatory, EMV TLV tags including tag 57 for Track 2)
 * → DE 23 (Card Sequence Number from tag 5F34)
 * → NO DE 35 (Track 2 is inside tag 57 in DE 55, not in DE 35)
 * → NO DE 14 (expiry is inside tag 57)
 * → 071 = Online PIN, 072 = No CVM
 */
public class Iso8583Builder {

    private Iso8583Builder() {
    }

    // =====================================================================
    // Purchase (0200)
    // =====================================================================

    public static IsoMessage buildPurchaseMsg(TransactionContext ctx, CardInputData card) {
        IsoMessage m = new IsoMessage("0200");

        // Mandatory fields (all entry modes)
        m.setField(IsoField.PAN_2, card.getPan());
        m.setField(IsoField.PROCESSING_CODE_3, ctx.processingCode3);
        m.setField(IsoField.AMOUNT_4, ctx.amount4);
        m.setField(IsoField.TRANSMISSION_DATETIME_7, ctx.transmissionDt7);
        m.setField(IsoField.STAN_11, ctx.stan11);
        m.setField(IsoField.LOCAL_TIME_12, ctx.localTime12);
        m.setField(IsoField.LOCAL_DATE_13, ctx.localDate13);
        m.setField(IsoField.MERCHANT_TYPE_18, ctx.mcc18);
        m.setField(IsoField.COUNTRY_CODE_19, ctx.country19);

        // DE 22: POS Entry Mode
        String de22 = card.getPosEntryMode();
        m.setField(IsoField.POS_ENTRY_MODE_22, de22);

        // Conditional fields based on entry mode
        if (card.isNfcChip()) {
            // ====== NFC Contactless CHIP (071/072) ======
            applyNfcChipFields(m, ctx, card, "00"); // 00 = Purchase
        } else if ("011".equals(de22) || "012".equals(de22)) {
            // ====== Manual Entry ======
            if (card.getExpiryDate() != null) {
                m.setField(IsoField.EXPIRATION_DATE_14, card.getExpiryDate());
            }
        } else if ("021".equals(de22) || "022".equals(de22)) {
            // ====== Magstripe ======
            if (card.getTrack2() != null && !card.getTrack2().isEmpty()) {
                m.setField(IsoField.TRACK2_35, card.getTrack2().replace('=', 'D'));
            }
            if (card.getExpiryDate() != null) {
                m.setField(IsoField.EXPIRATION_DATE_14, card.getExpiryDate());
            }
        }

        // Remaining mandatory fields
        m.setField(IsoField.POS_CONDITION_CODE_25, ctx.posCondition25);
        m.setField(IsoField.ACQUIRER_ID_32, ctx.acquirerId32);
        m.setField(IsoField.RRN_37, ctx.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, formatTerminalId(ctx.terminalId41));
        m.setField(IsoField.MERCHANT_ID_42, formatMerchantId(ctx.merchantId42));
        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, ctx.merchantNameLocation43);
        m.setField(IsoField.CURRENCY_CODE_49, ctx.currency49);

        // PIN Block
        if (ctx.encryptPin && ctx.pinBlock52 != null) {
            m.setField(IsoField.PIN_BLOCK_52, ctx.pinBlock52);
        }

        // DE 60
        if (ctx.field60 != null) {
            m.setField(60, ctx.field60);
        }

        return m;
    }

    // =====================================================================
    // Balance Inquiry (0200)
    // =====================================================================

    public static IsoMessage buildBalanceMsg(TransactionContext ctx, CardInputData card) {
        IsoMessage m = new IsoMessage("0200");

        m.setField(IsoField.PAN_2, card.getPan());
        m.setField(IsoField.PROCESSING_CODE_3, ctx.processingCode3); // 300000
        m.setField(IsoField.AMOUNT_4, "000000000000");
        m.setField(IsoField.TRANSMISSION_DATETIME_7, ctx.transmissionDt7);
        m.setField(IsoField.STAN_11, ctx.stan11);
        m.setField(IsoField.LOCAL_TIME_12, ctx.localTime12);
        m.setField(IsoField.LOCAL_DATE_13, ctx.localDate13);

        String de22 = card.getPosEntryMode();
        m.setField(IsoField.POS_ENTRY_MODE_22, de22);

        if (card.isNfcChip()) {
            applyNfcChipFields(m, ctx, card, "30"); // 30 = Balance Inquiry
        } else if ("011".equals(de22) || "012".equals(de22)) {
            if (card.getExpiryDate() != null) {
                m.setField(IsoField.EXPIRATION_DATE_14, card.getExpiryDate());
            }
        } else if ("021".equals(de22) || "022".equals(de22)
                || "901".equals(de22) || "902".equals(de22)) {
            if (card.getTrack2() != null && !card.getTrack2().isEmpty()) {
                m.setField(IsoField.TRACK2_35, card.getTrack2().replace('=', 'D'));
            }
            if (card.getExpiryDate() != null) {
                m.setField(IsoField.EXPIRATION_DATE_14, card.getExpiryDate());
            }
        }

        m.setField(IsoField.MERCHANT_TYPE_18, ctx.mcc18);
        m.setField(IsoField.COUNTRY_CODE_19, ctx.country19);
        m.setField(IsoField.POS_CONDITION_CODE_25, ctx.posCondition25);
        m.setField(IsoField.ACQUIRER_ID_32, ctx.acquirerId32);
        m.setField(IsoField.RRN_37, ctx.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, formatTerminalId(ctx.terminalId41));
        m.setField(IsoField.MERCHANT_ID_42, formatMerchantId(ctx.merchantId42));
        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, ctx.merchantNameLocation43);
        m.setField(IsoField.CURRENCY_CODE_49, ctx.currency49);

        if (ctx.encryptPin && ctx.pinBlock52 != null) {
            m.setField(IsoField.PIN_BLOCK_52, ctx.pinBlock52);
        }

        return m;
    }

    // =====================================================================
    // NFC CHIP Field Population (DE 23, DE 55 — no DE 35)
    // =====================================================================

    /**
     * Apply NFC Contactless CHIP-specific fields.
     *
     * NAPAS Domestic CHIP rules:
     * DE 23 — Card Sequence Number (mandatory for CHIP, from tag 5F34, 3 digits).
     * DE 55 — ICC Data (mandatory, TLV-encoded EMV tags including tag 57 for Track
     * 2).
     * DE 35 — ABSENT. Track 2 is inside tag 57 within DE 55, NOT in DE 35.
     * DE 14 — ABSENT. Expiry is inside tag 57 within DE 55.
     *
     * @param m       ISO message
     * @param ctx     Transaction context
     * @param card    Card data with emvTags populated
     * @param txnType EMV transaction type: "00"=Purchase, "30"=Balance, "01"=Cash
     */
    private static void applyNfcChipFields(IsoMessage m, TransactionContext ctx,
            CardInputData card, String txnType) {

        Map<Integer, byte[]> cardEmvTags = card.getEmvTags();

        // --- DE 23: Card Sequence Number ---
        String csn = card.getCardSequenceNumber();
        m.setField(IsoField.CARD_SEQ_23, csn);

        // --- DE 35: ABSENT for NFC CHIP ---
        // Track 2 Equivalent Data is ONLY inside tag 57 within DE 55.
        // DO NOT set DE 35 for contactless chip transactions.

        // --- DE 55: ICC System Related Data (contains tag 57) ---
        if (cardEmvTags != null && !cardEmvTags.isEmpty()) {
            String currencyCode = ctx.currency49 != null ? ctx.currency49 : "704";
            String countryCode = ctx.country19 != null ? ctx.country19 : "704";
            String amountForEmv = ctx.amount4 != null ? ctx.amount4 : "000000000000";

            boolean pinVerified = "071".equals(card.getPosEntryMode());

            // Build complete tag map (terminal-generated + card-read)
            Map<Integer, byte[]> fullTags = EmvTlvCodec.buildNfcPurchaseTags(
                    cardEmvTags,
                    amountForEmv,
                    currencyCode,
                    countryCode,
                    txnType,
                    pinVerified);

            // Build DE 55 hex string
            String de55Hex;
            try {
                de55Hex = EmvTlvCodec.buildDE55Request(fullTags);
            } catch (IllegalArgumentException e) {
                // Validation failed — build without strict validation
                android.util.Log.w("Iso8583Builder",
                        "DE 55 validation warning: " + e.getMessage());
                de55Hex = EmvTlvCodec.buildDE55(fullTags);
            }

            if (!de55Hex.isEmpty()) {
                m.setField(IsoField.ICC_DATA_55, de55Hex);
            }
        } else if (ctx.iccData55 != null && !ctx.iccData55.isEmpty()) {
            // Fallback: pre-built ICC data from context
            m.setField(IsoField.ICC_DATA_55, ctx.iccData55);
        }

        // DE 14: Expiry Date (Add for NFC CHIP as well)
        if (card.getExpiryDate() != null && !card.getExpiryDate().isEmpty()) {
            m.setField(IsoField.EXPIRATION_DATE_14, card.getExpiryDate());
        }
    }

    // =====================================================================
    // Reversal Advice (0420)
    // =====================================================================

    public static IsoMessage buildReversalAdvice(TransactionContext originalCtx,
            CardInputData card, String newTrace) {
        IsoMessage m = new IsoMessage("0420");

        m.setField(IsoField.PAN_2, card.getPan());

        String proc = (originalCtx.txnType == TxnType.BALANCE_INQUIRY) ? "300000" : "000000";
        m.setField(IsoField.PROCESSING_CODE_3, proc);
        m.setField(IsoField.AMOUNT_4, originalCtx.amount4);
        m.setField(IsoField.TRANSMISSION_DATETIME_7, originalCtx.transmissionDt7);
        m.setField(IsoField.STAN_11, newTrace);
        m.setField(IsoField.LOCAL_TIME_12, originalCtx.localTime12);
        m.setField(IsoField.LOCAL_DATE_13, originalCtx.localDate13);

        // DE 14: Expiry (if available from original)
        if (originalCtx.expiry14 != null && !originalCtx.expiry14.isEmpty()) {
            m.setField(IsoField.EXPIRATION_DATE_14, originalCtx.expiry14);
        }

        m.setField(IsoField.MERCHANT_TYPE_18, originalCtx.mcc18 != null ? originalCtx.mcc18
                : (originalCtx.txnType == TxnType.BALANCE_INQUIRY ? "6011" : "5411"));

        // DE 19: Country Code (required by NAPAS switch)
        if (originalCtx.country19 != null && !originalCtx.country19.isEmpty()) {
            m.setField(IsoField.COUNTRY_CODE_19, originalCtx.country19);
        }

        // DE 22: POS Entry Mode (required by NAPAS switch)
        if (originalCtx.posEntryMode22 != null && !originalCtx.posEntryMode22.isEmpty()) {
            m.setField(IsoField.POS_ENTRY_MODE_22, originalCtx.posEntryMode22);
        }

        // DE 25: POS Condition Code (required by NAPAS switch)
        if (originalCtx.posCondition25 != null && !originalCtx.posCondition25.isEmpty()) {
            m.setField(IsoField.POS_CONDITION_CODE_25, originalCtx.posCondition25);
        }

        String acquirerId = originalCtx.acquirerId32 != null ? originalCtx.acquirerId32 : "970488";
        m.setField(IsoField.ACQUIRER_ID_32, acquirerId);

        // DE 35: Track 2 (if original had it — magstripe/manual, NOT NFC chip)
        if (originalCtx.track2_35 != null && !originalCtx.track2_35.isEmpty()) {
            m.setField(IsoField.TRACK2_35, originalCtx.track2_35);
        }

        m.setField(IsoField.RRN_37, originalCtx.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, formatTerminalId(originalCtx.terminalId41));
        m.setField(IsoField.MERCHANT_ID_42, formatMerchantId(originalCtx.merchantId42));
        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, originalCtx.merchantNameLocation43);
        m.setField(IsoField.CURRENCY_CODE_49,
                originalCtx.currency49 != null ? originalCtx.currency49 : "704");

        // DE 90: Original Data Element
        String de90 = buildDE90("0200", originalCtx.stan11,
                originalCtx.transmissionDt7, acquirerId);
        m.setField(90, de90);

        // DE 55 for CHIP reversal (DF31 Issuer Script Result, 95, 9F10, 9F36)
        if (originalCtx.reversalIccData55 != null && !originalCtx.reversalIccData55.isEmpty()) {
            m.setField(IsoField.ICC_DATA_55, originalCtx.reversalIccData55);
        }

        return m;
    }

    // =====================================================================
    // DE 90 Builder
    // =====================================================================

    private static String buildDE90(String orgMti, String orgStan,
            String orgDateTime, String orgAcquirerId) {
        StringBuilder de90 = new StringBuilder();
        de90.append(padLeft(orgMti, 4, '0'));
        de90.append(padLeft(orgStan, 6, '0'));

        String dt = orgDateTime != null ? orgDateTime : "0000000000";
        if (dt.length() > 10)
            dt = dt.substring(0, 10);
        de90.append(padLeft(dt, 10, '0'));

        de90.append(padLeft(orgAcquirerId, 11, '0'));
        de90.append("00000000000");
        return de90.toString();
    }

    // =====================================================================
    // Formatting Helpers
    // =====================================================================

    private static String padLeft(String s, int length, char padChar) {
        if (s == null)
            s = "";
        if (s.length() >= length)
            return s.substring(s.length() - length);
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < length; i++)
            sb.append(padChar);
        sb.append(s);
        return sb.toString();
    }

    private static String formatTerminalId(String tid) {
        if (tid == null)
            tid = "";
        if (tid.length() > 8)
            return tid.substring(0, 8);
        return String.format("%-8s", tid);
    }

    private static String formatMerchantId(String mid) {
        if (mid == null)
            mid = "";
        if (mid.length() > 15)
            return mid.substring(0, 15);
        return String.format("%-15s", mid);
    }
}
