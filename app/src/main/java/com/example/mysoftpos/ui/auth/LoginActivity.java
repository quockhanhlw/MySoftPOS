package com.example.mysoftpos.ui.auth;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.dao.UserDao;
import com.example.mysoftpos.data.local.entity.UserEntity;
import com.example.mysoftpos.ui.dashboard.MainDashboardActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername;
    private EditText etPassword;
    private ImageView btnShowPassword;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        ImageView btnBack = findViewById(R.id.btnBack);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnShowPassword = findViewById(R.id.btnShowPassword);
        TextView tvForgotPassword = findViewById(R.id.tvForgotPassword);
        CardView btnLogin = findViewById(R.id.btnLogin);

        btnBack.setOnClickListener(v -> finish());
        btnShowPassword.setOnClickListener(v -> togglePasswordVisibility());

        tvForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        btnLogin.setOnClickListener(v -> handleLogin());
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            btnShowPassword.setImageResource(R.drawable.ic_eye_off);
            isPasswordVisible = false;
        } else {
            etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            btnShowPassword.setImageResource(R.drawable.ic_eye);
            isPasswordVisible = true;
        }
        etPassword.setSelection(etPassword.getText().length());
    }

    private void handleLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty()) {
            etUsername.setError("Vui lòng nhập tên đăng nhập");
            etUsername.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Vui lòng nhập mật khẩu");
            etPassword.requestFocus();
            return;
        }

        // Authenticate in background thread
        final String finalUsername = username;
        final String finalPassword = password;

        new Thread(() -> {
            try {
                // Check hardcoded admin first (always available)
                if (finalUsername.equals("admin") && finalPassword.equals("admin123")) {
                    runOnUiThread(() -> navigateToDashboard("ADMIN", "admin"));
                    return;
                }

                // Check database for registered users
                com.example.mysoftpos.data.local.AppDatabase db = com.example.mysoftpos.data.local.AppDatabase
                        .getInstance(this);
                com.example.mysoftpos.data.local.dao.UserDao userDao = db.userDao();

                String usernameHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(finalUsername);
                com.example.mysoftpos.data.local.entity.UserEntity user = userDao.findByUsernameHash(usernameHash);

                if (user != null) {
                    String inputPasswordHash = com.example.mysoftpos.utils.security.PasswordUtils
                            .hashSHA256(finalPassword);
                    if (inputPasswordHash.equals(user.passwordHash)) {
                        String displayName = user.displayName != null ? user.displayName : finalUsername;
                        runOnUiThread(() -> navigateToDashboard(user.role, displayName));
                        return;
                    }
                }

                // Invalid credentials
                runOnUiThread(() -> {
                    Toast.makeText(this, "Sai tên đăng nhập hoặc mật khẩu!", Toast.LENGTH_SHORT).show();
                    etPassword.setText("");
                    etPassword.requestFocus();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Lỗi đăng nhập: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void navigateToDashboard(String role, String username) {
        Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(LoginActivity.this, MainDashboardActivity.class);
        intent.putExtra("USER_ROLE", role);
        intent.putExtra("USERNAME", username);
        startActivity(intent);
        finish();
    }
}
