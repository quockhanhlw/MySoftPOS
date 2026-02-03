package com.example.mysoftpos.utils.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for password hashing using SHA-256.
 */
public class PasswordUtils {

    /**
     * Hash a string using SHA-256 algorithm.
     * 
     * @param input The plain text to hash
     * @return Hex string representation of the SHA-256 hash
     */
    public static String hashSHA256(String input) {
        if (input == null)
            return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verify if a plain text matches a hashed value.
     * 
     * @param plainText The plain text to verify
     * @param hash      The hash to compare against
     * @return true if the plain text hash matches the given hash
     */
    public static boolean verify(String plainText, String hash) {
        if (plainText == null || hash == null)
            return false;
        return hashSHA256(plainText).equalsIgnoreCase(hash);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}





