package com.example.mysoftpos.utils;

import com.example.mysoftpos.domain.model.CardInputData;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TransactionValidator {

    public enum ValidationResult {
        VALID,
        INVALID_AMOUNT,
        INVALID_CARD_DATA,
        CARD_EXPIRED,
        LUHN_CHECK_FAILED
    }

    public static ValidationResult validate(CardInputData card, String amount, boolean isPurchase) {
        // 1. Amount Check (Purchase Only)
        if (isPurchase) {
            try {
                long amtVal = Long.parseLong(amount);
                if (amtVal <= 0) {
                    return ValidationResult.INVALID_AMOUNT;
                }
            } catch (NumberFormatException e) {
                return ValidationResult.INVALID_AMOUNT;
            }
        }

        // 2. Card Data Presence
        if (card == null || card.getPan() == null || card.getPan().trim().isEmpty()) {
            return ValidationResult.INVALID_CARD_DATA;
        }

        // 3. Expiry Check
        if (isExpired(card.getExpiryDate())) {
            return ValidationResult.CARD_EXPIRED;
        }

        // 4. Security Logic based on Flow Type
        if (card.isContactless()) {
            // --- NFC FLOW ---
            // Constraint: Tag 57 presence implicit in isContactless checks usually, 
            // but we trust emvUtils parsed it.
            // Optimization: SKIP Luhn Check.
            // Strictness: Ensure we actually have track data if needed? 
            // Validator focuses on business rules.
        } else {
            // --- MANUAL FLOW (Key-in) ---
            // Constraint: Enforce Luhn Algorithm
            if (!checkLuhn(card.getPan())) {
                return ValidationResult.LUHN_CHECK_FAILED;
            }
        }

        return ValidationResult.VALID;
    }

    private static boolean isExpired(String expiryMmyy) {
        if (expiryMmyy == null || expiryMmyy.length() != 4) return true;
        try {
            // Strictly compare YYMM
            // Format: yyMM (e.g., 2512 for Dec 2025)
            SimpleDateFormat sdf = new SimpleDateFormat("yyMM", Locale.US);
            sdf.setLenient(false);
            
            // Current Month
            String current = sdf.format(new Date());
            
            // Compare as Strings works for yyMM format (Year first)
            return expiryMmyy.compareTo(current) < 0;
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean checkLuhn(String pan) {
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
}
