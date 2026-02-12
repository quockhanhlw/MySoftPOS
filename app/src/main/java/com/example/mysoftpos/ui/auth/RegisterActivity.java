package com.example.mysoftpos.ui.auth;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.dao.UserDao;
import com.example.mysoftpos.data.local.entity.UserEntity;
import com.example.mysoftpos.ui.BaseActivity;

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
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class RegisterActivity extends BaseActivity {

    private ViewFlipper viewFlipper;

    // Step 1 Views
    private EditText etFullName;
    private EditText etDob;
    private EditText etPhone;
    private EditText etEmail;
    private EditText etAddress;

    // Step 2 Views
    private EditText etUsername;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private CheckBox cbTerms;
    private TextView tvTermsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Main Views
        viewFlipper = findViewById(R.id.viewFlipper);
        View btnBack = findViewById(R.id.btnBack);
        TextView tvLogin = findViewById(R.id.tvLogin);

        // Initialize Step 1 Views
        etFullName = findViewById(R.id.etFullName);
        etDob = findViewById(R.id.etDob);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        etAddress = findViewById(R.id.etAddress);
        View btnNextStep = findViewById(R.id.btnNextStep);

        // Initialize Step 2 Views
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        cbTerms = findViewById(R.id.cbTerms);
        tvTermsText = findViewById(R.id.tvTermsText);
        View btnBackStep = findViewById(R.id.btnBackStep);
        View btnRegister = findViewById(R.id.btnRegister);

        // Listeners
        btnBack.setOnClickListener(v -> handleBack());
        if (tvLogin != null)
            tvLogin.setOnClickListener(v -> finish()); // Just finish to go back to Login/Welcome

        if (btnNextStep != null)
            btnNextStep.setOnClickListener(v -> handleNextStep());
        if (btnBackStep != null)
            btnBackStep.setOnClickListener(v -> viewFlipper.showPrevious());
        if (btnRegister != null)
            btnRegister.setOnClickListener(v -> handleRegister());

        setupTermsText();
    }

    private void handleBack() {
        if (viewFlipper.getDisplayedChild() > 0) {
            viewFlipper.showPrevious();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void handleNextStep() {
        String fullName = etFullName.getText().toString().trim();
        String dob = etDob.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String address = etAddress.getText().toString().trim();

        if (fullName.isEmpty()) {
            etFullName.setError("Required");
            return;
        }
        if (dob.isEmpty()) {
            etDob.setError("Required");
            return;
        }
        if (phone.isEmpty()) {
            etPhone.setError("Required");
            return;
        }
        if (email.isEmpty()) {
            etEmail.setError("Required");
            return;
        }
        // Address optional? Let's make it required as per original code
        if (address.isEmpty()) {
            etAddress.setError("Required");
            return;
        }

        // Proceed to Step 2
        viewFlipper.showNext();
    }

    private void handleRegister() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (username.isEmpty()) {
            etUsername.setError("Required");
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError("Required");
            return;
        }
        if (confirmPassword.isEmpty()) {
            etConfirmPassword.setError("Required");
            return;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords mismatch");
            return;
        }

        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "Please agree to Terms", Toast.LENGTH_SHORT).show();
            return;
        }

        // Gather all data
        String fullName = etFullName.getText().toString().trim();
        String dob = etDob.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();

        registerUser(fullName, dob, phone, email, username, password);
    }

    private void registerUser(String fullName, String dob, String phone, String email, String username,
            String password) {
        new Thread(() -> {
            try {
                com.example.mysoftpos.data.local.AppDatabase db = com.example.mysoftpos.data.local.AppDatabase
                        .getInstance(this);
                UserDao userDao = db.userDao();

                // Check duplicates
                String usernameHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(username);

                if (userDao.existsByUsernameHash(usernameHash)) {
                    runOnUiThread(() -> {
                        etUsername.setError("Username taken");
                        viewFlipper.setDisplayedChild(1);
                    });
                    return;
                }
                if (userDao.existsByEmail(email)) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Email already registered", Toast.LENGTH_SHORT).show();
                        viewFlipper.setDisplayedChild(0);
                    });
                    return;
                }
                if (userDao.existsByPhone(phone)) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Phone already registered", Toast.LENGTH_SHORT).show();
                        viewFlipper.setDisplayedChild(0);
                    });
                    return;
                }

                String passwordHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(password);

                UserEntity user = new UserEntity(
                        usernameHash,
                        passwordHash,
                        fullName,
                        "USER",
                        email,
                        phone,
                        dob);

                userDao.insert(user);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show();
                    finish();
                });

            } catch (Exception e) {
                Log.e("RegisterActivity", "Registration failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void setupTermsText() {
        if (tvTermsText == null)
            return;

        String fullText = getString(R.string.register_terms_text);
        SpannableString spannableString = new SpannableString(fullText);

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

        String termsTarget = "Terms & Conditions";
        int termsStart = fullText.indexOf(termsTarget);
        if (termsStart >= 0) {
            spannableString.setSpan(termsSpan, termsStart, termsStart + termsTarget.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        tvTermsText.setText(spannableString);
        tvTermsText.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void showTermsDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_terms);
        dialog.setCanceledOnTouchOutside(true);
        // ... dialog logic (existing)
        dialog.show();
    }
}
