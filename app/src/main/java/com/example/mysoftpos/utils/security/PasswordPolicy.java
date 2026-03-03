package com.example.mysoftpos.utils.security;

/**
 * PA-DSS 3.x: Password policy enforcement.
 * - Minimum 7 characters
 * - Must contain both alphabetic and numeric characters
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    // PA-DSS 3.1: Minimum password length
    public static final int MIN_LENGTH = 7;

    /**
     * Validate password against PA-DSS requirements.
     * 
     * @return null if valid, error message if invalid
     */
    public static String validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters";
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c))
                hasLetter = true;
            if (Character.isDigit(c))
                hasDigit = true;
        }
        // PA-DSS 3.1: Must contain both numeric and alphabetic
        if (!hasLetter)
            return "Password must contain at least one letter";
        if (!hasDigit)
            return "Password must contain at least one number";
        return null; // Valid
    }

    public static boolean isValid(String password) {
        return validate(password) == null;
    }
}
