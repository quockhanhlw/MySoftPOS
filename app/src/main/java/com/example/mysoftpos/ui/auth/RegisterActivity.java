package com.example.mysoftpos.ui.auth;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.dao.UserDao;
import com.example.mysoftpos.data.local.entity.UserEntity;
import com.example.mysoftpos.ui.BaseActivity;

import android.annotation.SuppressLint;
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

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class RegisterActivity extends BaseActivity {
    private static final String TAG = "RegisterActivity";
    private static final String REGISTERED_ADMIN_ROLE = "ADMIN";

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

    @SuppressLint("ClickableViewAccessibility")
    private void setupPasswordToggle(EditText editText, boolean isMainPassword) {
        editText.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                android.graphics.drawable.Drawable end = editText.getCompoundDrawablesRelative()[2];
                if (end != null && event.getRawX() >= (editText.getRight() - end.getBounds().width() - editText.getPaddingEnd())) {
                    v.performClick();
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
                                AppCompatResources.getDrawable(this, R.drawable.ic_baseline_visibility_24), null);
                    } else {
                        editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        editText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                editText.getCompoundDrawablesRelative()[0], null,
                                AppCompatResources.getDrawable(this, R.drawable.ic_baseline_visibility_off_24), null);
                    }
                    editText.setSelection(editText.getText().length());
                    return true;
                }
            }
            return false;
        });
    }

    private void handleRegister() {
        String fullName = getTrimmedText(etFullName);
        String phone = getTrimmedText(etPhone);
        String email = getTrimmedText(etEmail);
        String password = getTrimmedText(etPassword);
        String confirmPassword = getTrimmedText(etConfirmPassword);

        if (requireValue(etFullName, fullName)
                || requireValue(etEmail, email)
                || requireValue(etPhone, phone)
                || requireValue(etPassword, password)
                || requireValue(etConfirmPassword, confirmPassword)) {
            return;
        }
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError(getString(R.string.register_passwords_do_not_match));
            etConfirmPassword.requestFocus();
            return;
        }

        if (!cbTerms.isChecked()) {
            showToast(R.string.register_agree_terms);
            return;
        }


        registerUser(fullName, phone, email, email, password);
    }

    private void registerUser(String fullName, String phone, String email, String username,
            String password) {
        com.example.mysoftpos.data.remote.api.ApiService api =
                com.example.mysoftpos.data.remote.api.ApiClient.getService(this);

        api.register(new com.example.mysoftpos.data.remote.api.ApiService.RegisterRequest(
                username, password, fullName, phone, email
        )).enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(
                    @NonNull retrofit2.Call<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse> call,
                    @NonNull retrofit2.Response<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cacheUserLocally(fullName, phone, email, username, password);
                    runOnUiThread(() -> {
                        showToast(R.string.register_success);
                        finish();
                    });
                } else {
                    String errorMsg = getString(R.string.register_failed);
                    try (okhttp3.ResponseBody errorBody = response.errorBody()) {
                        if (errorBody != null) {
                            String body = errorBody.string();
                            if (body.contains("already registered") || body.contains("already exists")) {
                                errorMsg = getString(R.string.register_admin_exists);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    String finalMsg = errorMsg;
                    runOnUiThread(() -> Toast.makeText(RegisterActivity.this, finalMsg, Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onFailure(
                    @NonNull retrofit2.Call<com.example.mysoftpos.data.remote.api.ApiService.LoginResponse> call,
                    @NonNull Throwable t) {
                Log.w(TAG, "Admin registration requires backend connectivity: " + t.getMessage());
                runOnUiThread(() -> showToast(R.string.register_backend_required, Toast.LENGTH_LONG));
            }
        });
    }

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
                            REGISTERED_ADMIN_ROLE, email, phone, null);
                    userDao.insert(user);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to cache user locally: " + e.getMessage());
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
            Log.e(TAG, "Error reading raw file", e);
            return "";
        }
    }

    private String getTrimmedText(EditText editText) {
        return String.valueOf(editText.getText()).trim();
    }

    private boolean requireValue(EditText editText, String value) {
        if (!value.isEmpty()) {
            return false;
        }
        editText.setError(getString(R.string.common_required));
        editText.requestFocus();
        return true;
    }

    private void showToast(int stringResId) {
        showToast(stringResId, Toast.LENGTH_SHORT);
    }

    private void showToast(int stringResId, int duration) {
        Toast.makeText(this, stringResId, duration).show();
    }
}
