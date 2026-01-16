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

    // Reversal specific
    public final String originalDataElements90; // for reversal/void

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

        this.originalDataElements90 = b.originalDataElements90;
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

    /**
     * Build Field 90 full 42 digits per NAPAS:
     * 1) Original MTI (4)
     * 2) Original STAN (6) - F11 of original
     * 3) Original Date/Time (10) - F7 of original, MMDDhhmmss
     * 4) Original Acquiring ID (11) - F32 of original, left pad '0' to 11
     * 5) Original Forwarding ID (11) - F33 or F32, if absent fill 11 zeros
     */
    public static String buildField90Full42(
            String originalMti4,
            String originalStan11,
            String originalF7_10,
            String originalAcquirerId32,
            String originalForwardingId11) {

        if (originalMti4 == null || !originalMti4.matches("\\d{4}")) {
            throw new IllegalArgumentException("F90 original MTI must be 4 digits");
        }
        String stan6 = formatStan6(originalStan11);
        if (stan6 == null) {
            throw new IllegalArgumentException("F90 original STAN required");
        }
        if (originalF7_10 == null || !originalF7_10.matches("\\d{10}")) {
            throw new IllegalArgumentException("F90 original F7 must be 10 digits (MMddHHmmss)");
        }

        String acq11 = leftPadDigits(originalAcquirerId32, 11);
        String fwd11;
        if (originalForwardingId11 == null || originalForwardingId11.trim().isEmpty()) {
            fwd11 = "00000000000";
        } else {
            fwd11 = leftPadDigits(originalForwardingId11, 11);
        }

        String f90 = originalMti4 + stan6 + originalF7_10 + acq11 + fwd11;
        if (!f90.matches("\\d{42}")) {
            throw new IllegalStateException("F90 must be 42 digits, got: " + f90);
        }
        return f90;
    }

    /**
     * Build Field 60 for NAPAS - Case A: UPI Chip Transaction.
     * Format: ans...060 (LLLVAR), content is ASCII.
     *
     * Structure:
     * Component 1 (Terminal Information) - 12 bytes (ASCII digits):
     *  - Byte1: chip read capability (e.g. '6')
     *  - Byte2: chip txn status (e.g. '1')
     *  - Byte3-4: channel (e.g. '03' POS, '08' Mobile)
     *  - Byte5-12: reserved '00000000'
     * Component 2 (Sender Information) - 15 bytes:
     *  - Byte1-3: currency exponent digits (e.g. '000' as per your doc note)
     *  - Byte4: initiation method (e.g. '1' card present)
     *  - Byte5-15: reserved '00000000000'
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
        if (f60.length() > 60) {
            throw new IllegalArgumentException("F60 too long (>60)");
        }
        return f60;
    }

    private static String padRightSpace(String s, int len) {
        String v = s == null ? "" : s;
        if (v.length() > len) return v.substring(0, len);
        StringBuilder sb = new StringBuilder(v);
        while (sb.length() < len) sb.append(' ');
        return sb.toString();
    }

    private static String leftPadDigits(String v, int len) {
        if (v == null) {
            throw new IllegalArgumentException("Value required");
        }
        String d = v.replaceAll("\\D+", "");
        if (d.isEmpty()) {
            throw new IllegalArgumentException("Value must contain digits");
        }
        if (d.length() > len) {
            throw new IllegalArgumentException("Value too long (" + d.length() + ") for len=" + len);
        }
        return String.format(Locale.US, "%" + len + "s", d).replace(' ', '0');
    }

    // --------------------------------------------------------------------------

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
        private String originalDataElements90;
        private String mac128;

        public Builder(TxnType txnType) {
            this.txnType = txnType;
        }

        public Builder terminalId41(String v) { this.terminalId41 = v; return this; }
        public Builder merchantId42(String v) { this.merchantId42 = v; return this; }
        public Builder merchantNameLocation43(String v) { this.merchantNameLocation43 = v; return this; }
        public Builder pan2(String v) { this.pan2 = v; return this; }
        public Builder track2_35(String v) { this.track2_35 = v; return this; }
        public Builder expiry14(String v) { this.expiry14 = v; return this; }
        public Builder cardSeq23(String v) { this.cardSeq23 = v; return this; }
        public Builder processingCode3(String v) { this.processingCode3 = v; return this; }
        public Builder amount4(String v) { this.amount4 = v; return this; }
        public Builder transmissionDt7(String v) { this.transmissionDt7 = v; return this; }
        public Builder stan11(String v) { this.stan11 = v; return this; }
        public Builder localTime12(String v) { this.localTime12 = v; return this; }
        public Builder localDate13(String v) { this.localDate13 = v; return this; }
        public Builder mcc18(String v) { this.mcc18 = v; return this; }
        public Builder country19(String v) { this.country19 = v; return this; }
        public Builder posEntryMode22(String v) { this.posEntryMode22 = v; return this; }
        public Builder posCondition25(String v) { this.posCondition25 = v; return this; }
        public Builder acquirerId32(String v) { this.acquirerId32 = v; return this; }
        public Builder rrn37(String v) { this.rrn37 = v; return this; }
        public Builder currency49(String v) { this.currency49 = v; return this; }
        public Builder encryptPin(boolean v) { this.encryptPin = v; return this; }
        public Builder pinBlock52(String v) { this.pinBlock52 = v; return this; }
        public Builder iccData55(String v) { this.iccData55 = v; return this; }
        public Builder field60(String v) { this.field60 = v; return this; }
        public Builder originalDataElements90(String v) { this.originalDataElements90 = v; return this; }
        public Builder mac128(String v) { this.mac128 = v; return this; }

        public TransactionContext build() {
            return new TransactionContext(this);
        }
    }

    // Move: helper methods MUST stay above/beside builder for readability.
    // (No functional change; this comment is only to guide future edits.)
}
