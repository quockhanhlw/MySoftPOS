package com.example.mysoftpos.utils.logging;

import java.util.HashMap;
import java.util.Map;

public class ResponseCodeHelper {

    private static final Map<String, String> MESSAGES = new HashMap<>();

    static {
        MESSAGES.put("00", "Transaction Successful");
        MESSAGES.put("01", "Refer to Card Issuer");
        MESSAGES.put("03", "Invalid Merchant");
        MESSAGES.put("04", "Pick Up Card");
        MESSAGES.put("05", "Do Not Honor");
        MESSAGES.put("12", "Invalid Transaction");
        MESSAGES.put("13", "Invalid Amount");
        MESSAGES.put("14", "Invalid Card Number");
        MESSAGES.put("15", "No Such Issuer");
        MESSAGES.put("21", "No Action Taken");
        MESSAGES.put("25", "Unable to Locate Record");
        MESSAGES.put("30", "Format Error");
        MESSAGES.put("34", "Suspected Fraud");
        MESSAGES.put("39", "No Credit Account");
        MESSAGES.put("41", "Lost Card");
        MESSAGES.put("43", "Stolen Card");
        MESSAGES.put("51", "Insufficient Funds");
        MESSAGES.put("53", "No Savings Account");
        MESSAGES.put("54", "Expired Card");
        MESSAGES.put("55", "Incorrect PIN");
        MESSAGES.put("57", "Transaction Not Permitted");
        MESSAGES.put("58", "Transaction Not Permitted on Terminal");
        MESSAGES.put("59", "Suspected Fraud");
        MESSAGES.put("61", "Exceeds Withdrawal Limit");
        MESSAGES.put("62", "Restricted Card");
        MESSAGES.put("63", "Security Violation");
        MESSAGES.put("64", "Original Amount Incorrect");
        MESSAGES.put("65", "Exceeds Withdrawal Frequency");
        MESSAGES.put("68", "Response Received Too Late");
        MESSAGES.put("75", "Allowable PIN Tries Exceeded");
        MESSAGES.put("76", "Invalid Account");
        MESSAGES.put("90", "Cut-off in Progress");
        MESSAGES.put("91", "Issuer or Switch Inoperative");
        MESSAGES.put("92", "Routing Error");
        MESSAGES.put("94", "Duplicate Transaction");
        MESSAGES.put("96", "System Error");
    }

    public static String getMessage(String rc) {
        if (rc == null)
            return "Unknown Error";
        String msg = MESSAGES.get(rc);
        if (msg == null) {
            return "Unknown Error (RC: " + rc + ")";
        }
        return msg;
    }
}
