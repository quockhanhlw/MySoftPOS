package com.example.mysoftpos.iso8583;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Input data needed to build ISO8583 requests.
 * Keep this as UI-agnostic as possible.
 */
public final class TransactionContext {
    public final TxnType txnType;

    // Terminal/merchant settings
    public final String terminalId41;
    public final String merchantId42;
    public final String merchantNameLocation43;

    // Card data
    public final String pan2;
    public final String track2_35; // optional
    public final String expiry14;  // optional
    public final String cardSeq23; // optional

    // POS data
    public final String processingCode3;
    public final String amount4; // 12n
    public final String transmissionDt7; // MMDDhhmmss
    public final String stan11; // 6n
    public final String localTime12; // hhmmss
    public final String localDate13; // MMDD
    public final String mcc18;
    public final String country19; // optional conditional
    public final String posEntryMode22;
    public final String posCondition25;
    public final String acquirerId32;
    public final String rrn37;
    public final String currency49;

    // Security/ICC
    public final boolean encryptPin;
    public final String pinBlock52; // optional conditional
    public final String iccData55; // optional conditional

    public final String field60; // optional conditional

    // MAC
    public final String mac128; // optional/not mandatory

    private TransactionContext(Builder b) {
        this.txnType = b.txnType;

        this.terminalId41 = b.terminalId41;
        this.merchantId42 = b.merchantId42;
        this.merchantNameLocation43 = b.merchantNameLocation43;

        this.pan2 = b.pan2;
        this.track2_35 = b.track2_35;
        this.expiry14 = b.expiry14;
        this.cardSeq23 = b.cardSeq23;

        this.processingCode3 = b.processingCode3;
        this.amount4 = b.amount4;
        this.transmissionDt7 = b.transmissionDt7;
        this.stan11 = b.stan11;
        this.localTime12 = b.localTime12;
        this.localDate13 = b.localDate13;
        this.mcc18 = b.mcc18;
        this.country19 = b.country19;
        this.posEntryMode22 = b.posEntryMode22;
        this.posCondition25 = b.posCondition25;
        this.acquirerId32 = b.acquirerId32;
        this.rrn37 = b.rrn37;
        this.currency49 = b.currency49;

        this.encryptPin = b.encryptPin;
        this.pinBlock52 = b.pinBlock52;
        this.iccData55 = b.iccData55;

        this.field60 = b.field60;
        this.mac128 = b.mac128;
    }

    // ---------- NAPAS helper formatting (padding/validation utilities) ----------

    /**
     * NAPAS: Amount (F4) is 12n, left pad with '0'.
     * Input digits may include separators (.,,). Any non-digits are removed.
     *
     * Examples:
     * - "50.000" -> "000000050000"
     * - "100000000" -> "000100000000" (100 million)
     */
    public static String formatAmount12(String amountDigits) {
        if (amountDigits == null) return null;
        String d = amountDigits.replaceAll("\\D+", "");
        if (d.isEmpty()) return null;

        // Strict max: 12 digits (n12)
        if (d.length() > 12) {
            throw new IllegalArgumentException("F4 must be n12 (<=12 digits). Got " + d.length() + ": " + d);
        }

        String padded = String.format(Locale.US, "%012d", Long.parseLong(d));
        if (!padded.matches("\\d{12}")) {
            throw new IllegalStateException("F4 formatted must be exactly 12 digits, got: " + padded);
        }
        return padded;
    }

    /** NAPAS: STAN (F11) is 6n, left pad with '0'. */
    public static String formatStan6(String stanDigits) {
        if (stanDigits == null) return null;
        String d = stanDigits.replaceAll("\\D+", "");
        if (d.isEmpty()) return null;
        if (d.length() > 6) {
            throw new IllegalArgumentException("F11 too long (>6): " + d);
        }
        return String.format(Locale.US, "%06d", Integer.parseInt(d));
    }

    /** NAPAS: TID (F41) fixed 8, right pad by spaces. */
    public static String formatTid8(String tid) {
        return padRightSpace(tid, 8);
    }

    /** NAPAS: MID (F42) fixed 15, right pad by spaces. */
    public static String formatMid15(String mid) {
        return padRightSpace(mid, 15);
    }

    public static String defaultCurrencyVND() {
        return "704";
    }

