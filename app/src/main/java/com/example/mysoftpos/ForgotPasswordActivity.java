package com.example.mysoftpos;

import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class ForgotPasswordActivity extends AppCompatActivity {

    private ImageView btnBack;
    private EditText etNewPassword;
    private EditText etConfirmPassword;
    private ImageView btnShowNewPassword;
    private ImageView btnShowConfirmPassword;
    private ImageView btnClearConfirmPassword;
    private CardView btnConfirm;
    private boolean isNewPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Initialize views
        btnBack = findViewById(R.id.btnBack);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnShowNewPassword = findViewById(R.id.btnShowNewPassword);
        btnShowConfirmPassword = findViewById(R.id.btnShowConfirmPassword);
        btnClearConfirmPassword = findViewById(R.id.btnClearConfirmPassword);
        btnConfirm = findViewById(R.id.btnConfirm);

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Show/Hide new password
        btnShowNewPassword.setOnClickListener(v -> toggleNewPasswordVisibility());

        // Show/Hide confirm password
        btnShowConfirmPassword.setOnClickListener(v -> toggleConfirmPasswordVisibility());

        // Clear confirm password
        btnClearConfirmPassword.setOnClickListener(v -> etConfirmPassword.setText(""));

        // Confirm button
        btnConfirm.setOnClickListener(v -> handleConfirm());
    }

    private void toggleNewPasswordVisibility() {
        if (isNewPasswordVisible) {
            etNewPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            btnShowNewPassword.setImageResource(R.drawable.ic_eye_off);
            isNewPasswordVisible = false;
        } else {
            etNewPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            btnShowNewPassword.setImageResource(R.drawable.ic_eye);
            isNewPasswordVisible = true;
        }
        etNewPassword.setSelection(etNewPassword.getText().length());
    }

    private void toggleConfirmPasswordVisibility() {
        if (isConfirmPasswordVisible) {
            etConfirmPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            btnShowConfirmPassword.setImageResource(R.drawable.ic_eye_off);
            isConfirmPasswordVisible = false;
        } else {
            etConfirmPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            btnShowConfirmPassword.setImageResource(R.drawable.ic_eye);
            isConfirmPasswordVisible = true;
        }
        etConfirmPassword.setSelection(etConfirmPassword.getText().length());
    }

    private void handleConfirm() {
        String newPassword = etNewPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (newPassword.isEmpty()) {
            etNewPassword.setError("Vui lòng nhập mật khẩu mới");
            etNewPassword.requestFocus();
            return;
        }

        if (newPassword.length() < 8) {
            etNewPassword.setError("Mật khẩu phải có ít nhất 8 ký tự");
            etNewPassword.requestFocus();
            return;
        }

        if (confirmPassword.isEmpty()) {
            etConfirmPassword.setError("Vui lòng nhập lại mật khẩu");
            etConfirmPassword.requestFocus();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            etConfirmPassword.setError("Mật khẩu không khớp");
            etConfirmPassword.requestFocus();
            return;
        }

        // TODO: Implement actual password reset logic
        Toast.makeText(this, "Đổi mật khẩu thành công!", Toast.LENGTH_SHORT).show();
        finish();
    }
}

