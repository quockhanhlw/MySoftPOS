package com.example.mysoftpos.iso8583;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.iso8583.TransactionContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Mutable Transaction Context POJO.
 */
public class TransactionContext {

    // ── Thread-safe formatters (DateTimeFormatter là immutable/thread-safe) ──
    private static final DateTimeFormatter FMT_DE7  =
            DateTimeFormatter.ofPattern("MMddHHmmss", Locale.US); // DE 7
    private static final DateTimeFormatter FMT_DE12 =
            DateTimeFormatter.ofPattern("HHmmss",     Locale.US); // DE 12
    private static final DateTimeFormatter FMT_DE13 =
            DateTimeFormatter.ofPattern("MMdd",       Locale.US); // DE 13
    private static final DateTimeFormatter FMT_YEAR =
            DateTimeFormatter.ofPattern("y",          Locale.US);
    private static final DateTimeFormatter FMT_DAY  =
            DateTimeFormatter.ofPattern("D",          Locale.US); // Julian day

    // Core Fields
    public TxnType txnType;
    public String terminalId41;
    public String merchantId42;
    public String merchantNameLocation43;

    public String pan2;
    public String track2_35;
    public String expiry14; // Unused? Kept for safety
    public String cardSeq23;

    public String processingCode3;
    public String amount4;
    public String transmissionDt7;
    public String stan11;
    public String localTime12;
    public String localDate13;
    public String settlementDate15;

    public String mcc18;
    public String country19;
    public String posEntryMode22;
    public String posCondition25;
    public String acquirerId32;
    public String rrn37;
    public String currency49;

    // Networking
    public String ip;
    public int port;

    public boolean encryptPin;
    public String pinBlock52;
    public String iccData55;
    public String field60;
    public String mac128;

    // --- NFC CHIP (Domestic NAPAS) fields ---
    /** Pre-built ICC data hex string for DE 55 (set externally if not using CardInputData.emvTags) */
    public String reversalIccData55; // DE 55 for reversal (may contain DF31, 95, 9F10, 9F36)
    /** Issuer Script Result from NapasEmvProcessor (for reversal DE 55 tag DF31) */
    public byte[] issuerScriptResult;
    /** Whether the NFC card is still connected (for post-response processing) */
    public boolean nfcCardConnected;

    public TransactionContext() {
        // No-arg constructor
    }

    public void generateDateTime() {
        LocalDateTime now = LocalDateTime.now();
        this.transmissionDt7  = FMT_DE7.format(now);   // DE 7:  MMddHHmmss
        this.localTime12      = FMT_DE12.format(now);  // DE 12: HHmmss
        this.localDate13      = FMT_DE13.format(now);  // DE 13: MMdd
        this.settlementDate15 = this.localDate13;
    }

    /**
     * RRN = Last Digit Year (1) + Julian Date (3) + Server ID (2) + STAN (6)
     */
    public static String calculateRrn(String serverId, String stan) {
        if (stan == null)
            stan = "000000";
        if (serverId == null)
            serverId = "00";
        if (serverId.length() > 2)
            serverId = serverId.substring(0, 2);
        while (serverId.length() < 2)
            serverId = "0" + serverId;

        LocalDateTime now = LocalDateTime.now();

        // Last digit of year
        String yearStr = FMT_YEAR.format(now);
        String lastDigitYear = yearStr.substring(yearStr.length() - 1);

        // Julian Date (day-of-year, 1–366)
        int dayOfYear = Integer.parseInt(FMT_DAY.format(now));
        String julianDate = String.format(Locale.US, "%03d", dayOfYear);

        return lastDigitYear + julianDate + serverId + stan;
    }

    /**
     * Format Amount for DE 4 (Default VND).
     * VND: Input (e.g. "1234") -> Append "00" -> "123400" -> Pad to 12 chars ->
     * "000000123400"
     */
    public static String formatAmount12(String amount) {
        return formatAmount12(amount, "704"); // Default VND
    }

    /**
     * Format Amount for DE 4 with Currency.
     * 
     * VND (704): Input amount is whole number, append "00" suffix for minor units.
     * Example: "1234" -> "123400" -> "000000123400"
     * 
     * USD (840): Input amount already includes cents (last 2 digits are cents).
     * Example: "345678" -> "000000345678" (means $3,456.78)
     * 
     * @param amount       Raw amount string
     * @param currencyCode "704" for VND, "840" for USD
     */
    public static String formatAmount12(String amount, String currencyCode) {
        if (amount == null || amount.isEmpty())
            return "000000000000";
        try {
            long val = Long.parseLong(amount.replace(".", "").replace(",", ""));

            if ("704".equals(currencyCode)) {
                // VND: Append "00" (multiply by 100) - VND has no minor units
                val = val * 100;
            }
            // USD (840): Input already includes cents, no multiplication needed
            // Example: 345678 means $3,456.78

            return String.format(Locale.US, "%012d", val);
        } catch (NumberFormatException e) {
            return "000000000000";
        }
    }

