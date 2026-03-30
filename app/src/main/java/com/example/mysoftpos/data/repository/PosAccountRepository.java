package com.example.mysoftpos.data.repository;

import com.example.mysoftpos.data.local.entity.PosAccountEntity;

/**
 * Repository interface for User data access.
 * Consolidates user lookup/cache/lockout logic that was previously
 * scattered across LoginActivity (reducing God-class pattern).
 */
@Deprecated
public interface PosAccountRepository {

    /**
     * Find a user by phone, email, or username hash (in that order).
     * Must be called from IO thread.
     */
    PosAccountEntity findUser(String identifier);

    /**
     * Find a user by backend ID.
     * Must be called from IO thread.
     */
    PosAccountEntity findByBackendId(long backendId);

    /**
     * Cache user data locally (from backend API response).
     * Creates or updates the local Room record.
     * Must be called from IO thread.
     */
    void cacheUser(String username, String password,
                   com.example.mysoftpos.data.remote.api.ApiService.PosAccountDto userDto);

    /**
     * Resolve the local Room user ID for a given username.
     * Falls back to backend ID if local user not found.
     * Must be called from IO thread.
     */
    long resolveLocalUserId(String username, long backendId);

    /**
     * Check if the user account is locked.
     * Returns remaining lock time in millis, or 0 if not locked.
     */
    long getLockRemainingMillis(PosAccountEntity user);

    /**
     * Record a failed login attempt, locking the account after 6 failures.
     * Must be called from IO thread.
     */
    void incrementFailedAttempts(PosAccountEntity user);

    /**
     * Reset failed login attempts on successful login.
     * Must be called from IO thread.
     */
    void resetFailedAttempts(PosAccountEntity user);

    /**
     * Resolve business type (MCC source) from merchant profile linked to this account.
     */
    String resolveBusinessType(PosAccountEntity user);

    /** Resolve merchant display name/full name for a given account. */
    String resolveDisplayName(PosAccountEntity user);

    /** Resolve merchant contact phone for a given account. */
    String resolveContactPhone(PosAccountEntity user);

    /** Resolve merchant contact email for a given account. */
    String resolveContactEmail(PosAccountEntity user);
}

