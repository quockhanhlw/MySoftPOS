package com.example.mysoftpos.iso8583;

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
    public final String originalDataElements90; // for reversal

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
}

