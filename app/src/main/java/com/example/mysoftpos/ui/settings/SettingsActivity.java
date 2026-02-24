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

        LinearLayout btnIdentitySettings = findViewById(R.id.btnIdentitySettings);
        LinearLayout btnSecuritySettings = findViewById(R.id.btnSecuritySettings);

        if (btnIdentitySettings != null)
            btnIdentitySettings.setOnClickListener(v -> showIdentityDialog());
        if (btnSecuritySettings != null)
            btnSecuritySettings.setOnClickListener(v -> showSecurityDialog());

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
        } catch (Exception e) {
            e.printStackTrace();
            android.widget.Toast.makeText(this, "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, "Security Settings Saved", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });

            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
            android.widget.Toast.makeText(this, "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
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
