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

    public TransactionContext() {
        // No-arg constructor
    }

    public void generateDateTime() {
        Date now = new Date();
        SimpleDateFormat f7 = new SimpleDateFormat("MMddHHmmss", Locale.US);
        SimpleDateFormat f12 = new SimpleDateFormat("HHmmss", Locale.US);
        SimpleDateFormat f13 = new SimpleDateFormat("MMdd", Locale.US);
        
        this.transmissionDt7 = f7.format(now);
        this.localTime12 = f12.format(now);
        this.localDate13 = f13.format(now);
    }

    public static String calculateRrn(String serverId, String stan) {
        if (stan == null) stan = "000000";
        if (serverId == null) serverId = "00";
        if (serverId.length() > 2) serverId = serverId.substring(0, 2);
        
        SimpleDateFormat sdf = new SimpleDateFormat("yD", Locale.US);
        String datePart = sdf.format(new Date()); 
        String yearDigit = datePart.substring(3, 4);
        String julian = datePart.substring(4);
        return yearDigit + julian + serverId + stan; 
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
             long val = Long.parseLong(s);
             return String.format(Locale.US, "%012d", val);
         } catch(Exception e) { return "000000000000"; }
    }
    
    public static String buildLocalDate13Now() {
         return new SimpleDateFormat("MMdd", Locale.US).format(new Date());
    }
}
