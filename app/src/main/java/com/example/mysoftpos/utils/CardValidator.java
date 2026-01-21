package com.example.mysoftpos.utils;

import java.util.Calendar;

public class CardValidator {

    private static final long MAX_AMOUNT_LIMIT = 50000000; // 50,000,000 VND

    /**
     * Checks if the PAN is valid using the Luhn Algorithm.
     */
    public static boolean isValidLuhn(String pan) {
        if (pan == null || pan.length() < 13 || pan.length() > 19) return false;

        int sum = 0;
        boolean alternate = false;
        for (int i = pan.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(pan.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    /**
     * Checks if the card is expired.
     * @param expiryDate Format YYMM
     * @return true if expired
     */
    public static boolean isExpired(String expiryDate) {
        if (expiryDate == null || expiryDate.length() != 4) return true; 

        try {
            int expYear2Digit = Integer.parseInt(expiryDate.substring(0, 2));
            int expMonth = Integer.parseInt(expiryDate.substring(2, 4));

            Calendar now = Calendar.getInstance();
            int currentYearFull = now.get(Calendar.YEAR);
            int currentYear2Digit = currentYearFull % 100;
            int currentMonth = now.get(Calendar.MONTH) + 1; 

            if (expYear2Digit < currentYear2Digit) {
                return true; 
            } else if (expYear2Digit == currentYear2Digit) {
                return expMonth < currentMonth; 
            }
            return false; 
        } catch (NumberFormatException e) {
            return true; 
        }
    }

    /**
     * Checks if the transaction amount is valid.
     */
    public static boolean isValidAmount(String amount) {
        if (amount == null) return false;
        try {
            long val = Long.parseLong(amount);
            return val > 0 && val <= MAX_AMOUNT_LIMIT;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Master Narrative Rule 3: Check Card Type (must start with 9704).
     */
    public static boolean isValidNapasBin(String pan) {
        return pan != null && pan.startsWith("9704");
    }
}
