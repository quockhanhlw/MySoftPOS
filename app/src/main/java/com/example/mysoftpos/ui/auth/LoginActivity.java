package com.example.mysoftpos.ui.auth;

import com.example.mysoftpos.R;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;

import com.example.mysoftpos.ui.BaseActivity;

public class LoginActivity extends BaseActivity {

    private EditText etUsername;
    private EditText etPassword;
    private boolean passwordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);

        // Password toggle
        etPassword.setCompoundDrawablesRelativeWithIntrinsicBounds(
                etPassword.getCompoundDrawablesRelative()[0], null,
                getDrawable(R.drawable.ic_baseline_visibility_off_24), null);
        etPassword.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                Drawable end = etPassword.getCompoundDrawablesRelative()[2];
                if (end != null && event
                        .getRawX() >= (etPassword.getRight() - end.getBounds().width() - etPassword.getPaddingEnd())) {
                    passwordVisible = !passwordVisible;
                    if (passwordVisible) {
                        etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                        etPassword.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                etPassword.getCompoundDrawablesRelative()[0], null,
                                getDrawable(R.drawable.ic_baseline_visibility_24), null);
                    } else {
                        etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        etPassword.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                etPassword.getCompoundDrawablesRelative()[0], null,
                                getDrawable(R.drawable.ic_baseline_visibility_off_24), null);
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

        // Back button
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Password visibility is handled by TextInputLayout endIcon

        tvForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        // Sign up link
        if (tvSignUp != null) {
            tvSignUp.setOnClickListener(v -> {
                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
            });
        }

        btnLogin.setOnClickListener(v -> handleLogin());
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

        // Authenticate in background thread
        final String finalUsername = username;
        final String finalPassword = password;

        new Thread(() -> {
            try {
                // Check admin (from Config)
                com.example.mysoftpos.utils.config.ConfigManager config = com.example.mysoftpos.utils.config.ConfigManager
                        .getInstance(this);
                if (finalUsername.equals(config.getAdminUsername())
                        && finalPassword.equals(config.getAdminPassword())) {
                    runOnUiThread(() -> navigateToDashboard("ADMIN", config.getAdminUsername()));
                    return;
                }

                // Check database for registered users
                com.example.mysoftpos.data.local.AppDatabase db = com.example.mysoftpos.data.local.AppDatabase
                        .getInstance(this);
                com.example.mysoftpos.data.local.dao.UserDao userDao = db.userDao();

                // 1. Try finding by Username Hash
                String usernameHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(finalUsername);
                com.example.mysoftpos.data.local.entity.UserEntity user = userDao.findByUsernameHash(usernameHash);

                // 2. If not found, try finding by Email
                if (user == null) {
                    user = userDao.findByEmail(finalUsername);
                }

                // 3. If still not found, try finding by Phone
                if (user == null) {
                    user = userDao.findByPhone(finalUsername);
                }

                if (user != null) {
                    String inputPasswordHash = com.example.mysoftpos.utils.security.PasswordUtils
                            .hashSHA256(finalPassword);
                    if (inputPasswordHash.equals(user.passwordHash)) {
                        String displayName = user.displayName != null ? user.displayName : "User";

                        final com.example.mysoftpos.data.local.entity.UserEntity finalUser = user;
                        runOnUiThread(() -> navigateToDashboard(finalUser.role, displayName));
                        return;
                    }
                }

                // Invalid credentials
                runOnUiThread(() -> {
                    Toast.makeText(this, "Invalid username or password!", Toast.LENGTH_SHORT).show();
                    etPassword.setText("");
                    etPassword.requestFocus();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Login Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void navigateToDashboard(String role, String username) {
        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(LoginActivity.this, com.example.mysoftpos.ui.dashboard.MainDashboardActivity.class);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USER_ROLE, role);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME, username);
        startActivity(intent);
        finish();
    }
}
