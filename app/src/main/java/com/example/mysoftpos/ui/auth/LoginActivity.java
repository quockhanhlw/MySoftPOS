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
        com.example.mysoftpos.data.local.LocalDataBootstrapper.runIfNeeded(this);

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
        // Local-first: allow cached users to login instantly even when offline.
        loginViaLocalRoom(normalizedIdentifier, password, true);
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
                                com.example.mysoftpos.data.remote.api.ApiService.PosAccountDto effectiveUser = resolveEffectiveUser(resp);
                                if (effectiveUser == null) {
                                    return;
                                }
                                com.example.mysoftpos.data.remote.api.ApiClient.saveUserSession(LoginActivity.this,
                                        resp);
                                applyServerConfigFromUser(effectiveUser);
                                com.example.mysoftpos.utils.config.ConfigManager
                                        .getInstance(LoginActivity.this)
                                        .setMcc18(effectiveUser.businessType);
                                if (effectiveUser.bankName != null && !effectiveUser.bankName.trim().isEmpty()) {
                                    com.example.mysoftpos.utils.config.ConfigManager
                                            .getInstance(LoginActivity.this)
                                            .setBankName(effectiveUser.bankName);
                                }
                                if (effectiveUser.merchantCode != null
                                        && effectiveUser.merchantCode.matches("^[A-Z0-9]{15}$")) {
                                    com.example.mysoftpos.utils.config.ConfigManager
                                            .getInstance(LoginActivity.this)
                                            .setMerchantId(effectiveUser.merchantCode);
                                }
                                final com.example.mysoftpos.data.remote.api.ApiService.PosAccountDto cachedUser = effectiveUser;
                                com.example.mysoftpos.di.ServiceLocator.getInstance(LoginActivity.this)
                                        .getDispatcherProvider().io().execute(() -> {
                                            cacheUserLocallySync(identifier, password, cachedUser);
                                            if ("ADMIN".equals(cachedUser.role)) {
                                                new com.example.mysoftpos.data.remote.ConfigSyncManager(
                                                        LoginActivity.this).sync();
                                                new com.example.mysoftpos.data.remote.TestSuiteSyncManager(
                                                        LoginActivity.this).push();
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
                            com.example.mysoftpos.data.remote.api.ApiService.PosAccountDto effectiveUser = resolveEffectiveUser(resp);
                            if (effectiveUser == null) {
                                hideLoading();
                                if (!isDestroyed() && !isFinishing()) {
                                    Toast.makeText(LoginActivity.this, R.string.login_invalid_credentials, Toast.LENGTH_SHORT).show();
                                }
                                return;
                            }
                            com.example.mysoftpos.utils.config.ConfigManager config = com.example.mysoftpos.utils.config.ConfigManager
                                    .getInstance(LoginActivity.this);
                            applyServerConfigFromUser(effectiveUser);
                            if ("USER".equalsIgnoreCase(effectiveUser.role) && !config.hasServerConnectionConfig()) {
                                hideLoading();
                                Toast.makeText(LoginActivity.this,
                                        R.string.err_server_not_configured,
                                        Toast.LENGTH_LONG).show();
                                return;
                            }

                            com.example.mysoftpos.data.remote.api.ApiClient.saveUserSession(LoginActivity.this, resp);
                            com.example.mysoftpos.utils.security.SessionManager.startSession();
                            com.example.mysoftpos.utils.security.AuditLogger.log(
                                    LoginActivity.this, identifier, "LOGIN",
                                    true, "LoginActivity", "API login: " + effectiveUser.role);

                            // Set ConfigManager IP/Port/TID for NAPAS connection
                            if (effectiveUser.merchantCode != null && effectiveUser.merchantCode.matches("^[A-Z0-9]{15}$")) {
                                config.setMerchantId(effectiveUser.merchantCode);
                            }
                            if (effectiveUser.bankName != null && !effectiveUser.bankName.trim().isEmpty()) {
                                config.setBankName(effectiveUser.bankName);
                            }
                            // Set user-specific Terminal ID
                            if (effectiveUser.terminalId != null && !effectiveUser.terminalId.isEmpty()) {
                                config.setTerminalId(effectiveUser.terminalId);
                            }
                            config.setMcc18(effectiveUser.businessType);

                            // Cache user locally for offline login, then resolve local ID and navigate
                            com.example.mysoftpos.di.ServiceLocator.getInstance(LoginActivity.this)
                                    .getDispatcherProvider().io().execute(() -> {
                                        final com.example.mysoftpos.data.remote.api.ApiService.PosAccountDto finalEffectiveUser = effectiveUser;
                                        // Cache user to local Room DB
                                        cacheUserLocallySync(identifier, password, finalEffectiveUser);

                                        // Resolve local Room user ID (not backend ID)
                                        long localUserId = resolveLocalUserId(identifier, finalEffectiveUser.id);

                                        // Sync config & transactions from backend (non-blocking)
                                        if ("ADMIN".equals(finalEffectiveUser.role)) {
                                            new com.example.mysoftpos.data.remote.ConfigSyncManager(LoginActivity.this)
                                                    .sync();
                                            new com.example.mysoftpos.data.remote.TestSuiteSyncManager(
                                                    LoginActivity.this).push();
                                        }
                                        new com.example.mysoftpos.data.remote.TransactionSyncManager(LoginActivity.this)
                                                .syncUnsynced();

                                        runOnUiThread(() -> {
                                            hideLoading();
                                            if (isDestroyed() || isFinishing())
                                                return;
                                            navigateToDashboard(localUserId, finalEffectiveUser.role,
                                                    finalEffectiveUser.fullName != null ? finalEffectiveUser.fullName
                                                            : getString(R.string.common_user),
                                                    finalEffectiveUser.phone, finalEffectiveUser.email);
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
                        android.util.Log.w("LoginActivity", "API login failed: " + t.getMessage());
                        hideLoading();
                        Toast.makeText(LoginActivity.this,
                                R.string.login_server_unavailable_offline_not_found,
                                Toast.LENGTH_LONG).show();
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
                        com.example.mysoftpos.data.local.dao.PosAccountDao posAccountDao = db.posAccountDao();

                        com.example.mysoftpos.data.local.entity.PosAccountEntity user = findLocalUser(posAccountDao, identifier);

                        if (user != null) {
                            if (requiresFirstLoginOnline(user)) {
                                if (allowApiFallback) {
                                    if (isNetworkAvailable()) {
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

                                posAccountDao.update(user);

                                String displayName = resolveLocalDisplayName(db.merchantDao(), user);
                                applyLocalConnectionConfig(config, db, user);
                                config.setMcc18(resolveLocalBusinessType(db.merchantDao(), user));

                                com.example.mysoftpos.utils.security.SessionManager.startSession();
                                com.example.mysoftpos.utils.security.AuditLogger.log(
                                        LoginActivity.this, identifier, "LOGIN",
                                        true, "LoginActivity", "Local login: " + user.role);

                                final com.example.mysoftpos.data.local.entity.PosAccountEntity finalUser = user;
                                runOnUiThread(() -> {
                                    hideLoading();
                                    if (!isDestroyed() && !isFinishing()) {
                                        Toast.makeText(LoginActivity.this,
                                                R.string.login_success, Toast.LENGTH_SHORT).show();
                                        navigateToDashboard(finalUser.id, finalUser.role, displayName,
                                                finalUser.username, "");
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
                                posAccountDao.update(user);
                            }
                        }

                        if (allowApiFallback) {
                            if (isNetworkAvailable()) {
                                runOnUiThread(() -> loginViaApi(identifier, password));
                            } else {
                                runOnUiThread(() -> {
                                    hideLoading();
                                    if (!isDestroyed() && !isFinishing()) {
                                        Toast.makeText(LoginActivity.this,
                                                R.string.login_server_unavailable_offline_not_found,
                                                Toast.LENGTH_LONG).show();
                                        etPassword.setText("");
                                    }
                                });
                            }
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
                            if (isNetworkAvailable()) {
                                runOnUiThread(() -> loginViaApi(identifier, password));
                            } else {
                                runOnUiThread(() -> {
                                    hideLoading();
                                    if (!isDestroyed() && !isFinishing()) {
                                        Toast.makeText(LoginActivity.this,
                                                R.string.login_server_unavailable_offline_not_found,
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
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
    private boolean requiresFirstLoginOnline(com.example.mysoftpos.data.local.entity.PosAccountEntity user) {
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
            com.example.mysoftpos.data.remote.api.ApiService.PosAccountDto userDto) {
        try {
            com.example.mysoftpos.data.local.AppDatabase db = com.example.mysoftpos.data.local.AppDatabase
                    .getInstance(LoginActivity.this);
            com.example.mysoftpos.data.local.dao.PosAccountDao posAccountDao = db.posAccountDao();
            com.example.mysoftpos.data.local.dao.MerchantDao merchantDao = db.merchantDao();

            String resolvedUsername = safeText(userDto.username).isEmpty() ? username : safeText(userDto.username);
            String usernameHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(resolvedUsername);
            String passwordHash = com.example.mysoftpos.utils.security.PasswordUtils.hashPassword(password);

            com.example.mysoftpos.data.local.entity.PosAccountEntity existing = posAccountDao.findByUsername(resolvedUsername);
            if (existing == null) {
                existing = posAccountDao.findByUsernameHash(usernameHash);
            }
            if (existing == null) {
                if (userDto.email != null)
                    existing = posAccountDao.findByUsername(userDto.email);
            }

            String normalizedBusinessType = BusinessTypeMccMapper.toMcc(userDto.businessType);

            if (existing != null) {
                existing.username = resolvedUsername;
                existing.usernameHash = usernameHash;
                existing.passwordHash = passwordHash;
                existing.role = userDto.role;
                existing.merchantBackendId = userDto.merchantId != null ? userDto.merchantId : 0L;
                existing.branchBackendId = userDto.branchId != null ? userDto.branchId : 0L;
                existing.phoneVerified = Boolean.TRUE.equals(userDto.phoneVerified);
                existing.backendId = userDto.id;
                existing.failedLoginAttempts = 0;
                existing.lockedUntil = 0;
                if (userDto.terminalId != null)
                    existing.terminalId = userDto.terminalId;
                posAccountDao.update(existing);
            } else {
                com.example.mysoftpos.data.local.entity.PosAccountEntity newUser = new com.example.mysoftpos.data.local.entity.PosAccountEntity(
                        usernameHash, passwordHash, userDto.role);
                newUser.username = resolvedUsername;
                newUser.merchantBackendId = userDto.merchantId != null ? userDto.merchantId : 0L;
                newUser.branchBackendId = userDto.branchId != null ? userDto.branchId : 0L;
                newUser.phoneVerified = Boolean.TRUE.equals(userDto.phoneVerified);
                newUser.backendId = userDto.id;
                if (userDto.terminalId != null)
                    newUser.terminalId = userDto.terminalId;
                posAccountDao.insert(newUser);
            }

            if (userDto.merchantId != null && userDto.merchantId > 0) {
                com.example.mysoftpos.data.local.entity.MerchantEntity merchant = merchantDao.getByBackendId(userDto.merchantId);
                if (merchant == null) {
                    merchant = new com.example.mysoftpos.data.local.entity.MerchantEntity();
                }
                merchant.backendId = userDto.merchantId;
                merchant.merchantCode = safeText(userDto.merchantCode);
                merchant.merchantName = safeText(userDto.storeName);
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
                                            com.example.mysoftpos.data.local.entity.PosAccountEntity user) {
        if (merchantDao == null || user == null || user.merchantBackendId <= 0) {
            return null;
        }
        com.example.mysoftpos.data.local.entity.MerchantEntity merchant = merchantDao.getByBackendId(user.merchantBackendId);
        return merchant != null ? merchant.businessType : null;
    }

    private String resolveLocalDisplayName(com.example.mysoftpos.data.local.dao.MerchantDao merchantDao,
                                           com.example.mysoftpos.data.local.entity.PosAccountEntity user) {
        if (user == null) {
            return getString(R.string.common_user);
        }

        com.example.mysoftpos.data.local.entity.MerchantEntity merchant = null;
        if (merchantDao != null) {
            if (user.merchantBackendId > 0) {
                merchant = merchantDao.getByBackendId(user.merchantBackendId);
            }
            if (merchant == null && user.backendId > 0) {
                merchant = merchantDao.getByOwnerUserBackendId(user.backendId);
            }
            if (merchant == null && user.username != null) {
                if (user.username.contains("@")) {
                    merchant = merchantDao.getByEmail(user.username);
                } else {
                    merchant = merchantDao.getByPhone(user.username);
                }
            }
        }

        if (merchant != null && merchant.fullName != null && !merchant.fullName.trim().isEmpty()) {
            return merchant.fullName.trim();
        }

        String username = user.username != null ? user.username.trim() : "";
        if (!username.isEmpty() && !looksLikeContact(username)) {
            return username;
        }

        return "ADMIN".equalsIgnoreCase(user.role)
                ? getString(R.string.dashboard_admin_label)
                : getString(R.string.common_user);
    }

    private boolean looksLikeContact(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        return v.contains("@") || v.matches("^[+]?\\d{8,15}$");
    }

    private com.example.mysoftpos.data.local.entity.PosAccountEntity findLocalUser(
            com.example.mysoftpos.data.local.dao.PosAccountDao posAccountDao,
            String identifier) {
        String normalized = normalizeIdentifierForLogin(identifier);
        com.example.mysoftpos.data.local.entity.PosAccountEntity user = null;

        String normalizedHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(normalized);
        user = posAccountDao.findByUsername(normalized);
        if (user == null) {
            user = posAccountDao.findByUsernameHash(normalizedHash);
        }
        if (user == null && !normalized.equals(identifier)) {
            String legacyHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(identifier);
            user = posAccountDao.findByUsernameHash(legacyHash);
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
            com.example.mysoftpos.data.local.dao.PosAccountDao posAccountDao = db.posAccountDao();

            com.example.mysoftpos.data.local.entity.PosAccountEntity user = findLocalUser(posAccountDao, username);
            // Last resort: find by backendId
            if (user == null)
                user = posAccountDao.findByBackendId(backendId);

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

    private void applyServerConfigFromUser(com.example.mysoftpos.data.remote.api.ApiService.PosAccountDto userDto) {
        com.example.mysoftpos.utils.config.ConfigManager config = com.example.mysoftpos.utils.config.ConfigManager
                .getInstance(LoginActivity.this);
        if (userDto == null) {
            return;
        }
        if (userDto.serverIp != null && !userDto.serverIp.trim().isEmpty()) {
            config.setServerIp(userDto.serverIp);
        }
        if (userDto.serverPort != null && userDto.serverPort > 0) {
            config.setServerPort(userDto.serverPort);
        }
        if (userDto.terminalId != null && !userDto.terminalId.trim().isEmpty()) {
            config.setTerminalId(userDto.terminalId);
        }
    }

    private void applyLocalConnectionConfig(com.example.mysoftpos.utils.config.ConfigManager config,
                                            com.example.mysoftpos.data.local.AppDatabase db,
                                            com.example.mysoftpos.data.local.entity.PosAccountEntity user) {
        if (config == null || db == null || user == null) {
            return;
        }

        if (user.terminalId != null && !user.terminalId.trim().isEmpty()) {
            config.setTerminalId(user.terminalId.trim());
        }

        try {
            com.example.mysoftpos.data.local.dao.TerminalDao terminalDao = db.terminalDao();
            com.example.mysoftpos.data.local.entity.TerminalEntity terminal = null;
            if (user.backendId > 0) {
                terminal = terminalDao.getByPosAccountBackendId(user.backendId);
            }
            if (terminal == null && user.terminalId != null && !user.terminalId.trim().isEmpty()) {
                terminal = terminalDao.getByCode(user.terminalId.trim());
            }
            if (terminal != null) {
                if (terminal.terminalCode != null && !terminal.terminalCode.trim().isEmpty()) {
                    config.setTerminalId(terminal.terminalCode.trim());
                }
                if (terminal.serverIp != null && !terminal.serverIp.trim().isEmpty()) {
                    config.setServerIp(terminal.serverIp.trim());
                }
                if (terminal.serverPort > 0) {
                    config.setServerPort(terminal.serverPort);
                }
            }
        } catch (Exception e) {
            android.util.Log.w("LoginActivity", "Local terminal config restore failed: " + e.getMessage());
        }

        try {
            com.example.mysoftpos.data.local.dao.MerchantDao merchantDao = db.merchantDao();
            com.example.mysoftpos.data.local.entity.MerchantEntity merchant = null;
            if (user.merchantBackendId > 0) {
                merchant = merchantDao.getByBackendId(user.merchantBackendId);
            }
            if (merchant != null) {
                if (merchant.merchantCode != null && merchant.merchantCode.matches("^[A-Z0-9]{15}$")) {
                    config.setMerchantId(merchant.merchantCode);
                }
                if (merchant.bankName != null && !merchant.bankName.trim().isEmpty()) {
                    config.setBankName(merchant.bankName);
                }
            }
        } catch (Exception e) {
            android.util.Log.w("LoginActivity", "Local merchant config restore failed: " + e.getMessage());
        }
    }

    private com.example.mysoftpos.data.remote.api.ApiService.PosAccountDto resolveEffectiveUser(
            com.example.mysoftpos.data.remote.api.ApiService.LoginResponse response) {
        if (response == null) {
            return null;
        }
        if (response.user != null) {
            return response.user;
        }
        return response.posAccount;
    }

    private Drawable getPasswordToggleDrawable(int drawableResId) {
        return AppCompatResources.getDrawable(this, drawableResId);
    }
}
