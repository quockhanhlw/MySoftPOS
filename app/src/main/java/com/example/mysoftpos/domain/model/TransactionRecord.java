package com.example.mysoftpos.domain.model;

/**
 * Data Transfer Object for saving transaction records.
 * Replaces the 14-parameter saveTransaction method signature.
 */
public class TransactionRecord {

    public final String traceNumber;
    public final String amount;
    public final String status;
    public final String reqHex;
    public final String respHex;
    public final long timestamp;
    public final String merchantCode;
    public final String merchantName;
    public final String terminalCode;
    public final String panMasked;
    public final String bin;
    public final String last4;
    public final String scheme;
    public final String username;

    private TransactionRecord(Builder builder) {
        this.traceNumber = builder.traceNumber;
        this.amount = builder.amount;
        this.status = builder.status;
        this.reqHex = builder.reqHex;
        this.respHex = builder.respHex;
        this.timestamp = builder.timestamp;
        this.merchantCode = builder.merchantCode;
        this.merchantName = builder.merchantName;
        this.terminalCode = builder.terminalCode;
        this.panMasked = builder.panMasked;
        this.bin = builder.bin;
        this.last4 = builder.last4;
        this.scheme = builder.scheme;
        this.username = builder.username;
    }

    public static class Builder {
        private String traceNumber;
        private String amount;
        private String status;
        private String reqHex;
        private String respHex;
        private long timestamp;
        private String merchantCode;
        private String merchantName;
        private String terminalCode;
        private String panMasked;
        private String bin;
        private String last4;
        private String scheme;
        private String username;

        public Builder() {
        }

        public Builder setTraceNumber(String traceNumber) {
            this.traceNumber = traceNumber;
            return this;
        }

        public Builder setAmount(String amount) {
            this.amount = amount;
            return this;
        }

        public Builder setStatus(String status) {
            this.status = status;
            return this;
        }

        public Builder setRequestHex(String reqHex) {
            this.reqHex = reqHex;
            return this;
        }

        public Builder setResponseHex(String respHex) {
            this.respHex = respHex;
            return this;
        }

        public Builder setTimestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder setMerchantCode(String merchantCode) {
            this.merchantCode = merchantCode;
            return this;
        }

        public Builder setMerchantName(String merchantName) {
            this.merchantName = merchantName;
            return this;
        }

        public Builder setTerminalCode(String terminalCode) {
            this.terminalCode = terminalCode;
            return this;
        }

        public Builder setPanMasked(String panMasked) {
            this.panMasked = panMasked;
            return this;
        }

        public Builder setBin(String bin) {
            this.bin = bin;
            return this;
        }

        public Builder setLast4(String last4) {
            this.last4 = last4;
            return this;
        }

        public Builder setScheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder setUsername(String username) {
            this.username = username;
            return this;
        }

        public TransactionRecord build() {
            return new TransactionRecord(this);
        }
    }
}
