package com.example.mysoftpos;
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
        // Fixed accounts for testing
        boolean isValidAccount = false;

        if (username.equals("admin") && password.equals("admin123")) {
            isValidAccount = true;
        } else if (username.equals("techcombank") && password.equals("tcb2026")) {
            isValidAccount = true;
        } else if (username.equals("test") && password.equals("test123")) {
            isValidAccount = true;
        }

        if (!isValidAccount) {
            Toast.makeText(this, "Sai tên đăng nhập hoặc mật khẩu!", Toast.LENGTH_SHORT).show();
            etPassword.setText("");
            etPassword.requestFocus();
            return;
        }

        Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();

        // Navigate to Dashboard (kỹ thuật)
        Intent intent = new Intent(LoginActivity.this, MainDashboardActivity.class);
        startActivity(intent);
        finish();
    }
}