    public static String formatStan6(String stan) {
        if (stan == null)
            return "000000";
        try {
            int val = Integer.parseInt(stan);
            return String.format(Locale.US, "%06d", val);
        } catch (NumberFormatException e) {
            return "000000";
        }
    }

    public static String buildLocalDate13Now() {
        return FMT_DE13.format(LocalDateTime.now());
    }

    public static String defaultCurrencyVND() {
        return "704";
    }

    public static final class Builder {
        private final TransactionContext ctx;

        public Builder(TxnType txnType) {
            this.ctx = new TransactionContext();
            this.ctx.txnType = txnType;
        }

        public Builder pan2(String value) { ctx.pan2 = value; return this; }
        public Builder processingCode3(String value) { ctx.processingCode3 = value; return this; }
        public Builder amount4(String value) { ctx.amount4 = value; return this; }
        public Builder transmissionDt7(String value) { ctx.transmissionDt7 = value; return this; }
        public Builder stan11(String value) { ctx.stan11 = value; return this; }
        public Builder localTime12(String value) { ctx.localTime12 = value; return this; }
        public Builder localDate13(String value) { ctx.localDate13 = value; return this; }
        public Builder mcc18(String value) { ctx.mcc18 = value; return this; }
        public Builder country19(String value) { ctx.country19 = value; return this; }
        public Builder posEntryMode22(String value) { ctx.posEntryMode22 = value; return this; }
        public Builder cardSeq23(String value) { ctx.cardSeq23 = value; return this; }
        public Builder posCondition25(String value) { ctx.posCondition25 = value; return this; }
        public Builder acquirerId32(String value) { ctx.acquirerId32 = value; return this; }
        public Builder rrn37(String value) { ctx.rrn37 = value; return this; }
        public Builder terminalId41(String value) { ctx.terminalId41 = value; return this; }
        public Builder merchantId42(String value) { ctx.merchantId42 = value; return this; }
        public Builder merchantNameLocation43(String value) { ctx.merchantNameLocation43 = value; return this; }
        public Builder currency49(String value) { ctx.currency49 = value; return this; }
        public Builder encryptPin(boolean value) { ctx.encryptPin = value; return this; }
        public Builder pinBlock52(String value) { ctx.pinBlock52 = value; return this; }
        public Builder field60(String value) { ctx.field60 = value; return this; }
        public Builder mac128(String value) { ctx.mac128 = value; return this; }
        public Builder ip(String value) { ctx.ip = value; return this; }
        public Builder port(int value) { ctx.port = value; return this; }

        public TransactionContext build() {
            if (ctx.country19 == null || ctx.country19.isEmpty()) {
                ctx.country19 = ctx.currency49 != null && !ctx.currency49.isEmpty() ? ctx.currency49 : defaultCurrencyVND();
            }
            return ctx;
        }
    }

    public static String formatTid8(String tid) {
        if (tid == null) tid = "";
        if (tid.length() > 8) return tid.substring(0, 8);
        return String.format(Locale.ROOT, "%-8s", tid);
    }

    public static String formatMid15(String mid) {
        if (mid == null) mid = "";
        if (mid.length() > 15) return mid.substring(0, 15);
        return String.format(Locale.ROOT, "%-15s", mid);
    }

    public static String buildField60UpiChipCaseA(char terminalCapability,
            char cardholderAuthCapability,
            String attendedIndicator,
            String partialAuthorizationIndicator,
            char txnEnvironment) {
        String seed = new StringBuilder()
                .append(terminalCapability)
                .append(cardholderAuthCapability)
                .append(attendedIndicator == null ? "" : attendedIndicator)
                .append(partialAuthorizationIndicator == null ? "" : partialAuthorizationIndicator)
                .append(txnEnvironment)
                .toString();
        if (seed.length() >= 27) {
            return seed.substring(0, 27);
        }
        StringBuilder out = new StringBuilder(seed);
        while (out.length() < 27) {
            out.append('0');
        }
        return out.toString();
    }
}
