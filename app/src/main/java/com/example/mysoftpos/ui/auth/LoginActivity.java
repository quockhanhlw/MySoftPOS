package com.example.mysoftpos.ui.auth;

import com.example.mysoftpos.R;

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

import com.example.mysoftpos.ui.BaseActivity;

public class LoginActivity extends BaseActivity {

    private EditText etUsername;
    private EditText etPassword;
    private boolean passwordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);

        // Password toggle
        etPassword.setCompoundDrawablesRelativeWithIntrinsicBounds(
                etPassword.getCompoundDrawablesRelative()[0], null,
                getDrawable(R.drawable.ic_baseline_visibility_off_24), null);
        etPassword.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                Drawable end = etPassword.getCompoundDrawablesRelative()[2];
                if (end != null && event
                        .getRawX() >= (etPassword.getRight() - end.getBounds().width() - etPassword.getPaddingEnd())) {
                    passwordVisible = !passwordVisible;
                    if (passwordVisible) {
                        etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                        etPassword.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                etPassword.getCompoundDrawablesRelative()[0], null,
                                getDrawable(R.drawable.ic_baseline_visibility_24), null);
                    } else {
                        etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        etPassword.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                etPassword.getCompoundDrawablesRelative()[0], null,
                                getDrawable(R.drawable.ic_baseline_visibility_off_24), null);
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

        btnLogin.setOnClickListener(v -> handleLogin());
    }

    private void handleLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty()) {
            etUsername.setError("Please enter email or phone");
            etUsername.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError("Please enter your password");
            etPassword.requestFocus();
            return;
        }

        if (getIntent().getBooleanExtra("SESSION_TIMEOUT", false)) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
            getIntent().removeExtra("SESSION_TIMEOUT");
        }

        // LOCAL-FIRST: If user is cached in SQLite → login instantly, sync API in
        // background.
        // FIRST-TIME: If user not found locally → must go online via API.
        tryLocalFirstLogin(username, password);
    }

    /**
     * Local-first login strategy:
     * 1. Check SQLite cache → if user exists and password matches → instant login +
     * background API sync
     * 2. If user not found locally → fall through to online API login (first-time
     * login)
     */
    private void tryLocalFirstLogin(String username, String password) {
        com.example.mysoftpos.di.ServiceLocator.getInstance(this)
                .getDispatcherProvider().io().execute(() -> {
                    try {
                        com.example.mysoftpos.data.local.AppDatabase db = com.example.mysoftpos.data.local.AppDatabase
                                .getInstance(LoginActivity.this);
                        com.example.mysoftpos.data.local.dao.UserDao userDao = db.userDao();

                        // Find user in local cache
                        com.example.mysoftpos.data.local.entity.UserEntity user = userDao.findByPhone(username);
                        if (user == null)
                            user = userDao.findByEmail(username);
                        if (user == null) {
                            String hash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(username);
                            user = userDao.findByUsernameHash(hash);
                        }

                        if (user != null) {
                            // Check account lockout
                            if (user.lockedUntil > System.currentTimeMillis()) {
                                int min = (int) ((user.lockedUntil - System.currentTimeMillis()) / 60000) + 1;
                                runOnUiThread(() -> {
                                    if (!isDestroyed() && !isFinishing())
                                        Toast.makeText(LoginActivity.this,
                                                "Account locked. Try again in " + min + " minutes.",
                                                Toast.LENGTH_LONG).show();
                                });
                                return;
                            }

                            // Verify password against local cache
                            if (com.example.mysoftpos.utils.security.PasswordUtils
                                    .verifyPassword(password, user.passwordHash)) {
                                // ✅ LOCAL LOGIN SUCCESS — instant!
                                user.failedLoginAttempts = 0;
                                user.lockedUntil = 0;
                                userDao.update(user);

                                com.example.mysoftpos.utils.security.SessionManager.startSession();
                                com.example.mysoftpos.utils.security.AuditLogger.log(
                                        LoginActivity.this, username, "LOGIN",
                                        true, "LoginActivity", "Local-first login: " + user.role);

                                final com.example.mysoftpos.data.local.entity.UserEntity cachedUser = user;
                                // Background: sync with API to refresh token & update cache
                                // Pass a callback so we only navigate after we get the fresh token
                                syncWithBackendInBackground(username, password, () -> {
                                    runOnUiThread(() -> {
                                        if (!isDestroyed() && !isFinishing()) {
                                            String displayName = cachedUser.displayName != null ? cachedUser.displayName
                                                    : "User";

                                            // Set User IP/Port/TID to ConfigManager
                                            com.example.mysoftpos.utils.config.ConfigManager config = com.example.mysoftpos.utils.config.ConfigManager
                                                    .getInstance(LoginActivity.this);
                                            config.resetServerConfig();
                                            if (cachedUser.serverIp != null && !cachedUser.serverIp.isEmpty()
                                                    && cachedUser.serverPort > 0) {
                                                config.setServerIp(cachedUser.serverIp);
                                                config.setServerPort(cachedUser.serverPort);
                                            }
                                            if (cachedUser.terminalId != null && !cachedUser.terminalId.isEmpty()) {
                                                config.setTerminalId(cachedUser.terminalId);
                                            }

                                            navigateToDashboard(cachedUser.id, cachedUser.role, displayName,
                                                    cachedUser.phone, cachedUser.email);
                                        }
                                    });
                                });
                                return;
                            } else {
                                // Wrong password — increment failed attempts
                                user.failedLoginAttempts++;
                                if (user.failedLoginAttempts >= 6) {
                                    user.lockedUntil = System.currentTimeMillis() + (30 * 60 * 1000L);
                                    user.failedLoginAttempts = 0;
                                }
                                userDao.update(user);
                            }
                        }

                        // User not found locally OR wrong password → try API login (first-time or
                        // re-verify)
                        runOnUiThread(() -> {
                            if (!isDestroyed() && !isFinishing())
                                loginViaApi(username, password);
                        });
                    } catch (Exception e) {
                        // SQLite error → fallback to API
                        runOnUiThread(() -> {
                            if (!isDestroyed() && !isFinishing())
                                loginViaApi(username, password);
                        });
                    }
                });
    }

    /**
     * Background sync with backend API after successful local login.
     * Refreshes JWT token, updates local cache, syncs transactions.
     */
    private void syncWithBackendInBackground(String username, String password, Runnable onComplete) {
        try {
            com.example.mysoftpos.data.remote.api.ApiService api = com.example.mysoftpos.data.remote.api.ApiClient
                    .getService(this);

            api.login(new com.example.mysoftpos.data.remote.api.ApiService.LoginRequest(username, password))
                    .enqueue(new retrofit2.Callback<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse>() {
                        @Override
                        public void onResponse(
                                retrofit2.Call<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse> call,
                                retrofit2.Response<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                com.example.mysoftpos.data.remote.api.ApiService.LoginResponse resp = response.body();
                                // Save fresh JWT token
                                com.example.mysoftpos.data.remote.api.ApiClient.saveUserSession(LoginActivity.this,
                                        resp);
                                // Update local cache with latest data from backend
                                com.example.mysoftpos.di.ServiceLocator.getInstance(LoginActivity.this)
                                        .getDispatcherProvider().io().execute(() -> {
                                            cacheUserLocallySync(username, password, resp.user);
                                            // Sync config & transactions
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
                            if (onComplete != null)
                                onComplete.run();
                        }

                        @Override
                        public void onFailure(
                                retrofit2.Call<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse> call,
                                Throwable t) {
                            // Network unavailable — no problem, user already logged in
                            android.util.Log.d("LoginActivity", "Background sync skipped: " + t.getMessage());
                            if (onComplete != null)
                                onComplete.run();
                        }
                    });
        } catch (Exception e) {
            android.util.Log.w("LoginActivity", "Background sync error: " + e.getMessage());
            if (onComplete != null)
                onComplete.run();
        }
    }

    // ====================================================================
    // PRIMARY: Backend API login via Retrofit
    // ====================================================================
    private void loginViaApi(String username, String password) {
        com.example.mysoftpos.data.remote.api.ApiService api = com.example.mysoftpos.data.remote.api.ApiClient
                .getService(this);

        api.login(new com.example.mysoftpos.data.remote.api.ApiService.LoginRequest(username, password))
                .enqueue(new retrofit2.Callback<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse>() {
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
                                    LoginActivity.this, username, "LOGIN",
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
                            // Set user-specific Terminal ID
                            if (resp.user.terminalId != null && !resp.user.terminalId.isEmpty()) {
                                config.setTerminalId(resp.user.terminalId);
                            }

                            // Cache user locally for offline login, then resolve local ID and navigate
                            com.example.mysoftpos.di.ServiceLocator.getInstance(LoginActivity.this)
                                    .getDispatcherProvider().io().execute(() -> {
                                        // Cache user to local Room DB
                                        cacheUserLocallySync(username, password, resp.user);

                                        // Resolve local Room user ID (not backend ID)
                                        long localUserId = resolveLocalUserId(username, resp.user.id);

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
                                            if (isDestroyed() || isFinishing())
                                                return;
                                            navigateToDashboard(localUserId, resp.user.role,
                                                    resp.user.fullName != null ? resp.user.fullName : "User",
                                                    resp.user.phone, resp.user.email);
                                        });
                                    });
                        } else {
                            String errorMsg = "Invalid username or password!";
                            try {
                                if (response.errorBody() != null) {
                                    String body = response.errorBody().string();
                                    if (body.contains("locked"))
                                        errorMsg = "Account locked. Try again later.";
                                }
                            } catch (Exception ignored) {
                            }

                            com.example.mysoftpos.utils.security.AuditLogger.log(
                                    LoginActivity.this, username, "LOGIN_FAILED",
                                    false, "LoginActivity", "API: " + response.code());

                            String finalMsg = errorMsg;
                            runOnUiThread(() -> {
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
                        loginViaLocalRoom(username, password);
                    }
                });
    }

    // ====================================================================
    // FALLBACK: Local Room DB login (offline mode)
    // ====================================================================
    private void loginViaLocalRoom(String username, String password) {
        com.example.mysoftpos.di.ServiceLocator.getInstance(this)
                .getDispatcherProvider().io().execute(() -> {
                    try {
                        com.example.mysoftpos.utils.config.ConfigManager config = com.example.mysoftpos.utils.config.ConfigManager
                                .getInstance(LoginActivity.this);
                        com.example.mysoftpos.data.local.AppDatabase db = com.example.mysoftpos.data.local.AppDatabase
                                .getInstance(LoginActivity.this);
                        com.example.mysoftpos.data.local.dao.UserDao userDao = db.userDao();

                        com.example.mysoftpos.data.local.entity.UserEntity user = userDao.findByPhone(username);
                        if (user == null)
                            user = userDao.findByEmail(username);
                        if (user == null) {
                            String hash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(username);
                            user = userDao.findByUsernameHash(hash);
                        }

                        if (user != null) {
                            if (user.lockedUntil > System.currentTimeMillis()) {
                                int min = (int) ((user.lockedUntil - System.currentTimeMillis()) / 60000) + 1;
                                runOnUiThread(() -> {
                                    if (!isDestroyed() && !isFinishing())
                                        Toast.makeText(LoginActivity.this,
                                                "Account locked. Try again in " + min + " minutes.",
                                                Toast.LENGTH_LONG).show();
                                });
                                return;
                            }

                            if (com.example.mysoftpos.utils.security.PasswordUtils
                                    .verifyPassword(password, user.passwordHash)) {
                                user.failedLoginAttempts = 0;
                                user.lockedUntil = 0;

                                // Progressive migration: upgrade legacy SHA-256 hashes to PBKDF2
                                if (!user.passwordHash.contains(":")) {
                                    user.passwordHash = com.example.mysoftpos.utils.security.PasswordUtils
                                            .hashPassword(password);
                                }

                                userDao.update(user);

                                String displayName = user.displayName != null ? user.displayName : "User";
                                config.resetServerConfig();
                                if (user.serverIp != null && !user.serverIp.isEmpty() && user.serverPort > 0) {
                                    config.setServerIp(user.serverIp);
                                    config.setServerPort(user.serverPort);
                                }
                                if (user.terminalId != null && !user.terminalId.isEmpty()) {
                                    config.setTerminalId(user.terminalId);
                                }

                                com.example.mysoftpos.utils.security.SessionManager.startSession();
                                com.example.mysoftpos.utils.security.AuditLogger.log(
                                        LoginActivity.this, username, "LOGIN",
                                        true, "LoginActivity", "Offline login: " + user.role);

                                // Background: sync with API to refresh JWT token for API-dependent screens
                                final com.example.mysoftpos.data.local.entity.UserEntity finalUser = user;
                                syncWithBackendInBackground(username, password, () -> {
                                    runOnUiThread(() -> {
                                        if (!isDestroyed() && !isFinishing()) {
                                            Toast.makeText(LoginActivity.this,
                                                    "Login Successful (Offline)!", Toast.LENGTH_SHORT).show();
                                            navigateToDashboard(finalUser.id, finalUser.role, displayName,
                                                    finalUser.phone, finalUser.email);
                                        }
                                    });
                                });
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

                        runOnUiThread(() -> {
                            if (!isDestroyed() && !isFinishing()) {
                                Toast.makeText(LoginActivity.this,
                                        "Server không khả dụng và không tìm thấy tài khoản offline.",
                                        Toast.LENGTH_LONG).show();
                                etPassword.setText("");
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            if (!isDestroyed() && !isFinishing())
                                Toast.makeText(LoginActivity.this,
                                        "Login Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private void navigateToDashboard(long userId, String role, String displayName, String phone, String email) {
        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();
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

            String usernameHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(username);
            String passwordHash = com.example.mysoftpos.utils.security.PasswordUtils.hashPassword(password);

            com.example.mysoftpos.data.local.entity.UserEntity existing = userDao.findByUsernameHash(usernameHash);
            if (existing == null) {
                if (userDto.phone != null)
                    existing = userDao.findByPhone(userDto.phone);
                if (existing == null && userDto.email != null)
                    existing = userDao.findByEmail(userDto.email);
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
                        userDto.email, userDto.phone, null);
                newUser.backendId = userDto.id;
                if (userDto.terminalId != null)
                    newUser.terminalId = userDto.terminalId;
                if (userDto.serverIp != null)
                    newUser.serverIp = userDto.serverIp;
                if (userDto.serverPort != null)
                    newUser.serverPort = userDto.serverPort;
                userDao.insert(newUser);
            }
        } catch (Exception e) {
            android.util.Log.w("LoginActivity",
                    "Failed to cache user locally: " + e.getMessage());
        }
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

            // Try phone first
            com.example.mysoftpos.data.local.entity.UserEntity user = userDao.findByPhone(username);
            if (user == null)
                user = userDao.findByEmail(username);
            if (user == null) {
                String hash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(username);
                user = userDao.findByUsernameHash(hash);
            }
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
}