    public static String buildTransmissionDateTime7Now() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMddHHmmss", Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return sdf.format(new Date());
    }

    public static String buildLocalTime12Now() {
        SimpleDateFormat sdf = new SimpleDateFormat("HHmmss", Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return sdf.format(new Date());
    }

    public static String buildLocalDate13Now() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMdd", Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return sdf.format(new Date());
    }

    private static String padRightSpace(String v, int len) {
        if (v == null) v = "";
        if (v.length() > len) {
            return v.substring(0, len);
        }
        StringBuilder sb = new StringBuilder(v);
        while (sb.length() < len) sb.append(' ');
        return sb.toString();
    }

    /**
     * Calculate RRN (DE 37) based on formula:
     * RRN = Last Digit of Year + Julian Date + Server_ID + STAN (F11)
     * Length: 1 + 3 + 2 + 6 = 12 digits.
     */
    public static String calculateRrn(String serverId, String stan) {
        if (serverId == null) serverId = "01";
        if (stan == null) stan = "000000";

        // 1. Last Digit of Year (e.g. 2026 -> 6)
        SimpleDateFormat yearFmt = new SimpleDateFormat("y", Locale.US);
        yearFmt.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        String yearStr = yearFmt.format(new Date());
        char lastDigitYear = yearStr.charAt(yearStr.length() - 1);

        // 2. Julian Date (Day of Year) - 3 digits (e.g. 021)
        SimpleDateFormat julianFmt = new SimpleDateFormat("D", Locale.US);
        julianFmt.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        String julian = String.format(Locale.US, "%03d", Integer.parseInt(julianFmt.format(new Date())));

        // 3. Server ID (e.g. 01)
        // Ensure strictly 2 chars? Assume config is correct or pad?
        // Let's enforce 2 chars for safety.
        String sid = serverId;
        if (sid.length() > 2) sid = sid.substring(0, 2);
        else if (sid.length() < 2) sid = String.format("%2s", sid).replace(' ', '0');

        // 4. STAN (F11) - 6 digits
        String s = formatStan6(stan);

        return lastDigitYear + julian + sid + s;
    }

    /**
     * Build Field 60 for NAPAS - Case A: UPI Chip Transaction.
     * Format: ans...060 (LLLVAR), content is ASCII.
     *
     * Structure:
     * Component 1 (Terminal Information) - 12 bytes (ASCII digits):
     *  - Byte1: chip read capability (e.g. '6')
     *  - Byte2: chip txn status (e.g. '1')
     *  - Byte3-4: channel (e.g. "03" POS, "08" Mobile)
     *  - Byte5-12: reserved "00000000"
     * Component 2 (Sender Information) - 15 bytes:
     *  - Byte1-3: currency exponent digits (e.g. "000")
     *  - Byte4: initiation method (e.g. '1' card present)
     *  - Byte5-15: reserved "00000000000"
     *
     * Total length: 27 chars.
     */
    public static String buildField60UpiChipCaseA(
            char chipReadCapability,
            char chipTxnStatus,
            String channel2,
            String currencyExponent3,
            char initiationMethod) {

        if (channel2 == null || !channel2.matches("\\d{2}")) {
            throw new IllegalArgumentException("F60 channel must be 2 digits");
        }
        if (currencyExponent3 == null || !currencyExponent3.matches("\\d{3}")) {
            throw new IllegalArgumentException("F60 currencyExponent must be 3 digits");
        }

        String component1 = String.valueOf(chipReadCapability)
                + chipTxnStatus
                + channel2
                + "00000000";
        if (component1.length() != 12) {
            throw new IllegalStateException("F60 component1 must be 12, got " + component1.length());
        }

        String component2 = currencyExponent3
                + initiationMethod
                + "00000000000";
        if (component2.length() != 15) {
            throw new IllegalStateException("F60 component2 must be 15, got " + component2.length());
        }

        String f60 = component1 + component2;
        if (f60.length() != 27) {
            throw new IllegalStateException("F60 case A must be exactly 27, got " + f60.length());
        }
        return f60;
    }

    /** Builder for TransactionContext. */
    public static final class Builder {
        private final TxnType txnType;

        private String terminalId41;
        private String merchantId42;
        private String merchantNameLocation43;

        private String pan2;
        private String track2_35;
        private String expiry14;
        private String cardSeq23;

        private String processingCode3;
        private String amount4;
        private String transmissionDt7;
        private String stan11;
        private String localTime12;
        private String localDate13;
        private String mcc18;
        private String country19;
        private String posEntryMode22;
        private String posCondition25;
        private String acquirerId32;
        private String rrn37;
        private String currency49;

        private boolean encryptPin;
        private String pinBlock52;
        private String iccData55;
        private String field60;

        private String mac128;

        public Builder(TxnType txnType) {
            this.txnType = txnType;
        }

        public Builder terminalId41(String terminalId41) { this.terminalId41 = terminalId41; return this; }
        public Builder merchantId42(String merchantId42) { this.merchantId42 = merchantId42; return this; }
        public Builder merchantNameLocation43(String merchantNameLocation43) { this.merchantNameLocation43 = merchantNameLocation43; return this; }

        public Builder pan2(String pan2) { this.pan2 = pan2; return this; }
        public Builder track2_35(String track2_35) { this.track2_35 = track2_35; return this; }
        public Builder expiry14(String expiry14) { this.expiry14 = expiry14; return this; }
        public Builder cardSeq23(String cardSeq23) { this.cardSeq23 = cardSeq23; return this; }

        public Builder processingCode3(String processingCode3) { this.processingCode3 = processingCode3; return this; }
        public Builder amount4(String amount4) { this.amount4 = amount4; return this; }
        public Builder transmissionDt7(String transmissionDt7) { this.transmissionDt7 = transmissionDt7; return this; }
        public Builder stan11(String stan11) { this.stan11 = stan11; return this; }
        public Builder localTime12(String localTime12) { this.localTime12 = localTime12; return this; }
        public Builder localDate13(String localDate13) { this.localDate13 = localDate13; return this; }
        public Builder mcc18(String mcc18) { this.mcc18 = mcc18; return this; }
        public Builder country19(String country19) { this.country19 = country19; return this; }
        public Builder posEntryMode22(String posEntryMode22) { this.posEntryMode22 = posEntryMode22; return this; }
        public Builder posCondition25(String posCondition25) { this.posCondition25 = posCondition25; return this; }
        public Builder acquirerId32(String acquirerId32) { this.acquirerId32 = acquirerId32; return this; }
        public Builder rrn37(String rrn37) { this.rrn37 = rrn37; return this; }
        public Builder currency49(String currency49) { this.currency49 = currency49; return this; }

        public Builder encryptPin(boolean encryptPin) { this.encryptPin = encryptPin; return this; }
        public Builder pinBlock52(String pinBlock52) { this.pinBlock52 = pinBlock52; return this; }
        public Builder iccData55(String iccData55) { this.iccData55 = iccData55; return this; }
        public Builder field60(String field60) { this.field60 = field60; return this; }

        public Builder mac128(String mac128) { this.mac128 = mac128; return this; }

        public TransactionContext build() {
            return new TransactionContext(this);
        }
    }
}
