package com.example.mysoftpos.ui.settings;

import com.example.mysoftpos.R;
import com.example.mysoftpos.utils.LocaleHelper;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.example.mysoftpos.ui.BaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends BaseActivity {
    private static final String TAG = "SettingsActivity";
    private static final String REQUIRED_BACKEND_SCHEME = "https://";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String userRole = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.USER_ROLE);
        boolean isAdmin = "ADMIN".equals(userRole);

        if (isAdmin) {
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
        LinearLayout btnSecuritySettings = findViewById(R.id.btnSecuritySettings);

        if (btnIdentitySettings != null)
            btnIdentitySettings.setOnClickListener(v -> showIdentityDialog());
        if (btnSecuritySettings != null)
            btnSecuritySettings.setOnClickListener(v -> showSecurityDialog());

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

    private void showSecurityDialog() {
        try {
            com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                    this);
            View view = getLayoutInflater().inflate(R.layout.dialog_admin_security, null);
            builder.setView(view);
            androidx.appcompat.app.AlertDialog dialog = builder.create();
            view.setBackgroundColor(android.graphics.Color.WHITE);

            CheckBox cbEncryptPin = view.findViewById(R.id.cbEncryptPin);
            MaterialButton btnCancel = view.findViewById(R.id.btnCancel);
            MaterialButton btnSave = view.findViewById(R.id.btnSave);

            com.example.mysoftpos.utils.config.ConfigManager config = com.example.mysoftpos.utils.config.ConfigManager
                    .getInstance(this);
            cbEncryptPin.setChecked(config.isPinEncryptionEnabled());

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            btnSave.setOnClickListener(v -> {
                config.setPinEncryptionEnabled(cbEncryptPin.isChecked());
                showToast(R.string.settings_security_saved);
                dialog.dismiss();
            });

            dialog.show();
        } catch (Exception e) {
            showDialogError("showSecurityDialog failed", e);
        }
    }

    private void showBackendUrlDialog() {
        try {
            android.view.View view = getLayoutInflater().inflate(R.layout.dialog_backend_url, null);
            androidx.appcompat.app.AlertDialog dialog =
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setView(view).create();

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
                if (url.isEmpty()) { etUrl.setError(getString(R.string.settings_backend_url_required)); return; }
                if (!url.endsWith("/")) url = url + "/";
                tvStatus.setVisibility(android.view.View.VISIBLE);
                updateConnectionStatus(tvStatus, null, null);

                final String testUrl = url;
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
                    runOnUiThread(() -> updateConnectionStatus(tvStatus, ok, msg));
                }).start();
            });

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            btnSave.setOnClickListener(v -> {
                String url = trimmedValue(etUrl);
                if (url.isEmpty()) { etUrl.setError(getString(R.string.settings_backend_url_required)); return; }
                if (!url.startsWith(REQUIRED_BACKEND_SCHEME)) {
                    etUrl.setError(getString(R.string.settings_backend_url_https_required));
                    return;
                }
                if (!url.endsWith("/")) url = url + "/";
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

        // Update Language Text
        if (tvCurrentLanguage != null) {
            String currentLang = LocaleHelper.getLanguage(this);
            tvCurrentLanguage.setText("vi".equals(currentLang)
                    ? getString(R.string.settings_language_vi)
                    : getString(R.string.settings_language_en));
        }

        // Wallpaper Logic
        if (btnChangeWallpaper != null) {
            btnChangeWallpaper.setOnClickListener(v -> com.example.mysoftpos.utils.NotificationHelper.showNotification(
                    this,
                    getString(R.string.settings_feature_wallpaper),
                    getString(R.string.settings_feature_coming_soon),
                    false));
        }

        // Language Logic
        if (btnChangeLanguage != null) {
            btnChangeLanguage.setOnClickListener(v -> {
                String current = LocaleHelper.getLanguage(this);
                String newLang = "vi".equals(current) ? "en" : "vi";

                LocaleHelper.setLocale(this, newLang);

                // Restart Activity to apply changes
                Intent intent = new Intent(this, SettingsActivity.class);
                String userRole = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.USER_ROLE);
                if (userRole == null)
                    userRole = "USER";

                intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USER_ROLE, userRole);
                // intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME, username);
                // // Optional if needed
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }

        // Password Logic
        if (btnChangePassword != null) {
            btnChangePassword.setOnClickListener(v -> com.example.mysoftpos.utils.NotificationHelper.showNotification(
                    this,
                    getString(R.string.settings_feature_password),
                    getString(R.string.settings_password_dialog_placeholder),
                    false));
        }

        // Logout Logic
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                // Clear API session (tokens, user info)
                com.example.mysoftpos.data.remote.api.ApiClient.clearSession(this);
                com.example.mysoftpos.data.remote.api.ApiClient.reset();
                com.example.mysoftpos.data.remote.SyncWorker.cancelPeriodicSync(this);
                // End PA-DSS session
                com.example.mysoftpos.utils.security.SessionManager.endSession();

                Intent intent = new Intent(this, com.example.mysoftpos.ui.auth.WelcomeActivity.class);
                intent.addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
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
}
