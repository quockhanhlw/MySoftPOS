package com.example.mysoftpos.ui.auth;

import com.example.mysoftpos.R;
import com.example.mysoftpos.utils.mcc.BusinessTypeMccMapper;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import androidx.appcompat.content.res.AppCompatResources;
import com.example.mysoftpos.ui.BaseActivity;

import java.util.Locale;

public class LoginActivity extends BaseActivity {

    private static final String EXTRA_PASSWORD_CHANGED_RELOGIN = "PASSWORD_CHANGED_RELOGIN";
    public static final String EXTRA_PREFILL_IDENTIFIER = "PREFILL_IDENTIFIER";

    private EditText etUsername;
    private EditText etPassword;
    private boolean passwordVisible = false;
    private View loadingOverlay;

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        String prefillIdentifier = getIntent().getStringExtra(EXTRA_PREFILL_IDENTIFIER);
        if (prefillIdentifier != null && !prefillIdentifier.trim().isEmpty()) {
            etUsername.setText(prefillIdentifier.trim());
            etPassword.requestFocus();
        }

        // Password toggle
        etPassword.setCompoundDrawablesRelativeWithIntrinsicBounds(
                etPassword.getCompoundDrawablesRelative()[0], null,
                getPasswordToggleDrawable(R.drawable.ic_baseline_visibility_off_24), null);
        etPassword.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                Drawable end = etPassword.getCompoundDrawablesRelative()[2];
                if (end != null && event
                        .getRawX() >= (etPassword.getRight() - end.getBounds().width() - etPassword.getPaddingEnd())) {
                    v.performClick();
                    passwordVisible = !passwordVisible;
                    if (passwordVisible) {
                        etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                        etPassword.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                etPassword.getCompoundDrawablesRelative()[0], null,
                                getPasswordToggleDrawable(R.drawable.ic_baseline_visibility_24), null);
                    } else {
                        etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        etPassword.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                etPassword.getCompoundDrawablesRelative()[0], null,
                                getPasswordToggleDrawable(R.drawable.ic_baseline_visibility_off_24), null);
                    }
                    etPassword.setSelection(etPassword.getText().length());
                    return true;
                }
            }
            return false;
        });

        TextView tvForgotPassword = findViewById(R.id.tvForgotPassword);
        View btnLogin = findViewById(R.id.btnLogin);
        View btnBack = findViewById(R.id.btnBack);
        TextView tvSignUp = findViewById(R.id.tvSignUp);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        tvForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        if (tvSignUp != null) {
            tvSignUp.setOnClickListener(v -> {
                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
            });
        }

        // Language toggle
        View btnLanguageToggle = findViewById(R.id.btnLanguageToggle);
        if (btnLanguageToggle != null) {
            btnLanguageToggle.setOnClickListener(v -> {
                String current = com.example.mysoftpos.utils.LocaleHelper.getLanguage(this);
                String next = "vi".equals(current) ? "en" : "vi";
                com.example.mysoftpos.utils.LocaleHelper.setLocale(getApplicationContext(), next);
                recreate();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        }

        if (getIntent().getBooleanExtra(EXTRA_PASSWORD_CHANGED_RELOGIN, false)) {
            Toast.makeText(this, R.string.settings_password_relogin_required, Toast.LENGTH_SHORT).show();
            getIntent().removeExtra(EXTRA_PASSWORD_CHANGED_RELOGIN);
        }

        btnLogin.setOnClickListener(v -> handleLogin());
    }

    private void showLoading() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoading() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
    }

    private void handleLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String normalizedIdentifier = normalizeIdentifierForLogin(username);

        if (normalizedIdentifier.isEmpty()) {
            etUsername.setError(getString(R.string.login_error_enter_username));
            etUsername.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError(getString(R.string.login_error_enter_password));
            etPassword.requestFocus();
            return;
        }

        if (getIntent().getBooleanExtra("SESSION_TIMEOUT", false)) {
            Toast.makeText(this, R.string.login_session_expired, Toast.LENGTH_SHORT).show();
            getIntent().removeExtra("SESSION_TIMEOUT");
        }

        showLoading();
        boolean networkAvailable = isNetworkAvailable();
        loginViaLocalRoom(normalizedIdentifier, password, networkAvailable);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(
                android.content.Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkCapabilities capabilities = connectivityManager
                    .getNetworkCapabilities(connectivityManager.getActiveNetwork());
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
        }
        return false;
    }

    /**
     * Background sync with backend API after successful local login.
     * Refreshes JWT token, updates local cache, syncs transactions.
     */
    private void syncWithBackendInBackground(String identifier, String password) {
        try {
            com.example.mysoftpos.data.remote.api.ApiService api = com.example.mysoftpos.data.remote.api.ApiClient
                    .getAuthService(this);

            api.login(new com.example.mysoftpos.data.remote.api.ApiService.LoginRequest(identifier, password))
                    .enqueue(new retrofit2.Callback<>() {
                        @Override
                        public void onResponse(
                                retrofit2.Call<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse> call,
                                retrofit2.Response<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                com.example.mysoftpos.data.remote.api.ApiService.LoginResponse resp = response.body();
                                com.example.mysoftpos.data.remote.api.ApiClient.saveUserSession(LoginActivity.this,
                                        resp);
                                com.example.mysoftpos.utils.config.ConfigManager
                                        .getInstance(LoginActivity.this)
                                        .setMcc18(resp.user != null ? resp.user.businessType : null);
                                if (resp.user != null && resp.user.bankName != null && !resp.user.bankName.trim().isEmpty()) {
                                    com.example.mysoftpos.utils.config.ConfigManager
                                            .getInstance(LoginActivity.this)
                                            .setBankName(resp.user.bankName);
                                }
                                if (resp.user != null && resp.user.merchantCode != null
                                        && resp.user.merchantCode.matches("^[A-Z0-9]{15}$")) {
                                    com.example.mysoftpos.utils.config.ConfigManager
                                            .getInstance(LoginActivity.this)
                                            .setMerchantId(resp.user.merchantCode);
                                }
                                com.example.mysoftpos.di.ServiceLocator.getInstance(LoginActivity.this)
                                        .getDispatcherProvider().io().execute(() -> {
                                            cacheUserLocallySync(identifier, password, resp.user);
                                            if ("ADMIN".equals(resp.user.role)) {
                                                new com.example.mysoftpos.data.remote.ConfigSyncManager(
                                                        LoginActivity.this).sync();
                                                new com.example.mysoftpos.data.remote.TestSuiteSyncManager(
                                                        LoginActivity.this).pull();
                                            }
                                            new com.example.mysoftpos.data.remote.TransactionSyncManager(
                                                    LoginActivity.this).syncUnsynced();
                                        });
                            }
                        }

                        @Override
                        public void onFailure(
                                retrofit2.Call<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse> call,
                                Throwable t) {
                            android.util.Log.d("LoginActivity", "Background sync skipped: " + t.getMessage());
                        }
                    });
        } catch (Exception e) {
            android.util.Log.w("LoginActivity", "Background sync error: " + e.getMessage());
        }
    }

    // ====================================================================
    // PRIMARY: Backend API login via Retrofit
    // ====================================================================
    private void loginViaApi(String identifier, String password) {
        com.example.mysoftpos.data.remote.api.ApiService api = com.example.mysoftpos.data.remote.api.ApiClient
                .getAuthService(this);

        api.login(new com.example.mysoftpos.data.remote.api.ApiService.LoginRequest(identifier, password))
                .enqueue(new retrofit2.Callback<>() {
                    @Override
                    public void onResponse(
                            retrofit2.Call<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse> call,
                            retrofit2.Response<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse> response) {
                        if (isDestroyed() || isFinishing())
                            return;

                        if (response.isSuccessful() && response.body() != null) {
                            com.example.mysoftpos.data.remote.api.ApiService.LoginResponse resp = response.body();
                            com.example.mysoftpos.data.remote.api.ApiClient.saveUserSession(LoginActivity.this, resp);
                            com.example.mysoftpos.utils.security.SessionManager.startSession();
                            com.example.mysoftpos.utils.security.AuditLogger.log(
                                    LoginActivity.this, identifier, "LOGIN",
                                    true, "LoginActivity", "API login: " + resp.user.role);

                            // Set ConfigManager IP/Port/TID for NAPAS connection
                            com.example.mysoftpos.utils.config.ConfigManager config = com.example.mysoftpos.utils.config.ConfigManager
                                    .getInstance(LoginActivity.this);
                            config.resetServerConfig();
                            if (resp.user.serverIp != null && !resp.user.serverIp.isEmpty()
                                    && resp.user.serverPort != null && resp.user.serverPort > 0) {
                                config.setServerIp(resp.user.serverIp);
                                config.setServerPort(resp.user.serverPort);
                            }
                            if (resp.user.merchantCode != null && resp.user.merchantCode.matches("^[A-Z0-9]{15}$")) {
                                config.setMerchantId(resp.user.merchantCode);
                            }
                            if (resp.user.bankName != null && !resp.user.bankName.trim().isEmpty()) {
                                config.setBankName(resp.user.bankName);
                            }
                            // Set user-specific Terminal ID
                            if (resp.user.terminalId != null && !resp.user.terminalId.isEmpty()) {
                                config.setTerminalId(resp.user.terminalId);
                            }
                            config.setMcc18(resp.user.businessType);

                            // Cache user locally for offline login, then resolve local ID and navigate
                            com.example.mysoftpos.di.ServiceLocator.getInstance(LoginActivity.this)
                                    .getDispatcherProvider().io().execute(() -> {
                                        // Cache user to local Room DB
                                        cacheUserLocallySync(identifier, password, resp.user);

                                        // Resolve local Room user ID (not backend ID)
                                        long localUserId = resolveLocalUserId(identifier, resp.user.id);

                                        // Sync config & transactions from backend (non-blocking)
                                        if ("ADMIN".equals(resp.user.role)) {
                                            new com.example.mysoftpos.data.remote.ConfigSyncManager(LoginActivity.this)
                                                    .sync();
                                            new com.example.mysoftpos.data.remote.TestSuiteSyncManager(
                                                    LoginActivity.this).pull();
                                        }
                                        new com.example.mysoftpos.data.remote.TransactionSyncManager(LoginActivity.this)
                                                .syncUnsynced();

                                        runOnUiThread(() -> {
                                            hideLoading();
                                            if (isDestroyed() || isFinishing())
                                                return;
                                            navigateToDashboard(localUserId, resp.user.role,
                                                    resp.user.fullName != null ? resp.user.fullName
                                                            : getString(R.string.common_user),
                                                    resp.user.phone, resp.user.email);
                                        });
                                    });
                        } else {
                            String errorMsg = getString(R.string.login_invalid_credentials);
                            try (okhttp3.ResponseBody errorBody = response.errorBody()) {
                                if (errorBody != null) {
                                    String body = errorBody.string();
                                    if (body.contains("locked")) {
                                        errorMsg = getString(R.string.login_account_locked);
                                    }
                                }
                            } catch (Exception ignored) {
                            }

                            com.example.mysoftpos.utils.security.AuditLogger.log(
                                    LoginActivity.this, identifier, "LOGIN_FAILED",
                                    false, "LoginActivity", "API: " + response.code());

                            String finalMsg = errorMsg;
                            runOnUiThread(() -> {
                                hideLoading();
                                Toast.makeText(LoginActivity.this, finalMsg, Toast.LENGTH_SHORT).show();
                                etPassword.setText("");
                            });
                        }
                    }

                    @Override
                    public void onFailure(
                            retrofit2.Call<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse> call,
                            Throwable t) {
                        if (isDestroyed() || isFinishing())
                            return;
                        android.util.Log.w("LoginActivity",
                                "API unreachable, falling back to offline login: " + t.getMessage());
                        loginViaLocalRoom(identifier, password, false);
                    }
                });
    }

    // ====================================================================
    // LOCAL-FIRST: cached Room login with optional API fallback
    // ====================================================================
    private void loginViaLocalRoom(String identifier, String password, boolean allowApiFallback) {
        com.example.mysoftpos.di.ServiceLocator.getInstance(this)
                .getDispatcherProvider().io().execute(() -> {
                    try {
                        com.example.mysoftpos.utils.config.ConfigManager config = com.example.mysoftpos.utils.config.ConfigManager
                                .getInstance(LoginActivity.this);
                        com.example.mysoftpos.data.local.AppDatabase db = com.example.mysoftpos.data.local.AppDatabase
                                .getInstance(LoginActivity.this);
                        com.example.mysoftpos.data.local.dao.UserDao userDao = db.userDao();

                        com.example.mysoftpos.data.local.entity.UserEntity user = findLocalUser(userDao, identifier);

                        if (user != null) {
                            if (requiresFirstLoginOnline(user)) {
                                if (allowApiFallback) {
                                    runOnUiThread(() -> loginViaApi(identifier, password));
                                } else {
                                    runOnUiThread(() -> {
                                        hideLoading();
                                        if (!isDestroyed() && !isFinishing()) {
                                            Toast.makeText(LoginActivity.this,
                                                    R.string.login_first_online_required,
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                                return;
                            }

                            if (user.lockedUntil > System.currentTimeMillis()) {
                                int min = (int) ((user.lockedUntil - System.currentTimeMillis()) / 60000) + 1;
                                runOnUiThread(() -> {
                                    hideLoading();
                                    if (!isDestroyed() && !isFinishing())
                                        Toast.makeText(LoginActivity.this,
                                                getString(R.string.login_account_locked_try_again, min),
                                                Toast.LENGTH_LONG).show();
                                });
                                return;
                            }

                            if (com.example.mysoftpos.utils.security.PasswordUtils
                                    .verifyPassword(password, user.passwordHash)) {
                                user.failedLoginAttempts = 0;
                                user.lockedUntil = 0;

                                if (!user.passwordHash.contains(":")) {
                                    user.passwordHash = com.example.mysoftpos.utils.security.PasswordUtils
                                            .hashPassword(password);
                                }

                                userDao.update(user);

                                String displayName = user.displayName != null ? user.displayName
                                        : getString(R.string.common_user);
                                config.resetServerConfig();
                                if (user.serverIp != null && !user.serverIp.isEmpty() && user.serverPort > 0) {
                                    config.setServerIp(user.serverIp);
                                    config.setServerPort(user.serverPort);
                                }
                                if (user.terminalId != null && !user.terminalId.isEmpty()) {
                                    config.setTerminalId(user.terminalId);
                                }
                                config.setMcc18(resolveLocalBusinessType(db.merchantDao(), user));

                                com.example.mysoftpos.utils.security.SessionManager.startSession();
                                com.example.mysoftpos.utils.security.AuditLogger.log(
                                        LoginActivity.this, identifier, "LOGIN",
                                        true, "LoginActivity", "Local login: " + user.role);

                                final com.example.mysoftpos.data.local.entity.UserEntity finalUser = user;
                                runOnUiThread(() -> {
                                    hideLoading();
                                    if (!isDestroyed() && !isFinishing()) {
                                        Toast.makeText(LoginActivity.this,
                                                R.string.login_success, Toast.LENGTH_SHORT).show();
                                        navigateToDashboard(finalUser.id, finalUser.role, displayName,
                                                finalUser.phone, finalUser.email);
                                    }
                                });

                                if (allowApiFallback) {
                                    syncWithBackendInBackground(identifier, password);
                                }
                                return;
                            } else {
                                user.failedLoginAttempts++;
                                if (user.failedLoginAttempts >= 6) {
                                    user.lockedUntil = System.currentTimeMillis() + (30 * 60 * 1000L);
                                    user.failedLoginAttempts = 0;
                                }
                                userDao.update(user);
                            }
                        }

                        if (allowApiFallback) {
                            runOnUiThread(() -> loginViaApi(identifier, password));
                            return;
                        }

                        runOnUiThread(() -> {
                            hideLoading();
                            if (!isDestroyed() && !isFinishing()) {
                                Toast.makeText(LoginActivity.this,
                                        R.string.login_server_unavailable_offline_not_found,
                                        Toast.LENGTH_LONG).show();
                                etPassword.setText("");
                            }
                        });
                    } catch (Exception e) {
                        if (allowApiFallback) {
                            runOnUiThread(() -> loginViaApi(identifier, password));
                            return;
                        }
                        runOnUiThread(() -> {
                            hideLoading();
                            if (!isDestroyed() && !isFinishing())
                                Toast.makeText(LoginActivity.this,
                                        getString(R.string.login_error_with_reason, e.getMessage()), Toast.LENGTH_SHORT)
                                        .show();
                        });
                    }
                });
    }

    /**
     * Admin-created users are cached locally without a usable password hash,
     * so their first successful login must go through backend authentication.
     */
    private boolean requiresFirstLoginOnline(com.example.mysoftpos.data.local.entity.UserEntity user) {
        if (user == null) {
            return false;
        }
        boolean hasLocalPassword = user.passwordHash != null && !user.passwordHash.trim().isEmpty();
        return !hasLocalPassword && user.backendId > 0 && "USER".equalsIgnoreCase(user.role);
    }

    private void navigateToDashboard(long userId, String role, String displayName, String phone, String email) {
        Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(LoginActivity.this, com.example.mysoftpos.ui.dashboard.MainDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USER_ROLE, role);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USER_ID, userId);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME, phone != null ? phone : email);
        intent.putExtra("DISPLAY_NAME", displayName);
        intent.putExtra("USER_EMAIL", email);
        startActivity(intent);
        finish();
    }

    /**
     * Synchronous version: cache user locally. Must be called from IO thread.
     */
    private void cacheUserLocallySync(String username, String password,
            com.example.mysoftpos.data.remote.api.ApiService.UserDto userDto) {
        try {
            com.example.mysoftpos.data.local.AppDatabase db = com.example.mysoftpos.data.local.AppDatabase
                    .getInstance(LoginActivity.this);
            com.example.mysoftpos.data.local.dao.UserDao userDao = db.userDao();
            com.example.mysoftpos.data.local.dao.MerchantDao merchantDao = db.merchantDao();

            String resolvedUsername = safeText(userDto.username).isEmpty() ? username : safeText(userDto.username);
            String usernameHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(resolvedUsername);
            String passwordHash = com.example.mysoftpos.utils.security.PasswordUtils.hashPassword(password);

            com.example.mysoftpos.data.local.entity.UserEntity existing = userDao.findByUsername(resolvedUsername);
            if (existing == null) {
                existing = userDao.findByUsernameHash(usernameHash);
            }
            if (existing == null) {
                if (userDto.email != null)
                    existing = userDao.findByEmail(userDto.email);
            }

            String normalizedBusinessType = BusinessTypeMccMapper.toMcc(userDto.businessType);

            if (existing != null) {
                existing.username = resolvedUsername;
                existing.usernameHash = usernameHash;
                existing.passwordHash = passwordHash;
                existing.displayName = userDto.fullName;
                existing.role = userDto.role;
                existing.phone = userDto.phone;
                existing.email = userDto.email;
                existing.dob = userDto.dob;
                existing.gender = safeText(userDto.gender);
                existing.merchantBackendId = userDto.merchantId != null ? userDto.merchantId : 0L;
                existing.branchBackendId = userDto.branchId != null ? userDto.branchId : 0L;
                existing.phoneVerified = Boolean.TRUE.equals(userDto.phoneVerified);
                existing.backendId = userDto.id;
                existing.failedLoginAttempts = 0;
                existing.lockedUntil = 0;
                if (userDto.terminalId != null)
                    existing.terminalId = userDto.terminalId;
                if (userDto.serverIp != null)
                    existing.serverIp = userDto.serverIp;
                if (userDto.serverPort != null)
                    existing.serverPort = userDto.serverPort;
                userDao.update(existing);
            } else {
                com.example.mysoftpos.data.local.entity.UserEntity newUser = new com.example.mysoftpos.data.local.entity.UserEntity(
                        usernameHash, passwordHash,
                        userDto.fullName, userDto.role,
                        userDto.email, userDto.phone, userDto.dob);
                newUser.username = resolvedUsername;
                newUser.gender = safeText(userDto.gender);
                newUser.merchantBackendId = userDto.merchantId != null ? userDto.merchantId : 0L;
                newUser.branchBackendId = userDto.branchId != null ? userDto.branchId : 0L;
                newUser.phoneVerified = Boolean.TRUE.equals(userDto.phoneVerified);
                newUser.backendId = userDto.id;
                if (userDto.terminalId != null)
                    newUser.terminalId = userDto.terminalId;
                if (userDto.serverIp != null)
                    newUser.serverIp = userDto.serverIp;
                if (userDto.serverPort != null)
                    newUser.serverPort = userDto.serverPort;
                userDao.insert(newUser);
            }

            if (userDto.merchantId != null && userDto.merchantId > 0) {
                com.example.mysoftpos.data.local.entity.MerchantEntity merchant = merchantDao.getByBackendId(userDto.merchantId);
                if (merchant == null) {
                    merchant = new com.example.mysoftpos.data.local.entity.MerchantEntity();
                }
                merchant.backendId = userDto.merchantId;
                merchant.merchantCode = safeText(userDto.merchantCode);
                merchant.merchantNameLocation = safeText(userDto.storeName);
                merchant.businessType = normalizedBusinessType;
                merchant.storeAddress = safeText(userDto.storeAddress);
                merchant.bankName = safeText(userDto.bankName);
                if (merchant.id > 0) {
                    merchantDao.update(merchant);
                } else {
                    merchantDao.insert(merchant);
                }
            }
        } catch (Exception e) {
            android.util.Log.w("LoginActivity",
                    "Failed to cache user locally: " + e.getMessage());
        }
    }

    private String safeText(String value) {
        return value != null ? value : "";
    }

    private String resolveLocalBusinessType(com.example.mysoftpos.data.local.dao.MerchantDao merchantDao,
                                            com.example.mysoftpos.data.local.entity.UserEntity user) {
        if (merchantDao == null || user == null || user.merchantBackendId <= 0) {
            return null;
        }
        com.example.mysoftpos.data.local.entity.MerchantEntity merchant = merchantDao.getByBackendId(user.merchantBackendId);
        return merchant != null ? merchant.businessType : null;
    }

    private com.example.mysoftpos.data.local.entity.UserEntity findLocalUser(
            com.example.mysoftpos.data.local.dao.UserDao userDao,
            String identifier) {
        String normalized = normalizeIdentifierForLogin(identifier);
        com.example.mysoftpos.data.local.entity.UserEntity user = null;

        String normalizedHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(normalized);
        user = userDao.findByUsername(normalized);
        if (user == null) {
            user = userDao.findByUsernameHash(normalizedHash);
        }
        if (user == null && !normalized.equals(identifier)) {
            String legacyHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(identifier);
            user = userDao.findByUsernameHash(legacyHash);
        }
        if (user == null && normalized.contains("@")) {
            user = userDao.findByEmail(normalized);
            if (user == null && !normalized.equals(identifier)) {
                user = userDao.findByEmail(identifier);
            }
        }
        return user;
    }

    /**
     * Resolve the local Room user ID for a given username.
     * Falls back to backend ID if local user not found.
     * Must be called from IO thread.
     */
    private long resolveLocalUserId(String username, long backendId) {
        try {
            com.example.mysoftpos.data.local.AppDatabase db = com.example.mysoftpos.data.local.AppDatabase
                    .getInstance(LoginActivity.this);
            com.example.mysoftpos.data.local.dao.UserDao userDao = db.userDao();

            com.example.mysoftpos.data.local.entity.UserEntity user = findLocalUser(userDao, username);
            // Last resort: find by backendId
            if (user == null)
                user = userDao.findByBackendId(backendId);

            if (user != null)
                return user.id;
        } catch (Exception e) {
            android.util.Log.w("LoginActivity", "Failed to resolve local user ID: " + e.getMessage());
        }
        return backendId; // fallback
    }

    private String normalizeIdentifierForLogin(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.contains("@")) {
            return normalized.toLowerCase(Locale.ROOT);
        }
        normalized = normalized.replaceAll("[\\s()-]", "");
        if (normalized.startsWith("00")) {
            normalized = "+" + normalized.substring(2);
        }
        if (normalized.indexOf('+') > 0) {
            normalized = normalized.replace("+", "");
        }
        return normalized;
    }

    private Drawable getPasswordToggleDrawable(int drawableResId) {
        return AppCompatResources.getDrawable(this, drawableResId);
    }
}
