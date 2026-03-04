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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class RegisterActivity extends BaseActivity {

    // Step 1 Views
    private EditText etFullName;
    private EditText etPhone;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private CheckBox cbTerms;
    private TextView tvTermsText;
    private boolean passwordVisible = false;
    private boolean confirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Bind Views
        etFullName = findViewById(R.id.etFullName);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        cbTerms = findViewById(R.id.cbTerms);
        tvTermsText = findViewById(R.id.tvTermsText);

        MaterialButton btnRegister = findViewById(R.id.btnRegister);
        TextView tvLogin = findViewById(R.id.tvLogin);
        View btnBack = findViewById(R.id.btnBack);

        // Back button
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Navigate to Login
        if (tvLogin != null)
            tvLogin.setOnClickListener(v -> {
                startActivity(new android.content.Intent(this, LoginActivity.class));
                finish();
            });

        if (btnRegister != null)
            btnRegister.setOnClickListener(v -> handleRegister());

        setupPasswordToggle(etPassword, true);
        setupPasswordToggle(etConfirmPassword, false);
        setupTermsText();
    }

    private void setupPasswordToggle(EditText editText, boolean isMainPassword) {
        editText.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                android.graphics.drawable.Drawable end = editText.getCompoundDrawablesRelative()[2];
                if (end != null && event.getRawX() >= (editText.getRight() - end.getBounds().width() - editText.getPaddingEnd())) {
                    boolean visible;
                    if (isMainPassword) {
                        passwordVisible = !passwordVisible;
                        visible = passwordVisible;
                    } else {
                        confirmPasswordVisible = !confirmPasswordVisible;
                        visible = confirmPasswordVisible;
                    }
                    if (visible) {
                        editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                        editText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                editText.getCompoundDrawablesRelative()[0], null,
                                getDrawable(R.drawable.ic_baseline_visibility_24), null);
                    } else {
                        editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        editText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                editText.getCompoundDrawablesRelative()[0], null,
                                getDrawable(R.drawable.ic_baseline_visibility_off_24), null);
                    }
                    editText.setSelection(editText.getText().length());
                    return true;
                }
            }
            return false;
        });
    }

    private void handleRegister() {
        String fullName = etFullName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (fullName.isEmpty()) {
            etFullName.setError("Required");
            return;
        }
        if (email.isEmpty()) {
            etEmail.setError("Required");
            return;
        }
        if (phone.isEmpty()) {
            etPhone.setError("Required");
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
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "Please agree to Terms", Toast.LENGTH_SHORT).show();
            return;
        }

        // Use Email as Username for simplicity in this new design
        String username = email;

        registerUser(fullName, phone, email, username, password);
    }

    private void registerUser(String fullName, String phone, String email, String username,
            String password) {
        // PRIMARY: Register via backend API
        com.example.mysoftpos.data.remote.api.ApiService api =
                com.example.mysoftpos.data.remote.api.ApiClient.getService(this);

        api.register(new com.example.mysoftpos.data.remote.api.ApiService.RegisterRequest(
                username, password, fullName, phone, email
        )).enqueue(new retrofit2.Callback<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse>() {
            @Override
            public void onResponse(
                    retrofit2.Call<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse> call,
                    retrofit2.Response<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Cache user locally for offline login
                    cacheUserLocally(fullName, phone, email, username, password);
                    runOnUiThread(() -> {
                        Toast.makeText(RegisterActivity.this,
                                "Registration Successful!", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } else {
                    String errorMsg = "Registration failed";
                    try {
                        if (response.errorBody() != null) {
                            String body = response.errorBody().string();
                            if (body.contains("already exists"))
                                errorMsg = "Username already exists";
                        }
                    } catch (Exception ignored) {}
                    String finalMsg = errorMsg;
                    runOnUiThread(() -> {
                        Toast.makeText(RegisterActivity.this, finalMsg, Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onFailure(
                    retrofit2.Call<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse> call,
                    Throwable t) {
                Log.w("RegisterActivity",
                        "API unreachable, falling back to offline registration: " + t.getMessage());
                // FALLBACK: Register locally only
                registerUserLocally(fullName, phone, email, username, password);
            }
        });
    }

    /** Cache a backend-registered user into local Room for offline login */
    private void cacheUserLocally(String fullName, String phone, String email,
            String username, String password) {
        new Thread(() -> {
            try {
                com.example.mysoftpos.data.local.AppDatabase db =
                        com.example.mysoftpos.data.local.AppDatabase.getInstance(this);
                UserDao userDao = db.userDao();
                String usernameHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(username);

                if (!userDao.existsByUsernameHash(usernameHash)) {
                    // PA-DSS 2.x: Use PBKDF2 for password hashing, not SHA-256
                    String passwordHash = com.example.mysoftpos.utils.security.PasswordUtils.hashPassword(password);
                    UserEntity user = new UserEntity(usernameHash, passwordHash, fullName,
                            "ADMIN", email, phone, null);
                    userDao.insert(user);
                }
            } catch (Exception e) {
                Log.w("RegisterActivity", "Failed to cache user locally: " + e.getMessage());
            }
        }).start();
    }

    /** Fallback: Register user locally only (when backend is unreachable) */
    private void registerUserLocally(String fullName, String phone, String email,
            String username, String password) {
        new Thread(() -> {
            try {
                com.example.mysoftpos.data.local.AppDatabase db =
                        com.example.mysoftpos.data.local.AppDatabase.getInstance(this);
                UserDao userDao = db.userDao();

                String usernameHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(username);

                if (userDao.existsByUsernameHash(usernameHash) || userDao.existsByEmail(email)) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Email already registered", Toast.LENGTH_SHORT).show();
                        etEmail.setError("Already registered");
                    });
                    return;
                }
                if (userDao.existsByPhone(phone)) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Phone already registered", Toast.LENGTH_SHORT).show();
                        etPhone.setError("Already registered");
                    });
                    return;
                }

                String passwordHash = com.example.mysoftpos.utils.security.PasswordUtils.hashPassword(password);
                UserEntity user = new UserEntity(usernameHash, passwordHash, fullName,
                        "ADMIN", email, phone, null);
                userDao.insert(user);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Registration Successful (Offline)!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                Log.e("RegisterActivity", "Registration failed", e);
                runOnUiThread(() -> Toast.makeText(this,
                        "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void setupTermsText() {
        if (tvTermsText == null)
            return;

        String fullText = getString(R.string.register_terms_text);
        SpannableString spannableString = new SpannableString(fullText);

        // "Terms & Conditions" clickable
        ClickableSpan termsSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                showDocumentDialog(getString(R.string.terms_title), R.raw.terms_conditions);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Color.parseColor("#4A9EFF"));
                ds.setUnderlineText(false);
            }
        };

        // Find clickable targets using string resources (works for all languages)
        String termsTarget = getString(R.string.terms_title);
        int termsStart = fullText.indexOf(termsTarget);
        if (termsStart >= 0) {
            spannableString.setSpan(termsSpan, termsStart, termsStart + termsTarget.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // "Privacy Policy" clickable
        ClickableSpan privacySpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                showDocumentDialog(getString(R.string.privacy_title), R.raw.privacy_policy);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Color.parseColor("#4A9EFF"));
                ds.setUnderlineText(false);
            }
        };

        String privacyTarget = getString(R.string.privacy_title);
        int privacyStart = fullText.indexOf(privacyTarget);
        if (privacyStart >= 0) {
            spannableString.setSpan(privacySpan, privacyStart, privacyStart + privacyTarget.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        tvTermsText.setText(spannableString);
        tvTermsText.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void showDocumentDialog(String title, int rawResId) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_terms);
        dialog.setCanceledOnTouchOutside(true);

        TextView tvTitle = dialog.findViewById(R.id.tvDialogTitle);
        TextView tvContent = dialog.findViewById(R.id.tvDialogContent);
        View btnClose = dialog.findViewById(R.id.btnCloseTerms);

        if (tvTitle != null) tvTitle.setText(title);
        if (tvContent != null) tvContent.setText(readRawTextFile(rawResId));
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private String readRawTextFile(int rawResId) {
        try {
            InputStream is = getResources().openRawResource(rawResId);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString().trim();
        } catch (Exception e) {
            Log.e("RegisterActivity", "Error reading raw file", e);
            return "";
        }
    }
}
