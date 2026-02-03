package com.example.mysoftpos.iso8583;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.iso8583.TransactionContext;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Mutable Transaction Context POJO.
 */
public class TransactionContext {

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
    public String fwdInst33;
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

    public TransactionContext() {
        // No-arg constructor
    }

    public void generateDateTime() {
        Date now = new Date();
        // DE 7: MMDDhhmmss
        SimpleDateFormat f7 = new SimpleDateFormat("MMddHHmmss", Locale.US);
        this.transmissionDt7 = f7.format(now);

        // DE 12: HHmmss
        SimpleDateFormat f12 = new SimpleDateFormat("HHmmss", Locale.US);
        this.localTime12 = f12.format(now);

        // DE 13: MMDD
        SimpleDateFormat f13 = new SimpleDateFormat("MMdd", Locale.US);
        this.localDate13 = f13.format(now);
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

        Date now = new Date();
        // Last Digit Year
        SimpleDateFormat yearFmt = new SimpleDateFormat("y", Locale.US);
        String yearStr = yearFmt.format(now);
        String lastDigitYear = yearStr.substring(yearStr.length() - 1);

        // Julian Date
        SimpleDateFormat dayFmt = new SimpleDateFormat("D", Locale.US);
        int dayOfYear = Integer.parseInt(dayFmt.format(now));
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
        return new SimpleDateFormat("MMdd", Locale.US).format(new Date());
    }

    public static String defaultCurrencyVND() {
        return "704";
    }
}






