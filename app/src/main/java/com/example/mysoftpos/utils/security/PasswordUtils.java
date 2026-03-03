package com.example.mysoftpos.utils.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PA-DSS 2.x: Secure password hashing.
 * Uses PBKDF2WithHmacSHA256 with unique salt.
 * SHA-256 retained only for username hashing (non-password use).
 */
public class PasswordUtils {

    // PA-DSS 2.x: PBKDF2 iteration count (NIST recommended minimum)
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 256;

    /**
     * PA-DSS 2.1: Hash password using PBKDF2WithHmacSHA256 with random salt.
     * Returns format: "iterations:base64Salt:base64Hash"
     */
    public static String hashPassword(String password) {
        if (password == null)
            return null;
        try {
            byte[] salt = generateSalt();
            byte[] hash = pbkdf2(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH);
            return PBKDF2_ITERATIONS + ":" + base64Encode(salt) + ":" + base64Encode(hash);
        } catch (Exception e) {
            throw new RuntimeException("PBKDF2 hashing failed", e);
        }
    }

    /**
     * PA-DSS 2.1: Verify password against stored PBKDF2 hash.
     * Also supports legacy SHA-256 hash for backward compatibility.
     */
    public static boolean verifyPassword(String password, String storedHash) {
        if (password == null || storedHash == null)
            return false;

        // Check if stored hash is in PBKDF2 format (iterations:salt:hash)
        if (storedHash.contains(":")) {
            try {
                String[] parts = storedHash.split(":");
                if (parts.length != 3)
                    return false;
                int iterations = Integer.parseInt(parts[0]);
                byte[] salt = base64Decode(parts[1]);
                byte[] expectedHash = base64Decode(parts[2]);
                byte[] actualHash = pbkdf2(password.toCharArray(), salt, iterations, KEY_LENGTH);
                return constantTimeEquals(expectedHash, actualHash);
            } catch (Exception e) {
                return false;
            }
        }

        // Legacy fallback: SHA-256 (for existing accounts before migration)
        return hashSHA256(password).equalsIgnoreCase(storedHash);
    }

    /**
     * SHA-256 hash — used ONLY for username hashing (non-password).
     * PA-DSS note: NOT suitable for password storage.
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
     * Legacy verify — for backward compatibility only.
     */
    public static boolean verify(String plainText, String hash) {
        if (plainText == null || hash == null)
            return false;
        return hashSHA256(plainText).equalsIgnoreCase(hash);
    }

    // --- Internal helpers ---

    private static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength)
            throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword(); // PA-DSS 1.x: Wipe password from memory
        }
    }

    // PA-DSS 9.x: Constant-time comparison to prevent timing attacks
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length)
            return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    private static String base64Encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    private static byte[] base64Decode(String data) {
        return Base64.getDecoder().decode(data);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
