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

        LinearLayout btnConnectionSettings = findViewById(R.id.btnConnectionSettings);
        LinearLayout btnIdentitySettings = findViewById(R.id.btnIdentitySettings);
        LinearLayout btnSecuritySettings = findViewById(R.id.btnSecuritySettings);

        if (btnConnectionSettings != null)
            btnConnectionSettings.setOnClickListener(v -> showConnectionDialog());
        if (btnIdentitySettings != null)
            btnIdentitySettings.setOnClickListener(v -> showIdentityDialog());
        if (btnSecuritySettings != null)
            btnSecuritySettings.setOnClickListener(v -> showSecurityDialog());

        // Initialize Common User Settings (now present in Admin layout too)
        initCommonUI();
    }

    private void showConnectionDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_admin_connection, null);
        builder.setView(view);
        android.app.AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0)); // Transparent needed
                                                                                                  // for rounded corners
                                                                                                  // if root has
                                                                                                  // background
        // Ideally set background in XML root or use style, but here we can just rely on
        // the view's background from XML if set,
        // or wrap in CardView in XML. The XML I wrote has root background? No.
        // Let's set the dialog background to white rounded if possible, or just
        // standard.
        // Actually, the XML I wrote works best if wrapped in a CardView or if I set
        // background here.
        view.setBackgroundResource(R.drawable.bg_dialog_rounded); // Assuming this drawable exists or similar

        TextInputEditText etServerIp = view.findViewById(R.id.etServerIp);
        TextInputEditText etPort = view.findViewById(R.id.etPort);
        TextInputEditText etTimeout = view.findViewById(R.id.etTimeout);
        MaterialButton btnPingTest = view.findViewById(R.id.btnPingTest);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);
        MaterialButton btnSave = view.findViewById(R.id.btnSave);

        // Load Config
        com.example.mysoftpos.utils.config.ConfigManager config = com.example.mysoftpos.utils.config.ConfigManager
                .getInstance(this);
        etServerIp.setText(config.getServerIp());
        etPort.setText(String.valueOf(config.getServerPort()));
        etTimeout.setText(String.valueOf(config.getTimeout()));

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnPingTest.setOnClickListener(v -> {
            String serverIp = etServerIp.getText().toString().trim();
            String portStr = etPort.getText().toString().trim();
            performPingTest(serverIp, portStr);
        });

        btnSave.setOnClickListener(v -> {
            String serverIp = etServerIp.getText().toString().trim();
            String portStr = etPort.getText().toString().trim();
            String timeoutStr = etTimeout.getText().toString().trim();

            if (serverIp.isEmpty()) {
                etServerIp.setError("Required");
                return;
            }
            if (portStr.isEmpty()) {
                etPort.setError("Required");
                return;
            }

            int port;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                etPort.setError("Invalid");
                return;
            }

            int timeout;
            try {
                timeout = Integer.parseInt(timeoutStr);
            } catch (NumberFormatException e) {
                timeout = 30000;
            }

            config.setServerIp(serverIp);
            config.setServerPort(port);
            config.setTimeout(timeout);

            Toast.makeText(this, "Connection Settings Saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showIdentityDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_admin_identity, null);
        builder.setView(view);
        android.app.AlertDialog dialog = builder.create();
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
            String terminalId = etTerminalId.getText().toString().trim();
            String merchantId = etMerchantId.getText().toString().trim();

            if (terminalId.isEmpty()) {
                etTerminalId.setError("Required");
                return;
            }
            if (merchantId.isEmpty()) {
                etMerchantId.setError("Required");
                return;
            }

            config.setTerminalId(terminalId);
            config.setMerchantId(merchantId);

            Toast.makeText(this, "Identity Settings Saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showSecurityDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_admin_security, null);
        builder.setView(view);
        android.app.AlertDialog dialog = builder.create();
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
            Toast.makeText(this, "Security Settings Saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void performPingTest(String serverIp, String portStr) {
        if (serverIp.isEmpty() || portStr.isEmpty()) {
            Toast.makeText(this, "Please check IP and Port", Toast.LENGTH_SHORT).show();
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
        pd.setMessage("Ping " + serverIp + ":" + port + "...");
        pd.setCancelable(false);
        pd.show();

        new Thread(() -> {
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(serverIp, port), 5000);
                runOnUiThread(() -> {
                    pd.dismiss();
                    showPingResult(true, "Connection Successful!");
                });
            } catch (java.io.IOException e) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    showPingResult(false, "Connection Failed:\n" + e.getMessage());
                });
            }
        }).start();
    }

    private void showPingResult(boolean success, String message) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(success ? "Success" : "Failed")
                .setMessage(message)
                .setIcon(success ? R.drawable.ic_check_circle : R.drawable.ic_error)
                .setPositiveButton("OK", null)
                .show();
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
                com.example.mysoftpos.utils.NotificationHelper.showNotification(this, "Wallpaper",
                        "Feature Coming Soon!", false);
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
                String userRole = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.USER_ROLE);
                if (userRole == null)
                    userRole = "USER";

                String username = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME);
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
                com.example.mysoftpos.utils.NotificationHelper.showNotification(this, "Password",
                        "Change Password Dialog would appear here", false);
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
}
