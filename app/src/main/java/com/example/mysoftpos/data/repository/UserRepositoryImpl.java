package com.example.mysoftpos.data.repository;

import android.util.Log;

import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.dao.UserDao;
import com.example.mysoftpos.data.local.entity.UserEntity;
import com.example.mysoftpos.data.remote.api.ApiService;
import com.example.mysoftpos.utils.security.PasswordUtils;

/**
 * Concrete implementation of {@link UserRepository}.
 * Extracted from LoginActivity to follow single-responsibility principle.
 * All public methods must be called from IO thread.
 */
public class UserRepositoryImpl implements UserRepository {

    private static final String TAG = "UserRepo";
    private static final int MAX_FAILED_ATTEMPTS = 6;
    private static final long LOCK_DURATION_MS = 30 * 60 * 1000L; // 30 min

    private final AppDatabase db;

    public UserRepositoryImpl(AppDatabase db) {
        this.db = db;
    }

    @Override
    public UserEntity findUser(String identifier) {
        if (identifier == null) return null;
        UserDao dao = db.userDao();

        // Try phone first
        UserEntity user = dao.findByPhone(identifier);
        if (user != null) return user;

        // Try email
        user = dao.findByEmail(identifier);
        if (user != null) return user;

        // Try username hash
        String hash = PasswordUtils.hashSHA256(identifier);
        return dao.findByUsernameHash(hash);
    }

    @Override
    public UserEntity findByBackendId(long backendId) {
        return db.userDao().findByBackendId(backendId);
    }

    @Override
    public void cacheUser(String username, String password, ApiService.UserDto userDto) {
        try {
            UserDao dao = db.userDao();
            String usernameHash = PasswordUtils.hashSHA256(username);
            String passwordHash = PasswordUtils.hashPassword(password);

            UserEntity existing = dao.findByUsernameHash(usernameHash);
            if (existing == null && userDto.phone != null) {
                existing = dao.findByPhone(userDto.phone);
            }
            if (existing == null && userDto.email != null) {
                existing = dao.findByEmail(userDto.email);
            }

            if (existing != null) {
                existing.passwordHash = passwordHash;
                existing.displayName = userDto.fullName;
                existing.role = userDto.role;
                existing.phone = userDto.phone;
                existing.email = userDto.email;
                existing.backendId = userDto.id;
                existing.failedLoginAttempts = 0;
                existing.lockedUntil = 0;
                if (userDto.terminalId != null) existing.terminalId = userDto.terminalId;
                if (userDto.serverIp != null) existing.serverIp = userDto.serverIp;
                if (userDto.serverPort != null) existing.serverPort = userDto.serverPort;
                dao.update(existing);
            } else {
                UserEntity newUser = new UserEntity(
                        usernameHash, passwordHash,
                        userDto.fullName, userDto.role,
                        userDto.email, userDto.phone, null);
                newUser.backendId = userDto.id;
                if (userDto.terminalId != null) newUser.terminalId = userDto.terminalId;
                if (userDto.serverIp != null) newUser.serverIp = userDto.serverIp;
                if (userDto.serverPort != null) newUser.serverPort = userDto.serverPort;
                dao.insert(newUser);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to cache user locally: " + e.getMessage());
        }
    }

    @Override
    public long resolveLocalUserId(String username, long backendId) {
        try {
            UserEntity user = findUser(username);
            if (user == null) {
                user = findByBackendId(backendId);
            }
            if (user != null) return user.id;
        } catch (Exception e) {
            Log.w(TAG, "Failed to resolve local user ID: " + e.getMessage());
        }
        return backendId; // fallback
    }

    @Override
    public long getLockRemainingMillis(UserEntity user) {
        if (user == null) return 0;
        long remaining = user.lockedUntil - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }

    @Override
    public void incrementFailedAttempts(UserEntity user) {
        if (user == null) return;
        user.failedLoginAttempts++;
        if (user.failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            user.lockedUntil = System.currentTimeMillis() + LOCK_DURATION_MS;
            user.failedLoginAttempts = 0;
        }
        db.userDao().update(user);
    }

    @Override
    public void resetFailedAttempts(UserEntity user) {
        if (user == null) return;
        user.failedLoginAttempts = 0;
        user.lockedUntil = 0;
        db.userDao().update(user);
    }
}

