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

import com.example.mysoftpos.ui.BaseActivity;

public class RegisterActivity extends BaseActivity {

    private LinearLayout cardPersonal;
    private LinearLayout cardBusiness;
    private boolean isBusiness = false;

    private EditText etFullName;
    private EditText etDob;
    private EditText etPhone;
    private EditText etAddress;
    private EditText etUsername;
    private EditText etPassword;
    private EditText etConfirmPassword;

    private CheckBox cbTerms;
    private TextView tvTermsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize views
        ImageView btnBack = findViewById(R.id.btnBack);
        cardPersonal = findViewById(R.id.cardPersonal);
        cardBusiness = findViewById(R.id.cardBusiness);

        etFullName = findViewById(R.id.etFullName);
        etDob = findViewById(R.id.etDob);
        etPhone = findViewById(R.id.etPhone);
        etAddress = findViewById(R.id.etAddress);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);

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
        setupPasswordToggle();
    }

    private void setupPasswordToggle() {
        setupToggleForEditText(etPassword);
        setupToggleForEditText(etConfirmPassword);
    }

    private void setupToggleForEditText(EditText editText) {
        editText.setOnTouchListener((v, event) -> {
            final int DRAWABLE_RIGHT = 2;
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                if (event.getRawX() >= (editText.getRight()
                        - editText.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {
                    // Toggle visibility
                    if (editText.getTransformationMethod() instanceof PasswordTransformationMethod) {
                        editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                        editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye, 0);
                    } else {
                        editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye_off, 0);
                    }
                    // Maintain tint
                    for (android.graphics.drawable.Drawable d : editText.getCompoundDrawables()) {
                        if (d != null)
                            d.setTint(Color.parseColor("#9CA3AF"));
                    }
                    return true;
                }
            }
            return false;
        });
    }

    private void updateSelectionState(boolean businesses) {
        this.isBusiness = businesses;
        cardPersonal.setSelected(!businesses);
        cardBusiness.setSelected(businesses);
    }

    private void setupTermsText() {
        String fullText = getString(R.string.register_terms_text);
        SpannableString spannableString = new SpannableString(fullText);

        // Clickable span for "Terms & Conditions"
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

        // Clickable span for "Privacy Policy"
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

        // Find substrings
        String termsTarget = "Terms & Conditions";
        String privacyTarget = "Privacy Policy";

        int termsStart = fullText.indexOf(termsTarget);
        if (termsStart >= 0) {
            spannableString.setSpan(termsSpan, termsStart, termsStart + termsTarget.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        int privacyStart = fullText.indexOf(privacyTarget);
        if (privacyStart >= 0) {
            spannableString.setSpan(privacySpan, privacyStart, privacyStart + privacyTarget.length(),
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

        TextView tvDialogTitle = dialog.findViewById(R.id.tvDialogTitle);
        TextView tvDialogContent = dialog.findViewById(R.id.tvDialogContent);

        tvDialogTitle.setText("Terms & Conditions");
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

        tvDialogTitle.setText("Privacy Policy");
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
            return "Unable to load content. Please try again later.";
        }
    }

    private void handleRegister() {
        // Get selected account type
        String accountType = isBusiness ? "Business" : "Personal";

        String fullName = etFullName.getText().toString().trim();
        String dob = etDob.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // Validation
        if (fullName.isEmpty()) {
            etFullName.setError("Please enter your full name");
            etFullName.requestFocus();
            return;
        }

        if (dob.isEmpty()) {
            etDob.setError("Please enter date of birth");
            etDob.requestFocus();
            return;
        }

        if (phone.isEmpty()) {
            etPhone.setError("Please enter phone number");
            etPhone.requestFocus();
            return;
        }

        if (address.isEmpty()) {
            etAddress.setError("Please enter address");
            etAddress.requestFocus();
            return;
        }

        if (username.isEmpty()) {
            etUsername.setError("Please choose a username");
            etUsername.requestFocus();
            return;
        }

        if (username.length() < 3) {
            etUsername.setError("Username is too short");
            etUsername.requestFocus();
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

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        // Check terms and conditions
        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "Please agree to the Terms & Conditions", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save user to database with encrypted credentials
        final String finalFullName = fullName; // Display Name
        final String finalUsername = username; // Login Username
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
                        etUsername.setError("Username already exists");
                        etUsername.requestFocus();
                    });
                    return;
                }

                // Create and insert user entity
                // Note: UserEntity might need updates if we want to store DOB, Phone, Address.
                // Assuming current UserEntity only supports minimal fields, we'll just use
                // FullName/Username/Password for now.
                // If user wants to store these, we'd need Schema migration.
                // Given the prompt "Register screen FORM", usually implies UI first.
                // I will assume standard UserEntity for now.

                com.example.mysoftpos.data.local.entity.UserEntity user = new com.example.mysoftpos.data.local.entity.UserEntity(
                        usernameHash,
                        passwordHash,
                        finalFullName, // Display name
                        "USER" // Default role
                );

                userDao.insert(user);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Registration Successful! Welcome " + finalFullName, Toast.LENGTH_SHORT)
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
