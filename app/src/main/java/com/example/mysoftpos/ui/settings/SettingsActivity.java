package com.example.mysoftpos.ui.settings;

import com.example.mysoftpos.R;
import com.example.mysoftpos.utils.LocaleHelper;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.example.mysoftpos.ui.BaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends BaseActivity {
    private static final String TAG = "SettingsActivity";
    private static final String REQUIRED_BACKEND_SCHEME = "https://";
    private static final String EXTRA_PASSWORD_CHANGED_RELOGIN = "PASSWORD_CHANGED_RELOGIN";

    private boolean isAdminSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String userRole = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.USER_ROLE);
        isAdminSettings = "ADMIN".equals(userRole);

        if (isAdminSettings) {
            setContentView(R.layout.activity_settings);
            initAdminUI();
        } else {
            setContentView(R.layout.activity_settings_user);
            initUserUI();
        }
    }

    private void initAdminUI() {
        bindBackButton();

        LinearLayout btnIdentitySettings = findViewById(R.id.btnIdentitySettings);

        if (btnIdentitySettings != null)
            btnIdentitySettings.setOnClickListener(v -> showIdentityDialog());

        // Backend URL Settings
        LinearLayout btnBackendUrlSettings = findViewById(R.id.btnBackendUrlSettings);
        TextView tvCurrentBackendUrl = findViewById(R.id.tvCurrentBackendUrl);
        if (tvCurrentBackendUrl != null) {
            tvCurrentBackendUrl.setText(
                    com.example.mysoftpos.data.remote.api.ApiClient.getBaseUrl(this));
        }
        if (btnBackendUrlSettings != null) {
            btnBackendUrlSettings.setOnClickListener(v -> showBackendUrlDialog());
        }

        // Initialize Common User Settings (now present in Admin layout too)
        initCommonUI();
    }

    private void showIdentityDialog() {
        try {
            com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                    this);
            View view = getLayoutInflater().inflate(R.layout.dialog_admin_identity, null);
            builder.setView(view);
            androidx.appcompat.app.AlertDialog dialog = builder.create();
            // view.setBackgroundResource(R.drawable.bg_dialog_rounded);
            // If bg_dialog_rounded doesn't exist, use white
            view.setBackgroundColor(android.graphics.Color.WHITE);

            TextInputEditText etTerminalId = view.findViewById(R.id.etTerminalId);
            TextInputEditText etMerchantId = view.findViewById(R.id.etMerchantId);
            MaterialButton btnCancel = view.findViewById(R.id.btnCancel);
            MaterialButton btnSave = view.findViewById(R.id.btnSave);

            com.example.mysoftpos.utils.config.ConfigManager config = com.example.mysoftpos.utils.config.ConfigManager
                    .getInstance(this);
            etTerminalId.setText(config.getTerminalId());
            etMerchantId.setText(config.getMerchantId());

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            btnSave.setOnClickListener(v -> {
                String terminalId = trimmedValue(etTerminalId);
                String merchantId = trimmedValue(etMerchantId);

                if (requireValue(etTerminalId, terminalId) || requireValue(etMerchantId, merchantId)) {
                    return;
                }

                config.setTerminalId(terminalId);
                config.setMerchantId(merchantId);

                showToast(R.string.settings_identity_saved);
                dialog.dismiss();
            });

            dialog.show();
        } catch (Exception e) {
            showDialogError("showIdentityDialog failed", e);
        }
    }


    private void showBackendUrlDialog() {
        try {
            android.view.View view = getLayoutInflater().inflate(R.layout.dialog_backend_url, null);
            androidx.appcompat.app.AlertDialog dialog =
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setView(view).create();
            dialog.setCanceledOnTouchOutside(false);

            TextInputEditText etUrl = view.findViewById(R.id.etBackendUrl);
            android.widget.TextView tvCurrentUrl = view.findViewById(R.id.tvCurrentUrl);
            android.widget.TextView tvStatus = view.findViewById(R.id.tvConnectionStatus);
            android.view.View btnTest = view.findViewById(R.id.btnTestConnection);
            android.view.View btnCancel = view.findViewById(R.id.btnCancel);
            android.view.View btnSave = view.findViewById(R.id.btnSave);

            String currentUrl = com.example.mysoftpos.data.remote.api.ApiClient.getBaseUrl(this);
            tvCurrentUrl.setText(getString(R.string.settings_backend_current, currentUrl));
            etUrl.setText(currentUrl);
            etUrl.setSelection(etUrl.getText() != null ? etUrl.getText().length() : 0);

            btnTest.setOnClickListener(v -> {
                String url = trimmedValue(etUrl);
                if (url.isEmpty()) {
                    etUrl.setError(getString(R.string.settings_backend_url_required));
                    showBackendConnectionFailure(tvStatus, getString(R.string.settings_backend_url_required));
                    return;
                }

                final String testUrl = ensureTrailingSlash(url);
                try {
                    new java.net.URL(testUrl);
                } catch (Exception e) {
                    etUrl.setError(getString(R.string.settings_backend_failed, e.getMessage()));
                    showBackendConnectionFailure(tvStatus, e.getClass().getSimpleName() + ": " + e.getMessage());
                    return;
                }

                btnTest.setEnabled(false);
                tvStatus.setVisibility(android.view.View.VISIBLE);
                updateConnectionStatus(tvStatus, null, null);

                new Thread(() -> {
                    boolean reachable = false;
                    String detail;
                    try {
                        java.net.URL u = new java.net.URL(testUrl + "api-docs");
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(5000);
                        conn.setRequestMethod("GET");
                        int code = conn.getResponseCode();
                        reachable = (code >= 200 && code < 500);
                        detail = "HTTP " + code;
                        conn.disconnect();
                    } catch (Exception e) {
                        detail = e.getClass().getSimpleName() + ": " + e.getMessage();
                    }
                    final boolean ok = reachable;
                    final String msg = detail;
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || !dialog.isShowing()) {
                            return;
                        }
                        btnTest.setEnabled(true);
                        updateConnectionStatus(tvStatus, ok, msg);
                        if (!ok) {
                            Toast.makeText(this, getString(R.string.settings_backend_failed, msg), Toast.LENGTH_LONG)
                                    .show();
                        }
                    });
                }).start();
            });

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            btnSave.setOnClickListener(v -> {
                String url = trimmedValue(etUrl);
                if (url.isEmpty()) {
                    etUrl.setError(getString(R.string.settings_backend_url_required));
                    showBackendConnectionFailure(tvStatus, getString(R.string.settings_backend_url_required));
                    return;
                }
                if (!url.startsWith(REQUIRED_BACKEND_SCHEME)) {
                    etUrl.setError(getString(R.string.settings_backend_url_https_required));
                    showBackendConnectionFailure(tvStatus, getString(R.string.settings_backend_url_https_required));
                    return;
                }
                url = ensureTrailingSlash(url);
                com.example.mysoftpos.data.remote.api.ApiClient.setBaseUrl(this, url);

                TextView tvLabel = findViewById(R.id.tvCurrentBackendUrl);
                if (tvLabel != null) tvLabel.setText(url);

                showToast(R.string.settings_backend_saved);
                dialog.dismiss();
            });

            dialog.show();
        } catch (Exception e) {
            showDialogError("showBackendUrlDialog failed", e);
        }
    }

    private void initUserUI() {
        bindBackButton();
        initCommonUI();
    }

    private void initCommonUI() {
        LinearLayout btnChangeWallpaper = findViewById(R.id.btnChangeWallpaper);
        LinearLayout btnChangeLanguage = findViewById(R.id.btnChangeLanguage);
        LinearLayout btnChangePassword = findViewById(R.id.btnChangePassword);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        TextView tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage);

        if (tvCurrentLanguage != null) {
            tvCurrentLanguage.setText(getLanguageDisplayName(LocaleHelper.getLanguage(this)));
        }

        if (btnChangeWallpaper != null) {
            btnChangeWallpaper.setOnClickListener(v -> com.example.mysoftpos.utils.NotificationHelper.showNotification(
                    this,
                    getString(R.string.settings_feature_wallpaper),
                    getString(R.string.settings_feature_coming_soon),
                    false));
        }

        if (btnChangeLanguage != null) {
            btnChangeLanguage.setOnClickListener(v -> showLanguageDialog());
        }

        if (btnChangePassword != null) {
            btnChangePassword.setOnClickListener(v -> {
                if (isAdminSettings) {
                    com.example.mysoftpos.utils.NotificationHelper.showNotification(
                            this,
                            getString(R.string.settings_feature_password),
                            getString(R.string.settings_password_dialog_placeholder),
                            false);
                } else {
                    showChangePasswordDialog();
                }
            });
        }

        // Logout Logic
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> forceLogoutToWelcome());
        }
    }

    private void showLanguageDialog() {
        final String[] languageCodes = { "vi", "en" };
        final String currentLanguage = LocaleHelper.getLanguage(this);
        final CharSequence[] languageLabels = {
                getString(R.string.settings_language_vi),
                getString(R.string.settings_language_en)
        };
        int selectedIndex = "vi".equals(currentLanguage) ? 0 : 1;
        final int[] pendingIndex = { selectedIndex };

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(languageLabels, selectedIndex, (dialog, which) -> pendingIndex[0] = which)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_save, (dialog, which) -> {
                    String selectedLanguage = languageCodes[pendingIndex[0]];
                    if (!selectedLanguage.equals(currentLanguage)) {
                        LocaleHelper.setLocale(getApplicationContext(), selectedLanguage);
                        recreate();
                        overridePendingTransition(0, 0);
                    }
                })
                .show();
    }

    private void showChangePasswordDialog() {
        try {
            View view = getLayoutInflater().inflate(R.layout.dialog_change_password, null);
            androidx.appcompat.app.AlertDialog dialog =
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setView(view)
                            .create();
            dialog.setCanceledOnTouchOutside(false);

            TextInputEditText etCurrentPassword = view.findViewById(R.id.etCurrentPassword);
            TextInputEditText etNewPassword = view.findViewById(R.id.etNewPassword);
            TextInputEditText etConfirmPassword = view.findViewById(R.id.etConfirmPassword);
            TextView tvStatus = view.findViewById(R.id.tvPasswordStatus);
            MaterialButton btnCancel = view.findViewById(R.id.btnCancel);
            MaterialButton btnSave = view.findViewById(R.id.btnSave);

            btnCancel.setOnClickListener(v -> dialog.dismiss());
            btnSave.setOnClickListener(v -> attemptPasswordChange(
                    dialog,
                    etCurrentPassword,
                    etNewPassword,
                    etConfirmPassword,
                    tvStatus,
                    btnSave,
                    btnCancel));

            dialog.show();
        } catch (Exception e) {
            showDialogError("showChangePasswordDialog failed", e);
        }
    }

    private void attemptPasswordChange(androidx.appcompat.app.AlertDialog dialog,
                                       TextInputEditText etCurrentPassword,
                                       TextInputEditText etNewPassword,
                                       TextInputEditText etConfirmPassword,
                                       TextView tvStatus,
                                       MaterialButton btnSave,
                                       MaterialButton btnCancel) {
        String currentPassword = trimmedValue(etCurrentPassword);
        String newPassword = trimmedValue(etNewPassword);
        String confirmPassword = trimmedValue(etConfirmPassword);

        if (requireValue(etCurrentPassword, currentPassword)
                || requireValue(etNewPassword, newPassword)
                || requireValue(etConfirmPassword, confirmPassword)) {
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            etConfirmPassword.setError(getString(R.string.register_passwords_do_not_match));
            etConfirmPassword.requestFocus();
            return;
        }

        if (currentPassword.equals(newPassword)) {
            etNewPassword.setError(getString(R.string.settings_password_new_same_as_current));
            etNewPassword.requestFocus();
            return;
        }

        if (!com.example.mysoftpos.utils.security.PasswordPolicy.isValid(newPassword)) {
            etNewPassword.setError(getString(R.string.register_password_policy));
            etNewPassword.requestFocus();
            return;
        }

        if (!isNetworkAvailable()) {
            showPasswordStatus(tvStatus, false, getString(R.string.settings_password_network_required));
            return;
        }

        String token = com.example.mysoftpos.data.remote.api.ApiClient.bearerToken(this);
        long userId = com.example.mysoftpos.data.remote.api.ApiClient.getUserId(this);
        if (token.isEmpty() || userId <= 0 || !com.example.mysoftpos.data.remote.api.ApiClient.isLoggedIn(this)) {
            showPasswordStatus(tvStatus, false, getString(R.string.settings_password_session_required));
            return;
        }

        setPasswordDialogLoading(tvStatus, btnSave, btnCancel, true);
        com.example.mysoftpos.data.remote.api.ApiService.ChangePasswordRequest request =
                new com.example.mysoftpos.data.remote.api.ApiService.ChangePasswordRequest(
                        currentPassword, newPassword, confirmPassword);

        com.example.mysoftpos.data.remote.api.ApiClient.getService(this)
                .changePassword(token, request)
                .enqueue(new retrofit2.Callback<java.util.Map<String, String>>() {
                    @Override
                    public void onResponse(retrofit2.Call<java.util.Map<String, String>> call,
                                           retrofit2.Response<java.util.Map<String, String>> response) {
                        if (response.isSuccessful()) {
                            onPasswordChangedSuccessfully(dialog, tvStatus, newPassword);
                            return;
                        }

                        if (response.code() == 404 || response.code() == 405) {
                            setPasswordDialogLoading(tvStatus, btnSave, btnCancel, false);
                            showPasswordStatus(tvStatus, false, getString(R.string.settings_password_backend_unavailable));
                            return;
                        }

                        setPasswordDialogLoading(tvStatus, btnSave, btnCancel, false);
                        showPasswordStatus(tvStatus, false, extractBackendError(response,
                                getString(R.string.settings_password_backend_unavailable)));
                    }

                    @Override
                    public void onFailure(retrofit2.Call<java.util.Map<String, String>> call, Throwable t) {
                        setPasswordDialogLoading(tvStatus, btnSave, btnCancel, false);
                        showPasswordStatus(tvStatus, false,
                                getString(R.string.common_error_with_reason, t.getMessage()));
                    }
                });
    }

    private void onPasswordChangedSuccessfully(androidx.appcompat.app.AlertDialog dialog,
                                               TextView tvStatus,
                                               String newPassword) {
        updateLocalPasswordCacheAsync(newPassword, success -> runOnUiThread(() -> {
            if (success) {
                showPasswordStatus(tvStatus, true, getString(R.string.settings_password_success));
            } else {
                showPasswordStatus(tvStatus, true, getString(R.string.settings_password_local_sync_failed));
            }
            dialog.dismiss();
            forceLogoutToLoginAfterPasswordChange();
        }));
    }

    private void updateLocalPasswordCacheAsync(String newPassword,
                                               java.util.function.Consumer<Boolean> callback) {
        new Thread(() -> {
            boolean success = false;
            try {
                com.example.mysoftpos.data.local.dao.UserDao userDao =
                        com.example.mysoftpos.data.local.AppDatabase.getInstance(this).userDao();
                long backendUserId = com.example.mysoftpos.data.remote.api.ApiClient.getUserId(this);
                String currentUsername = com.example.mysoftpos.data.remote.api.ApiClient.getUsername(this);
                com.example.mysoftpos.data.local.entity.UserEntity localUser = null;

                if (backendUserId > 0) {
                    localUser = userDao.findByBackendId(backendUserId);
                }
                if (localUser == null && !currentUsername.trim().isEmpty()) {
                    localUser = userDao.findByAnyIdentifier(
                            currentUsername.trim(),
                            com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(currentUsername.trim()));
                }
                if (localUser != null) {
                    localUser.passwordHash = com.example.mysoftpos.utils.security.PasswordUtils.hashPassword(newPassword);
                    localUser.failedLoginAttempts = 0;
                    localUser.lockedUntil = 0;
                    userDao.update(localUser);
                    success = true;
                }
            } catch (Exception e) {
                Log.w(TAG, "updateLocalPasswordCacheAsync failed", e);
            }
            callback.accept(success);
        }).start();
    }

    private String extractBackendError(retrofit2.Response<?> response, String fallbackMessage) {
        String errorMessage = fallbackMessage;
        try (okhttp3.ResponseBody errorBody = response.errorBody()) {
            if (errorBody != null) {
                String body = errorBody.string();
                if (!body.trim().isEmpty()) {
                    errorMessage = body.trim();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "extractBackendError failed", e);
        }
        return errorMessage;
    }

    private void setPasswordDialogLoading(TextView tvStatus,
                                          MaterialButton btnSave,
                                          MaterialButton btnCancel,
                                          boolean loading) {
        btnSave.setEnabled(!loading);
        btnCancel.setEnabled(!loading);
        if (loading) {
            showPasswordStatus(tvStatus, null, getString(R.string.processing));
        }
    }

    private void showPasswordStatus(TextView tvStatus, Boolean success, String message) {
        if (tvStatus == null) {
            return;
        }
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(message);
        if (success == null) {
            tvStatus.setTextColor(android.graphics.Color.parseColor("#64748B"));
            tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#F1F5F9"));
        } else if (success) {
            tvStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"));
            tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#F0FDF4"));
        } else {
            tvStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"));
            tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#FEF2F2"));
        }
    }

    private String getLanguageDisplayName(String languageCode) {
        return "vi".equals(languageCode)
                ? getString(R.string.settings_language_vi)
                : getString(R.string.settings_language_en);
    }

    private String ensureTrailingSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    private void showBackendConnectionFailure(TextView tvStatus, String detail) {
        if (tvStatus != null) {
            tvStatus.setVisibility(android.view.View.VISIBLE);
            updateConnectionStatus(tvStatus, false, detail);
        }
        Toast.makeText(this, getString(R.string.settings_backend_failed, detail), Toast.LENGTH_LONG).show();
    }

    private void bindBackButton() {
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }

    private String trimmedValue(TextInputEditText editText) {
        return String.valueOf(editText.getText()).trim();
    }

    private boolean requireValue(TextInputEditText editText, String value) {
        if (!value.isEmpty()) {
            return false;
        }
        editText.setError(getString(R.string.common_required));
        editText.requestFocus();
        return true;
    }

    private void showToast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private void showDialogError(String logMessage, Exception e) {
        Log.e(TAG, logMessage, e);
        Toast.makeText(this, getString(R.string.common_error_with_reason, e.getMessage()), Toast.LENGTH_LONG).show();
    }

    private void updateConnectionStatus(TextView tvStatus, Boolean connected, String detail) {
        if (connected == null) {
            tvStatus.setText(R.string.settings_backend_testing);
            tvStatus.setTextColor(android.graphics.Color.parseColor("#64748B"));
            tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#F1F5F9"));
            return;
        }
        if (connected) {
            tvStatus.setText(getString(R.string.settings_backend_connected, detail));
            tvStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"));
            tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#F0FDF4"));
        } else {
            tvStatus.setText(getString(R.string.settings_backend_failed, detail));
            tvStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"));
            tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#FEF2F2"));
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private void forceLogoutToWelcome() {
        com.example.mysoftpos.data.remote.api.ApiClient.clearSession(this);
        com.example.mysoftpos.data.remote.api.ApiClient.reset();
        com.example.mysoftpos.data.remote.SyncWorker.cancelPeriodicSync(this);
        com.example.mysoftpos.utils.security.SessionManager.endSession();

        Intent intent = new Intent(this, com.example.mysoftpos.ui.auth.WelcomeActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void forceLogoutToLoginAfterPasswordChange() {
        com.example.mysoftpos.data.remote.api.ApiClient.clearSession(this);
        com.example.mysoftpos.data.remote.api.ApiClient.reset();
        com.example.mysoftpos.data.remote.SyncWorker.cancelPeriodicSync(this);
        com.example.mysoftpos.utils.security.SessionManager.endSession();

        Intent intent = new Intent(this, com.example.mysoftpos.ui.auth.LoginActivity.class);
        intent.putExtra(EXTRA_PASSWORD_CHANGED_RELOGIN, true);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
