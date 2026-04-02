package com.example.mysoftpos.data.repository;

import android.util.Log;

import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.dao.MerchantDao;
import com.example.mysoftpos.data.local.dao.PosAccountDao;
import com.example.mysoftpos.data.local.entity.MerchantEntity;
import com.example.mysoftpos.data.local.entity.PosAccountEntity;
import com.example.mysoftpos.data.remote.api.ApiService;
import com.example.mysoftpos.utils.mcc.BusinessTypeMccMapper;
import com.example.mysoftpos.utils.security.PasswordUtils;

/**
 * Concrete implementation of {@link PosAccountRepository}.
 * Extracted from LoginActivity to follow single-responsibility principle.
 * All public methods must be called from IO thread.
 */
public class PosAccountRepositoryImpl implements PosAccountRepository {

    private static final String TAG = "UserRepo";
    private static final int MAX_FAILED_ATTEMPTS = 6;
    private static final long LOCK_DURATION_MS = 30 * 60 * 1000L; // 30 min

    private final AppDatabase db;

    public PosAccountRepositoryImpl(AppDatabase db) {
        this.db = db;
    }

    @Override
    public PosAccountEntity findUser(String identifier) {
        if (identifier == null) return null;
        PosAccountDao dao = db.posAccountDao();

        String normalized = identifier.trim();

        PosAccountEntity user = dao.findByUsername(normalized);
        if (user != null) return user;

        // Username is the primary login identity.
        user = dao.findByUsernameHash(PasswordUtils.hashSHA256(normalized));
        if (user != null) return user;
        return null;
    }

    @Override
    public PosAccountEntity findByBackendId(long backendId) {
        return db.posAccountDao().findByBackendId(backendId);
    }

    @Override
    public void cacheUser(String username, String password, ApiService.PosAccountDto userDto) {
        try {
            PosAccountDao dao = db.posAccountDao();
            MerchantDao merchantDao = db.merchantDao();
            String resolvedUsername = userDto.username != null ? userDto.username : username;
            String usernameHash = PasswordUtils.hashSHA256(resolvedUsername);
            String passwordHash = (password != null && !password.trim().isEmpty())
                    ? PasswordUtils.hashPassword(password)
                    : "";
            String normalizedBusinessType = BusinessTypeMccMapper.toMcc(userDto.businessType);

            PosAccountEntity existing = dao.findByUsername(resolvedUsername);
            if (existing == null) {
                existing = dao.findByUsernameHash(usernameHash);
            }

            if (existing != null) {
                existing.username = resolvedUsername;
                existing.usernameHash = usernameHash;
                if (!passwordHash.isEmpty()) {
                    existing.passwordHash = passwordHash;
                }
                existing.role = userDto.role;
                existing.merchantBackendId = userDto.merchantId != null ? userDto.merchantId : 0L;
                existing.branchBackendId = userDto.branchId != null ? userDto.branchId : 0L;
                existing.phoneVerified = Boolean.TRUE.equals(userDto.phoneVerified);
                existing.backendId = userDto.id;
                existing.failedLoginAttempts = 0;
                existing.lockedUntil = 0;
                if (userDto.terminalId != null) existing.terminalId = userDto.terminalId;
                dao.update(existing);
            } else {
                PosAccountEntity newUser = new PosAccountEntity(usernameHash,
                        passwordHash,
                        userDto.role != null ? userDto.role : "USER");
                newUser.username = resolvedUsername;
                newUser.merchantBackendId = userDto.merchantId != null ? userDto.merchantId : 0L;
                newUser.branchBackendId = userDto.branchId != null ? userDto.branchId : 0L;
                newUser.phoneVerified = Boolean.TRUE.equals(userDto.phoneVerified);
                newUser.backendId = userDto.id;
                if (userDto.terminalId != null) newUser.terminalId = userDto.terminalId;
                dao.insert(newUser);
            }

            if (userDto.merchantId != null && userDto.merchantId > 0) {
                MerchantEntity merchant = merchantDao.getByBackendId(userDto.merchantId);
                if (merchant == null) {
                    merchant = new MerchantEntity();
                }
                merchant.backendId = userDto.merchantId;
                merchant.merchantCode = safeText(userDto.merchantCode);
                merchant.merchantName = safeText(userDto.storeName);
                merchant.fullName = safeText(userDto.fullName);
                merchant.phone = safeText(userDto.phone);
                merchant.email = safeText(userDto.email);
                merchant.dob = safeText(userDto.dob);
                merchant.gender = safeText(userDto.gender);
                merchant.businessType = normalizedBusinessType;
                merchant.storeAddress = safeText(userDto.storeAddress);
                merchant.bankName = safeText(userDto.bankName);
                merchant.ownerUserBackendId = userDto.id;
                if (merchant.id > 0) {
                    merchantDao.update(merchant);
                } else {
                    merchantDao.insert(merchant);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to cache user locally: " + e.getMessage());
        }
    }

    private String safeText(String value) {
        return value != null ? value : "";
    }

    @Override
    public long resolveLocalUserId(String username, long backendId) {
        try {
            PosAccountEntity user = findUser(username);
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
    public long getLockRemainingMillis(PosAccountEntity user) {
        if (user == null) return 0;
        long remaining = user.lockedUntil - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }

    @Override
    public void incrementFailedAttempts(PosAccountEntity user) {
        if (user == null) return;
        user.failedLoginAttempts++;
        if (user.failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            user.lockedUntil = System.currentTimeMillis() + LOCK_DURATION_MS;
            user.failedLoginAttempts = 0;
        }
        db.posAccountDao().update(user);
    }

    @Override
    public void resetFailedAttempts(PosAccountEntity user) {
        if (user == null) return;
        user.failedLoginAttempts = 0;
        user.lockedUntil = 0;
        db.posAccountDao().update(user);
    }

    @Override
    public String resolveBusinessType(PosAccountEntity user) {
        if (user == null || user.merchantBackendId <= 0) {
            return null;
        }
        MerchantEntity merchant = db.merchantDao().getByBackendId(user.merchantBackendId);
        return merchant != null ? merchant.businessType : null;
    }

    @Override
    public String resolveDisplayName(PosAccountEntity user) {
        MerchantEntity merchant = resolveMerchant(user);
        if (merchant == null) {
            return null;
        }
        if (merchant.fullName != null && !merchant.fullName.trim().isEmpty()) {
            return merchant.fullName;
        }
        if (merchant.merchantName != null && !merchant.merchantName.trim().isEmpty()) {
            return merchant.merchantName;
        }
        return null;
    }

    @Override
    public String resolveContactPhone(PosAccountEntity user) {
        MerchantEntity merchant = resolveMerchant(user);
        return merchant != null ? merchant.phone : null;
    }

    @Override
    public String resolveContactEmail(PosAccountEntity user) {
        MerchantEntity merchant = resolveMerchant(user);
        return merchant != null ? merchant.email : null;
    }

    private MerchantEntity resolveMerchant(PosAccountEntity user) {
        if (user == null) {
            return null;
        }
        MerchantDao merchantDao = db.merchantDao();
        if (user.merchantBackendId > 0) {
            MerchantEntity merchant = merchantDao.getByBackendId(user.merchantBackendId);
            if (merchant != null) {
                return merchant;
            }
        }
        if (user.backendId > 0) {
            return merchantDao.getByOwnerUserBackendId(user.backendId);
        }
        return null;
    }
}

