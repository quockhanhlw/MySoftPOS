package com.example.mysoftpos.iso8583;

import com.example.mysoftpos.domain.model.CardInputData;
import android.util.Log;
import java.util.Map;

/**
 * Builds ISO8583 requests for supported transactions.
 * Strictly separates Purchase and Balance Inquiry logic.
 */
public final class IsoRequestBuilder {
    private static final String TAG = "IsoRequestBuilder";

    private IsoRequestBuilder() {}

    /**
     * Build Reversal Advice (MTI 0420)
     */
    public static IsoMessage buildReversalAdvice(TransactionContext ctx, CardInputData cardData) {
        IsoMessage m = new IsoMessage("0420");

        // Key Fields to Match Original Request
        m.setField(IsoField.PAN_2, cardData.getPan());
        m.setField(IsoField.PROCESSING_CODE_3, ctx.txnType == TxnType.BALANCE_INQUIRY ? "310000" : "000000");
        m.setField(IsoField.AMOUNT_4, normalizeAmount12(ctx.amount4));
        m.setField(IsoField.TRANSMISSION_DATETIME_7, ctx.transmissionDt7);
        m.setField(IsoField.STAN_11, normalizeStan6(ctx.stan11));
        m.setField(IsoField.LOCAL_TIME_12, ctx.localTime12);
        m.setField(IsoField.LOCAL_DATE_13, ctx.localDate13);
        
        m.setField(IsoField.MERCHANT_TYPE_18, ctx.mcc18!=null?ctx.mcc18:"5411"); 
        m.setField(IsoField.POS_ENTRY_MODE_22, cardData.getPosEntryMode());
        m.setField(IsoField.ACQUIRER_ID_32, ctx.acquirerId32 != null ? ctx.acquirerId32 : "970400");
        m.setField(IsoField.RRN_37, ctx.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, TransactionContext.formatTid8(ctx.terminalId41));
        m.setField(IsoField.MERCHANT_ID_42, TransactionContext.formatMid15(ctx.merchantId42));
        m.setField(IsoField.CURRENCY_CODE_49, "704");

        // DE 90: Original Data Elements
        // Format: MTI(4) + Stan(6) + Date(4) + Time(6) + AcqId(11, LLVAR)
        // Note: AcqId is usually fixed length in DE 90 structure or LLVAR depending on spec.
        // Assuming Standard: MTI(4) + Stan(6) + TransmissionDateTime(10) + AcquirerID(11, ZeroPadded) + ForwardingInst(11, ZeroPadded)
        // Let's use simple matching logic for now: MTI + STAN + TRACE + ACQ
        
        String originalMti = (ctx.txnType == TxnType.BALANCE_INQUIRY) ? "0100" : "0200";
        String originalStan = normalizeStan6(ctx.stan11);
        String originalTimeDate = ctx.transmissionDt7; // MMddHHmmss
        String originalAcq = "00970400000"; // 11 chars
        String originalFwd = "00000000000"; // 11 chars
        
        String de90 = originalMti + originalStan + originalTimeDate + originalAcq + originalFwd;
        m.setField(90, de90);

        return m;
    }

    /**
     * Build Purchase Request (MTI 0200).
     * MTI: 0200
     * Processing Code: 000000
     * Amount: Transaction Amount
     */
    public static IsoMessage buildPurchase(TransactionContext c, CardInputData cardData) {
        IsoMessage m = new IsoMessage("0200");

        // --- Mandatory Fields ---
        m.setField(IsoField.PAN_2, cardData.getPan());
        m.setField(IsoField.PROCESSING_CODE_3, "000000");
        m.setField(IsoField.AMOUNT_4, normalizeAmount12(c.amount4));
        m.setField(IsoField.TRANSMISSION_DATETIME_7, c.transmissionDt7);
        m.setField(IsoField.STAN_11, normalizeStan6(c.stan11));
        m.setField(IsoField.LOCAL_TIME_12, c.localTime12);
        
        // Safety: Ensure F13 is present
        String f13 = c.localDate13 != null ? c.localDate13 : TransactionContext.buildLocalDate13Now();
        m.setField(IsoField.LOCAL_DATE_13, f13);
        
        // Expiry (DE 14) - Required for Purchase
        m.setField(IsoField.EXPIRATION_DATE_14, cardData.getExpiryDate());

        m.setField(IsoField.MERCHANT_TYPE_18, c.mcc18 != null ? c.mcc18 : "5411");
        
        // DE 22: Pos Entry Mode matches Card Input (071 NFC vs 011 Manual)
        m.setField(IsoField.POS_ENTRY_MODE_22, cardData.getPosEntryMode());
        
        m.setField(IsoField.POS_CONDITION_CODE_25, "00");
        m.setField(IsoField.ACQUIRER_ID_32, c.acquirerId32 != null ? c.acquirerId32 : "970400");
        m.setField(IsoField.RRN_37, c.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, TransactionContext.formatTid8(c.terminalId41));
        m.setField(IsoField.MERCHANT_ID_42, TransactionContext.formatMid15(c.merchantId42));
        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, c.merchantNameLocation43);
        m.setField(IsoField.CURRENCY_CODE_49, TransactionContext.defaultCurrencyVND());

        // --- Conditional Fields ---
        if (c.country19 != null) m.setField(IsoField.COUNTRY_CODE_19, c.country19);
        if (c.cardSeq23 != null) m.setField(IsoField.CARD_SEQ_23, c.cardSeq23);

