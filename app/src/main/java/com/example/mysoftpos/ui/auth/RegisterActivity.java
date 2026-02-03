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

    private RadioGroup rgAccountType;
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
        rgAccountType = findViewById(R.id.rgAccountType);
        RadioButton rbPersonal = findViewById(R.id.rbPersonal);
        etAccountNumber = findViewById(R.id.etAccountNumber);
        etBankName = findViewById(R.id.etBankName);
        etAccountName = findViewById(R.id.etAccountName);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnShowPassword = findViewById(R.id.btnShowPassword);
        btnShowConfirmPassword = findViewById(R.id.btnShowConfirmPassword);
        cbTerms = findViewById(R.id.cbTerms);
        tvTermsText = findViewById(R.id.tvTermsText);
        CardView btnRegister = findViewById(R.id.btnRegister);

        // Set default selection
        rbPersonal.setChecked(true);

        // Setup clickable terms text
        setupTermsText();

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Show/Hide password
        btnShowPassword.setOnClickListener(v -> togglePasswordVisibility());

        // Show/Hide confirm password
        btnShowConfirmPassword.setOnClickListener(v -> toggleConfirmPasswordVisibility());

        // Register button
        btnRegister.setOnClickListener(v -> handleRegister());
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

    private void handleRegister() {
        // Get selected account type
        int selectedTypeId = rgAccountType.getCheckedRadioButtonId();
        String accountType = "";
        if (selectedTypeId == R.id.rbPersonal) {
            accountType = "Hộ kinh doanh";
        } else if (selectedTypeId == R.id.rbBusiness) {
            accountType = "Doanh nghiệp";
        }

        String accountNumber = etAccountNumber.getText().toString().trim();
        String bankName = etBankName.getText().toString().trim();
        String accountName = etAccountName.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // Validation
        if (accountNumber.isEmpty()) {
            etAccountNumber.setError("Vui lòng nhập số tài khoản");
            etAccountNumber.requestFocus();
            return;
        }

        if (accountNumber.length() < 9) {
            etAccountNumber.setError("Số tài khoản phải có ít nhất 9 chữ số");
            etAccountNumber.requestFocus();
            return;
        }

        if (bankName.isEmpty()) {
            etBankName.setError("Vui lòng nhập tên ngân hàng");
            etBankName.requestFocus();
            return;
        }

        if (accountName.isEmpty()) {
            etAccountName.setError("Vui lòng nhập tên tài khoản");
            etAccountName.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Vui lòng nhập mật khẩu");
            etPassword.requestFocus();
            return;
        }

        if (password.length() < 8) {
            etPassword.setError("Mật khẩu phải có ít nhất 8 ký tự");
            etPassword.requestFocus();
            return;
        }

        if (confirmPassword.isEmpty()) {
            etConfirmPassword.setError("Vui lòng nhập lại mật khẩu");
            etConfirmPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Mật khẩu không khớp");
            etConfirmPassword.requestFocus();
            return;
        }

        // Check terms and conditions
        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "Vui lòng đồng ý với điều khoản và chính sách bảo mật", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save user to database with encrypted credentials
        final String finalAccountName = accountName;
        final String finalPassword = password;
        final String finalAccountType = accountType;

        new Thread(() -> {
            try {
                com.example.mysoftpos.data.local.AppDatabase db = com.example.mysoftpos.data.local.AppDatabase
                        .getInstance(this);
                com.example.mysoftpos.data.local.dao.UserDao userDao = db.userDao();

                // Hash username and password with SHA-256
                String usernameHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(finalAccountName);
                String passwordHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(finalPassword);

                // Check if username already exists
                if (userDao.existsByUsernameHash(usernameHash)) {
                    runOnUiThread(() -> {
                        etAccountName.setError("Tên tài khoản đã tồn tại");
                        etAccountName.requestFocus();
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
                    Toast.makeText(this, "Đăng ký thành công! Loại TK: " + finalAccountType, Toast.LENGTH_SHORT).show();
                    finish();
                });

            } catch (Exception e) {
                Log.e("RegisterActivity", "Registration failed", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Đăng ký thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}







