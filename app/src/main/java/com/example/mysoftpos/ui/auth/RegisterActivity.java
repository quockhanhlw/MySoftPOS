package com.example.mysoftpos.ui.auth;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.dao.UserDao;
import com.example.mysoftpos.data.local.entity.UserEntity;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.LinkMovementMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class RegisterActivity extends AppCompatActivity {

    private LinearLayout cardPersonal;
    private LinearLayout cardBusiness;
    private boolean isBusiness = false;

    private EditText etAccountNumber;
    private EditText etBankName;
    private EditText etAccountName;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private ImageView btnShowPassword;
    private ImageView btnShowConfirmPassword;
    private CheckBox cbTerms;
    private TextView tvTermsText;

    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize views
        ImageView btnBack = findViewById(R.id.btnBack);
        cardPersonal = findViewById(R.id.cardPersonal);
        cardBusiness = findViewById(R.id.cardBusiness);

        etAccountNumber = findViewById(R.id.etAccountNumber);
        etBankName = findViewById(R.id.etBankName);
        etAccountName = findViewById(R.id.etAccountName);
        etPassword = findViewById(R.id.etPassword);
        // etConfirmPassword removed from UI but keeping variable if needed or simply
        // remove
        // Actually UI removed confirm password? Let me check layout XML.
        // Step 20788 shows Update Register Activity Layout...
        // Wait, looking at Step 20788:
        // Layout has etPassword but NO etConfirmPassword.
        // It has etAccountNumber, etBankName, etAccountName, etPassword.

        // So I should remove confirm password logic from Java.

        btnShowPassword = findViewById(R.id.btnShowPassword); // Wait, Layout in 20788 uses etPassword
                                                              // inputType="textPassword" but NO eye icon in the layout!
        // Step 20788 layout for etPassword:
        // <EditText id="@+id/etPassword" ... inputType="textPassword" ... />
        // usage of drawable/bg_input_field.
        // NO FrameLayout wrapping it with an eye icon.

        // Okay, I need to match the Java code to the NEW XML.
        // The new XML (Step 20788) has:
        // - cardPersonal, cardBusiness
        // - etAccountNumber, etBankName, etAccountName, etPassword
        // - cbTerms, tvTermsText
        // - btnRegister
        // - btnBack

        // It DOES NOT have:
        // - rgAccountType
        // - etConfirmPassword
        // - btnShowPassword (eye icon)

        cbTerms = findViewById(R.id.cbTerms);
        tvTermsText = findViewById(R.id.tvTermsText);
        com.google.android.material.button.MaterialButton btnRegister = findViewById(R.id.btnRegister);

        // Default Selection
        updateSelectionState(false);

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Card Selection
        cardPersonal.setOnClickListener(v -> updateSelectionState(false));
        cardBusiness.setOnClickListener(v -> updateSelectionState(true));

        // Register button
        btnRegister.setOnClickListener(v -> handleRegister());

        setupTermsText();
    }

    private void updateSelectionState(boolean businesses) {
        this.isBusiness = businesses;
        cardPersonal.setSelected(!businesses);
        cardBusiness.setSelected(businesses);
    }

    private void setupTermsText() {
        String fullText = "Bằng việc Đăng ký, bạn đã đồng ý với Điều khoản & Điều kiện cũng Chính sách bảo mật của softbank ePOS";
        SpannableString spannableString = new SpannableString(fullText);

        // Clickable span for "Điều khoản & Điều kiện"
        ClickableSpan termsSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                showTermsDialog();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Color.parseColor("#4A9EFF"));
                ds.setUnderlineText(false);
            }
        };

        // Clickable span for "Chính sách bảo mật"
        ClickableSpan privacySpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                showPrivacyDialog();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Color.parseColor("#4A9EFF"));
                ds.setUnderlineText(false);
            }
        };

        int termsStart = fullText.indexOf("Điều khoản & Điều kiện");
        int termsEnd = termsStart + "Điều khoản & Điều kiện".length();
        spannableString.setSpan(termsSpan, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int privacyStart = fullText.indexOf("Chính sách bảo mật");
        int privacyEnd = privacyStart + "Chính sách bảo mật".length();
        spannableString.setSpan(privacySpan, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        tvTermsText.setText(spannableString);
        tvTermsText.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void showTermsDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_terms);
        dialog.setCanceledOnTouchOutside(true);

        TextView tvDialogTitle = dialog.findViewById(R.id.tvDialogTitle);
        TextView tvDialogContent = dialog.findViewById(R.id.tvDialogContent);

        tvDialogTitle.setText("Dieu khoan & Dieu kien");
        tvDialogContent.setText(getTermsContent());

        dialog.show();
    }

    private void showPrivacyDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_terms);
        dialog.setCanceledOnTouchOutside(true);

        TextView tvDialogTitle = dialog.findViewById(R.id.tvDialogTitle);
        TextView tvDialogContent = dialog.findViewById(R.id.tvDialogContent);

        tvDialogTitle.setText("Chính sách bảo mật");
        tvDialogContent.setText(getPrivacyContent());

        dialog.show();
    }

    private String getTermsContent() {
        return readRawTextFile(R.raw.terms_conditions);
    }

    private String getPrivacyContent() {
        return readRawTextFile(R.raw.privacy_policy);
    }

    private String readRawTextFile(int resourceId) {
        try {
            InputStream inputStream = getResources().openRawResource(resourceId);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            return content.toString().trim();
        } catch (Exception e) {
            Log.e("RegisterActivity", "Error reading raw text file", e);
            return "Khong the tai noi dung. Vui long thu lai sau.";
        }
    }

    private void handleRegister() {
        // Get selected account type
        String accountType = isBusiness ? "Business" : "Personal";

        String accountNumber = etAccountNumber.getText().toString().trim();
        String bankName = etBankName.getText().toString().trim();
        String accountName = etAccountName.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Validation
        if (accountNumber.isEmpty()) {
            etAccountNumber.setError("Please enter a username");
            etAccountNumber.requestFocus();
            return;
        }

        if (accountNumber.length() < 3) {
            etAccountNumber.setError("Username is too short");
            etAccountNumber.requestFocus();
            return;
        }

        if (bankName.isEmpty()) {
            etBankName.setError("Please enter bank name");
            etBankName.requestFocus();
            return;
        }

        if (accountName.isEmpty()) {
            etAccountName.setError("Please enter your full name");
            etAccountName.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Please enter a password");
            etPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        // Check terms and conditions
        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "Please agree to the Terms & Conditions", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save user to database with encrypted credentials
        final String finalAccountName = accountName; // Display Name
        final String finalUsername = accountNumber; // Login Username
        final String finalPassword = password;
        final String finalAccountType = accountType;

        new Thread(() -> {
            try {
                com.example.mysoftpos.data.local.AppDatabase db = com.example.mysoftpos.data.local.AppDatabase
                        .getInstance(this);
                com.example.mysoftpos.data.local.dao.UserDao userDao = db.userDao();

                // Hash username and password with SHA-256
                String usernameHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(finalUsername);
                String passwordHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(finalPassword);

                // Check if username already exists
                if (userDao.existsByUsernameHash(usernameHash)) {
                    runOnUiThread(() -> {
                        etAccountNumber.setError("Username already exists");
                        etAccountNumber.requestFocus();
                    });
                    return;
                }

                // Create and insert user entity
                com.example.mysoftpos.data.local.entity.UserEntity user = new com.example.mysoftpos.data.local.entity.UserEntity(
                        usernameHash,
                        passwordHash,
                        finalAccountName, // Display name (not encrypted)
                        "USER" // Default role
                );

                userDao.insert(user);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Registration Successful! Type: " + finalAccountType, Toast.LENGTH_SHORT)
                            .show();
                    finish();
                });

            } catch (Exception e) {
                Log.e("RegisterActivity", "Registration failed", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Registration Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}
