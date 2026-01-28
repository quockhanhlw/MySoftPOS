package com.example.mysoftpos.iso8583;

import com.example.mysoftpos.domain.model.CardInputData;
import java.util.Map;

/**
 * ISO8583 Builder - Production Grade.
 * Adheres to Napas Specifications.
 */
public class Iso8583Builder {

    private Iso8583Builder() {}

    /**
     * Build Purchase Request (0200).
     */
    public static IsoMessage buildPurchaseMsg(TransactionContext ctx, CardInputData card) {
        IsoMessage m = new IsoMessage("0200");
        
        // 1. Mandatory Fields
        m.setField(IsoField.PAN_2, card.getPan());
        m.setField(IsoField.PROCESSING_CODE_3, "000000"); // Purchase
        m.setField(IsoField.AMOUNT_4, ctx.amount4);
        m.setField(IsoField.TRANSMISSION_DATETIME_7, ctx.transmissionDt7);
        m.setField(IsoField.STAN_11, ctx.stan11);
        m.setField(IsoField.LOCAL_TIME_12, ctx.localTime12);
        m.setField(IsoField.LOCAL_DATE_13, ctx.localDate13);
        m.setField(15, ctx.settlementDate15); // DE 15
        m.setField(IsoField.MERCHANT_TYPE_18, ctx.mcc18 != null ? ctx.mcc18 : "5411");
        
        // DE 22: POS Entry Mode - Dynamic based on Card/Test Case
        String entryMode = card.getPosEntryMode();
        m.setField(IsoField.POS_ENTRY_MODE_22, entryMode);
        
        m.setField(IsoField.POS_CONDITION_CODE_25, "00");
        m.setField(IsoField.ACQUIRER_ID_32, ctx.acquirerId32 != null ? ctx.acquirerId32 : "970406");
        
        m.setField(IsoField.RRN_37, ctx.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, formatTerminalId(ctx.terminalId41));
        m.setField(IsoField.MERCHANT_ID_42, formatMerchantId(ctx.merchantId42));
        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, formatMerchantNameLocation(ctx.merchantNameLocation43));
        m.setField(IsoField.CURRENCY_CODE_49, ctx.currency49 != null ? ctx.currency49 : "704");

        // Logic "Like Mock Track 2" but Dynamic DE 22
        
        // DE 14: Expiration Date - Always Include if Available (Mock Pattern)
        if (card.getExpiryDate() != null) {
            m.setField(IsoField.EXPIRATION_DATE_14, card.getExpiryDate());
        }

        // DE 35: Track 2 - Always Include if Available (Mock Pattern, 'D' separator)
        if (card.getTrack2() != null && !card.getTrack2().isEmpty()) {
            m.setField(IsoField.TRACK2_35, card.getTrack2().replace('=', 'D'));
        }

        // DE 52: PIN Block - Conditional on Mode ending in '1'
        boolean hasPin = entryMode != null && entryMode.endsWith("1");
        if ((hasPin) && ctx.encryptPin && ctx.pinBlock52 != null) {
            m.setField(IsoField.PIN_BLOCK_52, ctx.pinBlock52);
        }

        // DE 55: ICC Data - Conditional on Chip (05) or NFC (07)
        boolean isChipOrNfc = entryMode != null && (entryMode.startsWith("05") || entryMode.startsWith("07"));
        if (isChipOrNfc) {
            String emv = buildEmvString(card.getEmvTags());
            if (emv != null && !emv.isEmpty()) {
                m.setField(IsoField.ICC_DATA_55, emv);
            }
        }

        // DE 60-63: Reserved/Private (if any)
        if (ctx.field60 != null) {
            m.setField(63, ctx.field60);
        }

