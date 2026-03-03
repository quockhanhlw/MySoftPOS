package com.example.mysoftpos.utils.validation;
import com.example.mysoftpos.utils.validation.TransactionValidator;

import com.example.mysoftpos.domain.model.CardInputData;

/**
 * Validates transaction inputs.
 */
public class TransactionValidator {

    public enum ValidationResult {
        VALID,
        INVALID_AMOUNT,
        INVALID_CARD_NUMBER,
        INVALID_EXPIRY,
        INVALID_CARD_DATA
    }

    public static ValidationResult validate(CardInputData card, String amountStr, boolean isPurchase) {
        // 1. Amount Check (Only for Purchase)
        if (isPurchase) {
            try {
                long amount = Long.parseLong(amountStr);
                if (amount <= 0)
                    return ValidationResult.INVALID_AMOUNT;
            } catch (NumberFormatException e) {
                return ValidationResult.INVALID_AMOUNT;
            }
        }

        if (card == null)
            return ValidationResult.INVALID_CARD_DATA;

        // 2. Card Validation
        String posMode = card.getPosEntryMode();
        boolean isNfc = "071".equals(posMode) || "072".equals(posMode);

        if (isNfc) {
            // NFC chip: PAN and ICC data come from card — just verify PAN is present
            if (card.getPan() == null || card.getPan().isEmpty())
                return ValidationResult.INVALID_CARD_NUMBER;
            // Expiry lives inside DE 55 tag 57 for chip cards — skip separate expiry check
            return ValidationResult.VALID;
        }

        // Manual (012): Check Luhn
        if ("012".equals(posMode)) {
            if (!luhnCheck(card.getPan())) {
                return ValidationResult.INVALID_CARD_NUMBER;
            }
        }

        // Basic Presence Checks
        if (card.getPan() == null || card.getPan().isEmpty())
            return ValidationResult.INVALID_CARD_NUMBER;

        // Check Expiry (Basic) — only for non-NFC modes
        if (card.getExpiryDate() == null || card.getExpiryDate().length() != 4) {
            return ValidationResult.INVALID_EXPIRY;
        }

        return ValidationResult.VALID;
    }

    private static boolean luhnCheck(String pan) {
        if (pan == null || pan.length() < 13)
            return false; // Basic length check
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






