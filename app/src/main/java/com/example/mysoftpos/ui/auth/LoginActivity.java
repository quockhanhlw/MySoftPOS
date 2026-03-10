package com.example.mysoftpos.ui.auth;

import com.example.mysoftpos.R;
import com.example.mysoftpos.utils.mcc.BusinessTypeMccMapper;

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

public class LoginActivity extends BaseActivity {

    private static final String EXTRA_PASSWORD_CHANGED_RELOGIN = "PASSWORD_CHANGED_RELOGIN";

    private EditText etUsername;
    private EditText etPassword;
    private boolean passwordVisible = false;
    private View loadingOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        loadingOverlay = findViewById(R.id.loadingOverlay);

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

        showLoading();
        boolean networkAvailable = isNetworkAvailable();
        loginViaLocalRoom(username, password, networkAvailable);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(
                android.content.Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkCapabilities capabilities = connectivityManager
                    .getNetworkCapabilities(connectivityManager.getActiveNetwork());
            return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        }
        return false;
    }

    /**
     * Background sync with backend API after successful local login.
     * Refreshes JWT token, updates local cache, syncs transactions.
     */
    private void syncWithBackendInBackground(String username, String password) {
        try {
            com.example.mysoftpos.data.remote.api.ApiService api = com.example.mysoftpos.data.remote.api.ApiClient
                    .getAuthService(this);

            api.login(new com.example.mysoftpos.data.remote.api.ApiService.LoginRequest(username, password))
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
                                com.example.mysoftpos.di.ServiceLocator.getInstance(LoginActivity.this)
                                        .getDispatcherProvider().io().execute(() -> {
                                            cacheUserLocallySync(username, password, resp.user);
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
    private void loginViaApi(String username, String password) {
        com.example.mysoftpos.data.remote.api.ApiService api = com.example.mysoftpos.data.remote.api.ApiClient
                .getAuthService(this);

        api.login(new com.example.mysoftpos.data.remote.api.ApiService.LoginRequest(username, password))
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
                            config.setMcc18(resp.user.businessType);

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
                                            hideLoading();
                                            if (isDestroyed() || isFinishing())
                                                return;
                                            navigateToDashboard(localUserId, resp.user.role,
                                                    resp.user.fullName != null ? resp.user.fullName : "User",
                                                    resp.user.phone, resp.user.email);
                                        });
                                    });
                        } else {
                            String errorMsg = "Invalid username or password!";
                            try (okhttp3.ResponseBody errorBody = response.errorBody()) {
                                if (errorBody != null) {
                                    String body = errorBody.string();
                                    if (body.contains("locked")) {
                                        errorMsg = "Account locked. Try again later.";
                                    }
                                }
                            } catch (Exception ignored) {
                            }

                            com.example.mysoftpos.utils.security.AuditLogger.log(
                                    LoginActivity.this, username, "LOGIN_FAILED",
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
                        loginViaLocalRoom(username, password, false);
                    }
                });
    }

    // ====================================================================
    // LOCAL-FIRST: cached Room login with optional API fallback
    // ====================================================================
    private void loginViaLocalRoom(String username, String password, boolean allowApiFallback) {
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
                                    hideLoading();
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
                                config.setMcc18(user.businessType);

                                com.example.mysoftpos.utils.security.SessionManager.startSession();
                                com.example.mysoftpos.utils.security.AuditLogger.log(
                                        LoginActivity.this, username, "LOGIN",
                                        true, "LoginActivity", "Local login: " + user.role);

                                final com.example.mysoftpos.data.local.entity.UserEntity finalUser = user;
                                runOnUiThread(() -> {
                                    hideLoading();
                                    if (!isDestroyed() && !isFinishing()) {
                                        Toast.makeText(LoginActivity.this,
                                                "Login Successful!", Toast.LENGTH_SHORT).show();
                                        navigateToDashboard(finalUser.id, finalUser.role, displayName,
                                                finalUser.phone, finalUser.email);
                                    }
                                });

                                if (allowApiFallback) {
                                    syncWithBackendInBackground(username, password);
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
                            runOnUiThread(() -> loginViaApi(username, password));
                            return;
                        }

                        runOnUiThread(() -> {
                            hideLoading();
                            if (!isDestroyed() && !isFinishing()) {
                                Toast.makeText(LoginActivity.this,
                                        "Server không khả dụng và không tìm thấy tài khoản offline.",
                                        Toast.LENGTH_LONG).show();
                                etPassword.setText("");
                            }
                        });
                    } catch (Exception e) {
                        if (allowApiFallback) {
                            runOnUiThread(() -> loginViaApi(username, password));
                            return;
                        }
                        runOnUiThread(() -> {
                            hideLoading();
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

            String normalizedBusinessType = BusinessTypeMccMapper.toMcc(userDto.businessType);

            if (existing != null) {
                existing.usernameHash = usernameHash;
                existing.passwordHash = passwordHash;
                existing.displayName = userDto.fullName;
                existing.role = userDto.role;
                existing.phone = userDto.phone;
                existing.email = userDto.email;
                existing.dob = userDto.dob;
                existing.gender = safeText(userDto.gender);
                existing.storeName = safeText(userDto.storeName);
                existing.businessType = normalizedBusinessType;
                existing.storeAddress = safeText(userDto.storeAddress);
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
                newUser.gender = safeText(userDto.gender);
                newUser.storeName = safeText(userDto.storeName);
                newUser.businessType = normalizedBusinessType;
                newUser.storeAddress = safeText(userDto.storeAddress);
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
        } catch (Exception e) {
            android.util.Log.w("LoginActivity",
                    "Failed to cache user locally: " + e.getMessage());
        }
    }

    private String safeText(String value) {
        return value != null ? value : "";
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

    private Drawable getPasswordToggleDrawable(int drawableResId) {
        return AppCompatResources.getDrawable(this, drawableResId);
    }
}