        return m;
    }

    /**
     * Build Balance Inquiry Request (0200).
     */
    public static IsoMessage buildBalanceMsg(TransactionContext ctx, CardInputData card) {
        IsoMessage m = new IsoMessage("0200"); // Strictly 0200
        
        m.setField(IsoField.PAN_2, card.getPan());
        m.setField(IsoField.PROCESSING_CODE_3, "300000"); // Strict: 300000
        m.setField(IsoField.AMOUNT_4, "000000000000");    // Ignore Amount
        m.setField(IsoField.TRANSMISSION_DATETIME_7, ctx.transmissionDt7);
        m.setField(IsoField.STAN_11, ctx.stan11);
        m.setField(IsoField.LOCAL_TIME_12, ctx.localTime12);
        m.setField(IsoField.LOCAL_DATE_13, ctx.localDate13);
        m.setField(15, ctx.settlementDate15); // DE 15 - Added
        m.setField(IsoField.MERCHANT_TYPE_18, "6011"); // Financial Inst.
        
        // DE 22: POS Entry Mode - Dynamic
        String entryMode = card.getPosEntryMode();
        m.setField(IsoField.POS_ENTRY_MODE_22, entryMode);
        
        m.setField(IsoField.POS_CONDITION_CODE_25, "00");
        m.setField(IsoField.ACQUIRER_ID_32, ctx.acquirerId32 != null ? ctx.acquirerId32 : "970406");
        // DE 33 removed for Balance
        m.setField(IsoField.RRN_37, ctx.rrn37);
        m.setField(IsoField.TERMINAL_ID_41, ctx.terminalId41);
        m.setField(IsoField.MERCHANT_ID_42, ctx.merchantId42);
        m.setField(IsoField.MERCHANT_NAME_LOCATION_43, ctx.merchantNameLocation43);
        m.setField(IsoField.CURRENCY_CODE_49, "704");

        // DE 14: Expiration Date - Always Include if Available
        if (card.getExpiryDate() != null) {
            m.setField(IsoField.EXPIRATION_DATE_14, card.getExpiryDate());
        }

        // DE 35: Track 2 - Always Include if Available ('D')
        if (card.getTrack2() != null && !card.getTrack2().isEmpty()) {
            m.setField(IsoField.TRACK2_35, card.getTrack2().replace('=', 'D'));
        }

        // DE 52: PIN Block - Conditional
        boolean hasPin = entryMode != null && entryMode.endsWith("1");
        if ((hasPin) && ctx.encryptPin && ctx.pinBlock52 != null) {
            m.setField(IsoField.PIN_BLOCK_52, ctx.pinBlock52);
        }

        // DE 55: ICC Data - Conditional on Chip/NFC
        boolean isChipOrNfc = entryMode != null && (entryMode.startsWith("05") || entryMode.startsWith("07"));
        if (isChipOrNfc) {
            String emv = buildEmvString(card.getEmvTags());
            if (emv != null && !emv.isEmpty()) {
                m.setField(IsoField.ICC_DATA_55, emv);
            }
        }
        
        // DE 128: MAC
        m.setField(128, "0000000000000000");

        return m;
    }

    /**
     * Build Reversal Advice (0420).
     * @param originalCtx The context of the original FAILED transaction.
     * @param card Card Data.
     * @param newTrace The NEW Trace Number (DE 11).
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
        m.setField(IsoField.MERCHANT_TYPE_18, originalCtx.mcc18 != null ? originalCtx.mcc18 : (originalCtx.txnType == TxnType.BALANCE_INQUIRY ? "6011" : "5411"));
        
        // DE 22: POS Entry Mode - Dynamic
        String entryMode = card.getPosEntryMode();
        m.setField(IsoField.POS_ENTRY_MODE_22, entryMode);
        
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
        if (orgTrace.length() > 6) orgTrace = orgTrace.substring(orgTrace.length()-6);
        while(orgTrace.length() < 6) orgTrace = "0" + orgTrace;

        // FwdInst(11) + 0000...(11)
        String de90 = orgMti + orgTrace + orgDate + orgTime + "00970406000" + "00000000000";
        m.setField(90, de90);
        
        // DE 35: Track 2 - Included
        if (card.getTrack2() != null && !card.getTrack2().isEmpty()) {
            m.setField(IsoField.TRACK2_35, card.getTrack2().replace('=', 'D'));
        }
        
        // DE 55: ICC Data
        boolean isChipOrNfc = entryMode != null && (entryMode.startsWith("05") || entryMode.startsWith("07"));
        if (isChipOrNfc) {
            String emv = buildEmvString(card.getEmvTags());
            if (emv != null && !emv.isEmpty()) m.setField(IsoField.ICC_DATA_55, emv);
        }

        // DE 128: MAC
        m.setField(128, "0000000000000000");

        return m;
    }

    private static String buildEmvString(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) return null;
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
        if (tid == null) tid = "AUTO0001";
        if (tid.length() > 8) {
            return tid.substring(0, 8);
        }
        return String.format("%-8s", tid); // Left-aligned, space-padded
    }
    
    /**
     * Format DE42 (Merchant ID) to exactly 15 characters.
     * Right-padded with spaces.
     */
    private static String formatMerchantId(String mid) {
        if (mid == null) mid = "MYSOFTPOSSHOP01";
        if (mid.length() > 15) {
            return mid.substring(0, 15);
        }
        return String.format("%-15s", mid); // Left-aligned, space-padded
    }
    
    /**
     * Format DE43 (Merchant Name/Location) to exactly 40 characters:
     * Spec: [Bank Name 22] + [Space 1] + [Location 13] + [Space 1] + [Country 3]
     */
    private static String formatMerchantNameLocation(String input) {
        // Values from Requirement / Config
        String name = "MYSOFTPOS BANK";
        String city = "HA NOI";
        String country = "704"; // Vietnam Numeric
        
        // 1. Bank Name (22 chars)
        if (name.length() > 22) name = name.substring(0, 22);
        else name = String.format("%-22s", name);
        
        // 2. Space (1 char) -> automatically handled by concatenation or explicit?
        // Spec says: "23: Space", "37: Space".
        // So we need explicit spaces between fields if we fill them exactly.
        
        // 3. Location (13 chars)
        if (city.length() > 13) city = city.substring(0, 13);
        else city = String.format("%-13s", city);
        
        // 4. Country (3 chars)
        if (country.length() > 3) country = country.substring(0, 3);
        
        // Construct: Name(22) + " " + Location(13) + " " + Country(3)
        // Total: 22 + 1 + 13 + 1 + 3 = 40
        return name + " " + city + " " + country; 
    }
}
