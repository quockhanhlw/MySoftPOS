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

        // PA-DSS 3.x: Check for session timeout notification
        if (getIntent().getBooleanExtra("SESSION_TIMEOUT", false)) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
            getIntent().removeExtra("SESSION_TIMEOUT");
        }

        final String finalUsername = username;
        final String finalPassword = password;

        com.example.mysoftpos.di.ServiceLocator.getInstance(this)
                .getDispatcherProvider().io().execute(() -> {
                    try {
                        com.example.mysoftpos.utils.config.ConfigManager config = com.example.mysoftpos.utils.config.ConfigManager
                                .getInstance(LoginActivity.this);

                        com.example.mysoftpos.data.local.AppDatabase db = com.example.mysoftpos.data.local.AppDatabase
                                .getInstance(LoginActivity.this);
                        com.example.mysoftpos.data.local.dao.UserDao userDao = db.userDao();

                        // Find user by phone → email → username hash
                        com.example.mysoftpos.data.local.entity.UserEntity user = userDao.findByPhone(finalUsername);
                        if (user == null)
                            user = userDao.findByEmail(finalUsername);
                        if (user == null) {
                            String usernameHash = com.example.mysoftpos.utils.security.PasswordUtils
                                    .hashSHA256(finalUsername);
                            user = userDao.findByUsernameHash(usernameHash);
                        }

                        if (user != null) {
                            // PA-DSS 3.1.6: Check account lockout (6 attempts → 30 min lock)
                            if (user.lockedUntil > System.currentTimeMillis()) {
                                long remainingMs = user.lockedUntil - System.currentTimeMillis();
                                int remainingMin = (int) (remainingMs / 60000) + 1;
                                com.example.mysoftpos.utils.security.AuditLogger.log(
                                        LoginActivity.this, finalUsername, "LOGIN_BLOCKED",
                                        false, "LoginActivity", "Account locked, " + remainingMin + " min remaining");
                                runOnUiThread(() -> {
                                    if (isDestroyed() || isFinishing())
                                        return;
                                    Toast.makeText(LoginActivity.this,
                                            "Account locked. Try again in " + remainingMin + " minutes.",
                                            Toast.LENGTH_LONG).show();
                                });
                                return;
                            }

                            // PA-DSS 2.x: Verify password (PBKDF2 + SHA-256 legacy compat)
                            boolean passwordMatch = com.example.mysoftpos.utils.security.PasswordUtils
                                    .verifyPassword(finalPassword, user.passwordHash);

                            if (passwordMatch) {
                                // PA-DSS 3.x: Reset failed attempts on success
                                user.failedLoginAttempts = 0;
                                user.lockedUntil = 0;
                                userDao.update(user);

                                String displayName = user.displayName != null ? user.displayName : "User";
                                config.resetServerConfig();
                                if (user.serverIp != null && !user.serverIp.isEmpty() && user.serverPort > 0) {
                                    config.setServerIp(user.serverIp);
                                    config.setServerPort(user.serverPort);
                                }

                                // PA-DSS 3.x: Start session timer
                                com.example.mysoftpos.utils.security.SessionManager.startSession();

                                // PA-DSS 4.x: Log successful login
                                com.example.mysoftpos.utils.security.AuditLogger.log(
                                        LoginActivity.this, finalUsername, "LOGIN",
                                        true, "LoginActivity", "User logged in: " + user.role);

                                final com.example.mysoftpos.data.local.entity.UserEntity finalUser = user;
                                runOnUiThread(() -> {
                                    if (isDestroyed() || isFinishing())
                                        return;
                                    navigateToDashboard(finalUser.id, finalUser.role, displayName,
                                            finalUser.phone, finalUser.email);
                                });
                                return;
                            } else {
                                // PA-DSS 3.1.6: Increment failed attempts
                                user.failedLoginAttempts++;
                                if (user.failedLoginAttempts >= 6) {
                                    user.lockedUntil = System.currentTimeMillis() + (30 * 60 * 1000L);
                                    user.failedLoginAttempts = 0;
                                    userDao.update(user);
                                    com.example.mysoftpos.utils.security.AuditLogger.log(
                                            LoginActivity.this, finalUsername, "ACCOUNT_LOCKED",
                                            false, "LoginActivity", "Locked after 6 failed attempts");
                                    runOnUiThread(() -> {
                                        if (isDestroyed() || isFinishing())
                                            return;
                                        Toast.makeText(LoginActivity.this,
                                                "Account locked for 30 minutes.",
                                                Toast.LENGTH_LONG).show();
                                        etPassword.setText("");
                                    });
                                    return;
                                }
                                userDao.update(user);
                                com.example.mysoftpos.utils.security.AuditLogger.log(
                                        LoginActivity.this, finalUsername, "LOGIN_FAILED",
                                        false, "LoginActivity",
                                        "Failed attempt " + user.failedLoginAttempts + "/6");
                            }
                        } else {
                            com.example.mysoftpos.utils.security.AuditLogger.log(
                                    LoginActivity.this, finalUsername, "LOGIN_FAILED",
                                    false, "LoginActivity", "User not found");
                        }

                        runOnUiThread(() -> {
                            if (isDestroyed() || isFinishing())
                                return;
                            Toast.makeText(LoginActivity.this, "Invalid username or password!",
                                    Toast.LENGTH_SHORT).show();
                            etPassword.setText("");
                            etPassword.requestFocus();
                        });

                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            if (isDestroyed() || isFinishing())
                                return;
                            Toast.makeText(LoginActivity.this, "Login Error: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private void navigateToDashboard(long userId, String role, String displayName, String phone, String email) {
        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(LoginActivity.this, com.example.mysoftpos.ui.dashboard.MainDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USER_ROLE, role);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USER_ID, userId);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME, phone != null ? phone : email);
        intent.putExtra("DISPLAY_NAME", displayName);
        intent.putExtra("USER_EMAIL", email);
        startActivity(intent);
        finish();
    }
}