        // DE 35 (Track 2) Logic
        if (cardData.isContactless()) {
            m.setField(IsoField.TRACK2_35, cardData.getTrack2());
            m.setField(IsoField.EXPIRATION_DATE_14, null); // Skip Expiry if Track 2 present (Host Preference)
            
            String emvData = buildEmvTlv(cardData.getEmvTags());
            if (emvData != null && !emvData.isEmpty()) {
                m.setField(IsoField.ICC_DATA_55, emvData);
            }
        } else {
            // Manual Entry: Skip DE 35 and DE 55
            // Ensure DE 14 is set (already set above)
        }

        if (c.encryptPin) {
            m.setField(IsoField.PIN_BLOCK_52, c.pinBlock52);
        }
        
        if (c.field60 != null) {
            m.setField(IsoField.RESERVED_PRIVATE_60, c.field60);
        }

        logSecurely(m);
        return m;
    }

    /**
     * Build Balance Inquiry Request (MTI 0100).
     * MTI: 0100
     * Processing Code: 310000
     * Amount: 000000000000
     */
    public static IsoMessage buildBalanceInquiry(TransactionContext c, CardInputData cardData) {
        IsoMessage m = new IsoMessage("0100");

        // Mandatory Fields
        m.setField(IsoField.PAN_2, cardData.getPan());
        m.setField(IsoField.PROCESSING_CODE_3, "310000");
        m.setField(IsoField.AMOUNT_4, "000000000000"); // Fixed Zero
        m.setField(IsoField.TRANSMISSION_DATETIME_7, c.transmissionDt7);
        m.setField(IsoField.STAN_11, normalizeStan6(c.stan11));
        m.setField(IsoField.LOCAL_TIME_12, c.localTime12);
        
        String f13 = c.localDate13 != null ? c.localDate13 : TransactionContext.buildLocalDate13Now();
        m.setField(IsoField.LOCAL_DATE_13, f13);

        m.setField(IsoField.MERCHANT_TYPE_18, "6011"); 
        m.setField(IsoField.POS_ENTRY_MODE_22, cardData.getPosEntryMode());
        m.setField(IsoField.POS_CONDITION_CODE_25, "00");
        m.setField(IsoField.ACQUIRER_ID_32, c.acquirerId32 != null ? c.acquirerId32 : "970400");
        m.setField(IsoField.RRN_37, c.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, TransactionContext.formatTid8(c.terminalId41));
        m.setField(IsoField.MERCHANT_ID_42, TransactionContext.formatMid15(c.merchantId42));
        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, c.merchantNameLocation43);
        m.setField(IsoField.CURRENCY_CODE_49, "704");

        // Conditional
        if (c.country19 != null) m.setField(IsoField.COUNTRY_CODE_19, c.country19);
        if (c.cardSeq23 != null) m.setField(IsoField.CARD_SEQ_23, c.cardSeq23);

        // DE 35: Track 2 - Only if NFC
        if (cardData.isContactless()) {
            m.setField(IsoField.TRACK2_35, cardData.getTrack2());
            
            String emvData = buildEmvTlv(cardData.getEmvTags());
            if (emvData != null && !emvData.isEmpty()) {
                m.setField(IsoField.ICC_DATA_55, emvData);
            }
        }
        // Manual: Skip DE 35/55/14 for Balance (Minimal)

        if (c.encryptPin) {
             m.setField(IsoField.PIN_BLOCK_52, c.pinBlock52);
        }
        
        if (c.field60 != null) {
            m.setField(IsoField.RESERVED_PRIVATE_60, c.field60);
        }

        logSecurely(m);
        return m;
    }

    // --- Helpers ---

    private static void logSecurely(IsoMessage m) {
        StringBuilder sb = new StringBuilder("ISO MSG Constructed:\n");
        sb.append("MTI: ").append(m.getMti()).append("\n");
        appendMasked(sb, "DE 2", m.getField(IsoField.PAN_2));
        appendMasked(sb, "DE 35", m.getField(IsoField.TRACK2_35));
        sb.append("DE 3: ").append(m.getField(IsoField.PROCESSING_CODE_3)).append("\n");
        sb.append("DE 4: ").append(m.getField(IsoField.AMOUNT_4)).append("\n");
        sb.append("DE 11: ").append(m.getField(IsoField.STAN_11)).append("\n");
        Log.d(TAG, sb.toString());
    }

    private static void appendMasked(StringBuilder sb, String label, String value) {
        if (value == null) return;
        String masked = value.length() > 6 
                ? value.substring(0, 4) + "****" + value.substring(value.length() - 4) 
                : "****";
        sb.append(label).append(": ").append(masked).append("\n");
    }

    private static String buildEmvTlv(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String tag = entry.getKey();
            String value = entry.getValue();
            if (value == null) continue;
            sb.append(tag);
            int len = value.length() / 2;
            sb.append(String.format("%02X", len));
            sb.append(value);
        }
        return sb.toString();
    }

    private static String normalizeAmount12(String f4) {
        if (f4 == null) return null;
        String v = f4.trim();
        if (v.matches("\\d{12}")) return v;
        if (v.matches("\\d{1,12}")) return TransactionContext.formatAmount12(v);
        return v;
    }

    private static String normalizeStan6(String f11) {
        if (f11 == null) return null;
        String v = f11.trim();
        if (v.matches("\\d{6}")) return v;
        if (v.matches("\\d{1,6}")) return TransactionContext.formatStan6(v);
        return v;
    }
}
