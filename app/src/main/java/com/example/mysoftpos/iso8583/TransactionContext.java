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
        SimpleDateFormat f7 = new SimpleDateFormat("MMddhhmmss", Locale.US);
        // Format: MM=month(01-12), dd=day of month(01-31), hh=hour 12h(01-12), mm=minute, ss=second
        
        SimpleDateFormat f12 = new SimpleDateFormat("HHmmss", Locale.US); // Local
        SimpleDateFormat f13 = new SimpleDateFormat("MMdd", Locale.US);   // Local
        
        this.transmissionDt7 = f7.format(now);
        this.localTime12 = f12.format(now);
        this.localDate13 = f13.format(now);
        this.settlementDate15 = f13.format(now); // DE 15 same as DE 13 (MMdd)
        
        android.util.Log.d("ISO_DEBUG", "GenDateTime: Now=" + now + " DE7=" + transmissionDt7 + " DE12=" + localTime12 + " DE13=" + localDate13);
    }

    // Static formatting methods moved to IsoUtils.java - RESTORED FOR COMPATIBILITY
    public static String calculateRrn(String serverId, String stan) {
        return IsoUtils.padLeftZero(stan, 12); 
    }
    
    public static String generateRrn(String stan) {
        return calculateRrn("00", stan);
    }

    public static String formatStan6(String s) {
        return IsoUtils.formatStan6(s);
    }
    
    public static String formatTid8(String s) {
        return IsoUtils.formatTid8(s);
    }
    
    public static String formatMid15(String s) {
        return IsoUtils.formatMid15(s);
    }
    
    public static String defaultCurrencyVND() {
        return "704";
    }
    
    public static String formatAmount12(String s) {
         return IsoUtils.formatAmount12(s);
    }
    
    public static String buildLocalDate13Now() {
         return new SimpleDateFormat("MMdd", Locale.US).format(new Date());
    }
}
