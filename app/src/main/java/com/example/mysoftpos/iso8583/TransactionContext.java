package com.example.mysoftpos.iso8583;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

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
    public String expiry14;
    public String cardSeq23;

    public String processingCode3;
    public String amount4;
    public String transmissionDt7;
    public String stan11;
    public String localTime12;
    public String localDate13;
    public String settlementDate15; 
    // expiry14 moved up
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
        SimpleDateFormat f7 = new SimpleDateFormat("MMddHHmmss", Locale.US);
        // f7.setTimeZone("GMT"); // Reverted to Local (User Request)
        
        SimpleDateFormat f12 = new SimpleDateFormat("HHmmss", Locale.US); // Local
        SimpleDateFormat f13 = new SimpleDateFormat("MMdd", Locale.US);   // Local
        
        this.transmissionDt7 = f7.format(now);
        this.localTime12 = f12.format(now);
        this.localDate13 = f13.format(now);
        this.settlementDate15 = f13.format(now); // DE 15 same as DE 13 (MMdd)
        
        android.util.Log.d("ISO_DEBUG", "GenDateTime: Now=" + now + " DE7=" + transmissionDt7 + " DE12=" + localTime12 + " DE13=" + localDate13);
    }

    public static String calculateRrn(String serverId, String stan) {
        if (stan == null) stan = "000000";
        if (serverId == null) serverId = "00";
        // Ensure Server ID is 2 chars padding? Or user specified format just says "Server_ID".
        // Usually RRN is 12 digits. 1(Y) + 3(J) + 6(S) = 10. So ServerID likely 2 chars.
        if (serverId.length() > 2) serverId = serverId.substring(0, 2);
        while (serverId.length() < 2) serverId = "0" + serverId;
        
        Date now = new Date();
        SimpleDateFormat yearFmt = new SimpleDateFormat("y", Locale.US); // e.g., 2026
        SimpleDateFormat dayFmt = new SimpleDateFormat("D", Locale.US);  // e.g., 23
        
        String yearStr = yearFmt.format(now);
        String lastDigitYear = yearStr.substring(yearStr.length() - 1);
        
        int dayOfYear = Integer.parseInt(dayFmt.format(now));
        String julianDate = String.format(Locale.US, "%03d", dayOfYear);
        
        return lastDigitYear + julianDate + serverId + stan; 
    }
    
    public static String generateRrn(String stan) {
        return calculateRrn("00", stan);
    }

    public static String formatStan6(String s) {
        if (s == null) return "000000";
        try {
            int val = Integer.parseInt(s.trim());
            return String.format(Locale.US, "%06d", val);
        } catch (Exception e) { return "000000"; }
    }
    
    public static String formatTid8(String s) {
        if (s == null) s = "";
        if (s.length() > 8) return s.substring(0, 8);
        while (s.length() < 8) s += " ";
        return s;
    }
    
    public static String formatMid15(String s) {
        if (s == null) s = "";
        if (s.length() > 15) return s.substring(0, 15);
        while (s.length() < 15) s += " ";
        return s;
    }
    
    public static String defaultCurrencyVND() {
        return "704";
    }
    
    public static String formatAmount12(String s) {
         if (s == null) return "000000000000";
         try {
             String clean = s.replaceAll("[^0-9]", "");
             long val = Long.parseLong(clean);
             val = val * 100; // Append 00 for cents/decimal requirement
             return String.format(Locale.US, "%012d", val);
         } catch(Exception e) { return "000000000000"; }
    }
    
    public static String buildLocalDate13Now() {
         return new SimpleDateFormat("MMdd", Locale.US).format(new Date());
    }
}
