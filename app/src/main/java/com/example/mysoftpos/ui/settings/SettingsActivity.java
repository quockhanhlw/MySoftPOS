package com.example.mysoftpos.ui.settings;

import com.example.mysoftpos.R;
import com.example.mysoftpos.utils.LocaleHelper;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    // Admin Views
    private TextInputEditText etServerIp;
    private TextInputEditText etPort;
    private TextInputEditText etTimeout;
    private TextInputEditText etTerminalId;
    private TextInputEditText etMerchantId;
    private CheckBox cbEncryptPin;
    private MaterialButton btnSaveConfig;
    private MaterialButton btnPingTest;

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
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null)
            btnBack.setOnClickListener(v -> finish());

        etServerIp = findViewById(R.id.etServerIp);
        etPort = findViewById(R.id.etPort);
        etTimeout = findViewById(R.id.etTimeout);
        etTerminalId = findViewById(R.id.etTerminalId);
        etMerchantId = findViewById(R.id.etMerchantId);
        cbEncryptPin = findViewById(R.id.cbEncryptPin);

        btnSaveConfig = findViewById(R.id.btnSaveConfig);
        btnPingTest = findViewById(R.id.btnPingTest);

        loadAdminConfiguration();

        if (btnSaveConfig != null)
            btnSaveConfig.setOnClickListener(v -> saveAdminConfiguration());
        if (btnPingTest != null)
            btnPingTest.setOnClickListener(v -> performPingTest());

        // Initialize Common User Settings (now present in Admin layout too)
        initCommonUI();
    }

    private void initUserUI() {
        // Find Views from activity_settings_user.xml
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null)
            btnBack.setOnClickListener(v -> finish());

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
            tvCurrentLanguage.setText("vi".equals(currentLang) ? "Tiếng Việt" : "English");
        }

        // Wallpaper Logic
        if (btnChangeWallpaper != null) {
            btnChangeWallpaper.setOnClickListener(v -> {
                com.example.mysoftpos.utils.NotificationHelper.showNotification(this, "Wallpaper Feature Coming Soon!",
                        R.drawable.ic_settings);
            });
        }

        // Language Logic
        if (btnChangeLanguage != null) {
            btnChangeLanguage.setOnClickListener(v -> {
                String current = LocaleHelper.getLanguage(this);
                String newLang = "vi".equals(current) ? "en" : "vi";

                LocaleHelper.setLocale(this, newLang);

                // Restart App to apply changes
                Intent intent = new Intent(this, com.example.mysoftpos.ui.dashboard.MainDashboardActivity.class);
                // Pass back the user info so they don't have to login again if possible, or
                // force logout.
                // For now, let's just restart Dashboard with same user if we can, or just
                // settings.
                // If we restart Dashboard, we need user role.
                String userRole = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.USER_ROLE);
                if (userRole == null)
                    userRole = "USER";

                String username = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME); // Assuming
                                                                                                               // passed
                if (username == null)
                    username = "Guest";

                intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USER_ROLE, userRole);
                intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME, username);
                intent.addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }

        // Password Logic
        if (btnChangePassword != null) {
            btnChangePassword.setOnClickListener(v -> {
                // Show Custom Notification as requested
                com.example.mysoftpos.utils.NotificationHelper.showNotification(this,
                        "Change Password Dialog would appear here", R.drawable.ic_key);
            });
        }

        // Logout Logic
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                Intent intent = new Intent(this, com.example.mysoftpos.ui.auth.WelcomeActivity.class);
                intent.addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
    }

    private void loadAdminConfiguration() {
        com.example.mysoftpos.utils.config.ConfigManager config = com.example.mysoftpos.utils.config.ConfigManager
                .getInstance(this);

        if (etServerIp != null)
            etServerIp.setText(config.getServerIp());
        if (etPort != null)
            etPort.setText(String.valueOf(config.getServerPort()));
        if (etTimeout != null)
            etTimeout.setText(String.valueOf(config.getTimeout()));
        if (etTerminalId != null)
            etTerminalId.setText(config.getTerminalId());
        if (etMerchantId != null)
            etMerchantId.setText(config.getMerchantId());
        if (cbEncryptPin != null)
            cbEncryptPin.setChecked(config.isPinEncryptionEnabled());
    }

    private void saveAdminConfiguration() {
        String serverIp = etServerIp.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();
        String timeoutStr = etTimeout.getText().toString().trim();
        String terminalId = etTerminalId.getText().toString().trim();
        String merchantId = etMerchantId.getText().toString().trim();

        if (serverIp.isEmpty()) {
            etServerIp.setError("Please enter Server IP");
            return;
        }
        if (portStr.isEmpty()) {
            etPort.setError("Please enter Port");
            return;
        }
        if (terminalId.isEmpty()) {
            etTerminalId.setError("Please enter Terminal ID");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            etPort.setError("Invalid Port");
            return;
        }

        int timeout;
        try {
            timeout = Integer.parseInt(timeoutStr);
        } catch (NumberFormatException e) {
            timeout = 30000;
        }

        com.example.mysoftpos.utils.config.ConfigManager config = com.example.mysoftpos.utils.config.ConfigManager
                .getInstance(this);

        config.setServerIp(serverIp);
        config.setServerPort(port);
        config.setTimeout(timeout);
        config.setTerminalId(terminalId);
        config.setMerchantId(merchantId);
        config.setPinEncryptionEnabled(cbEncryptPin.isChecked());

        Toast.makeText(this, "Configuration Saved!", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void performPingTest() {
        String serverIp = etServerIp.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();

        if (serverIp.isEmpty() || portStr.isEmpty()) {
            Toast.makeText(this, "Please enter IP and Port", Toast.LENGTH_SHORT).show();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid Port", Toast.LENGTH_SHORT).show();
            return;
        }

        android.app.ProgressDialog pd = new android.app.ProgressDialog(this);
        pd.setMessage("Connecting to " + serverIp + ":" + port + "...");
        pd.setCancelable(false);
        pd.show();

        new Thread(() -> {
            try (java.net.Socket socket = new java.net.Socket()) {
                // Try to connect with a 5-second timeout
                socket.connect(new java.net.InetSocketAddress(serverIp, port), 5000);

                runOnUiThread(() -> {
                    pd.dismiss();
                    showResultDialog(true, "Connection Successful!\nHost is reachable.");
                });
            } catch (java.io.IOException e) {
                final String errorMsg = "Connection Failed:\n" + e.getMessage();
                runOnUiThread(() -> {
                    pd.dismiss();
                    showResultDialog(false, errorMsg);
                });
            }
        }).start();
    }

    private void showResultDialog(boolean success, String message) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(success ? "Connection Test Passed" : "Connection Test Failed")
                .setMessage(message)
                .setIcon(success ? R.drawable.ic_check_circle : R.drawable.ic_error)
                .setPositiveButton("OK", null)
                .show();
    }
}
