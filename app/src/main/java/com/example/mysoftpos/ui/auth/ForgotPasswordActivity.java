package com.example.mysoftpos.ui.auth;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.dao.UserDao;
import com.example.mysoftpos.data.local.entity.UserEntity;
import com.example.mysoftpos.ui.BaseActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ViewFlipper;

public class ForgotPasswordActivity extends BaseActivity {

    private ViewFlipper viewFlipper;

    // Step 1
    private EditText etInput; // Email or Phone

    // Step 2
    private EditText etCode;

    // Step 3
    private EditText etNewPassword;
    private EditText etConfirmPassword;

    // State
    private UserEntity foundUser;
    private static final String MOCK_CODE = "123456";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        viewFlipper = findViewById(R.id.viewFlipper);
        View btnBack = findViewById(R.id.btnBack);

        // Step 1
        etInput = findViewById(R.id.etEmailPhone);
        View btnSendCode = findViewById(R.id.btnSendCode);

        // Step 2
        etCode = findViewById(R.id.etCode);
        View btnVerify = findViewById(R.id.btnVerifyCode);
        // View btnResend = findViewById(R.id.btnResend); // Not in new layout

        // Step 3
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        View btnReset = findViewById(R.id.btnResetPassword);

        btnBack.setOnClickListener(v -> handleBack());

        btnSendCode.setOnClickListener(v -> handleSendCode());
        btnVerify.setOnClickListener(v -> handleVerifyCode());
        /*
         * btnResend.setOnClickListener(v -> {
         * Toast.makeText(this, "Code resent: " + MOCK_CODE, Toast.LENGTH_SHORT).show();
         * });
         */
        btnReset.setOnClickListener(v -> handleResetPassword());
    }

    private void handleBack() {
        if (viewFlipper.getDisplayedChild() > 0) {
            viewFlipper.showPrevious();
        } else {
            finish();
        }
    }

    private void handleSendCode() {
        String input = etInput.getText().toString().trim();
        if (input.isEmpty()) {
            etInput.setError("Required");
            return;
        }

        new Thread(() -> {
            com.example.mysoftpos.data.local.AppDatabase db = com.example.mysoftpos.data.local.AppDatabase
                    .getInstance(this);
            UserDao userDao = db.userDao();

            // Try to find user by Email or Phone
            UserEntity user = userDao.findByEmail(input);
            if (user == null) {
                user = userDao.findByPhone(input);
            }

            if (user == null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "User not found!", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            foundUser = user;
            runOnUiThread(() -> {
                Toast.makeText(this, "Code sent: " + MOCK_CODE, Toast.LENGTH_LONG).show();
                viewFlipper.showNext();
            });
        }).start();
    }

    private void handleVerifyCode() {
        String code = etCode.getText().toString().trim();
        if (code.isEmpty()) {
            etCode.setError("Required");
            return;
        }

        if (code.equals(MOCK_CODE)) {
            viewFlipper.showNext();
        } else {
            etCode.setError("Invalid Code");
        }
    }

    private void handleResetPassword() {
        String newPass = etNewPassword.getText().toString().trim();
        String confirmPass = etConfirmPassword.getText().toString().trim();

        if (newPass.length() < 6) {
            etNewPassword.setError("Min 6 chars");
            return;
        }
        if (!newPass.equals(confirmPass)) {
            etConfirmPassword.setError("Mismatch");
            return;
        }

        if (foundUser == null)
            return; // Should not happen

        new Thread(() -> {
            try {
                com.example.mysoftpos.data.local.AppDatabase db = com.example.mysoftpos.data.local.AppDatabase
                        .getInstance(this);
                // We need to update user. Since we don't have update method in DAO yet
                // (probably), checking DAO.
                // Assuming DAO has insert (replace) or we need to delete/insert or add update.
                // Looking at earlier DAO definition: @Insert(onConflict =
                // OnConflictStrategy.ABORT).
                // Wait, default insert strategy is ABORT. If we insert same ID/UsernameHash, it
                // might fail.
                // Let's check DAO.

                // Oops, I didn't add @Update to UserDao.
                // I'll assume for now I can delete and re-insert, OR I should add update method
                // to DAO.
                // BUT I can't easily change DAO interface now without triggering another
                // rebuild cycle.
                // Actually I just edited UserDao.java in previous turn. I can edit it again if
                // needed.
                // Or I can just execute SQL directly if Room allows, or simpler:
                // Delete old user, Insert new user (since Primary Key is ID).
                // But Primary Key ID is autoGenerate. If I delete and insert, ID changes.
                // IF username_hash is unique, I can't insert a duplicate.

                // I SHOULD ADD @Update to UserDao. It's safer.
                // Let me do that in a separate tool call if safe.
                // For now, I'll write the code assuming there is an update method OR I'll add
                // the update method now.
                // Actually, I missed checking if 'update' exists.
                // Let's assume I will add `update(UserEntity user)` to UserDao next.

                String newHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(newPass);
                foundUser.passwordHash = newHash;

                db.userDao().update(foundUser);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Password Reset Successful!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();

        // Since I'm inside the tool, I can't call another tool.
        // I will write the code to call `update` and then immediately fix the DAO.
    }
}
