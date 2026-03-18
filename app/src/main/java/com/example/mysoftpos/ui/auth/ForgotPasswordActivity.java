package com.example.mysoftpos.ui.auth;

import com.example.mysoftpos.R;
import com.example.mysoftpos.ui.BaseActivity;

import android.annotation.SuppressLint;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.graphics.drawable.Drawable;
import android.os.CountDownTimer;
import android.os.Bundle;
import android.util.Patterns;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.appcompat.content.res.AppCompatResources;
import org.json.JSONObject;

import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;
import com.google.android.material.button.MaterialButton;

@SuppressLint("ClickableViewAccessibility")
public class ForgotPasswordActivity extends BaseActivity {

    private ViewFlipper viewFlipper;

    // Step 1
    private EditText etInput;

    // Step 2
    private EditText etCode;

    // Step 3
    private EditText etNewPassword;
    private EditText etConfirmPassword;

    // State
    private String pendingEmail;
    private String verifiedCode;
    private boolean newPassVisible = false;
    private boolean confirmPassVisible = false;

    private MaterialButton btnSendCode;
    private MaterialButton btnVerifyCode;
    private MaterialButton btnResetPassword;
    private MaterialButton btnResendCode;
    private CharSequence sendCodeDefaultText;
    private CharSequence verifyCodeDefaultText;
    private CharSequence resetPasswordDefaultText;
    private CharSequence resendCodeDefaultText;
    private android.widget.TextView tvResendCountdown;

    private static final long RESEND_COOLDOWN_MS = 60_000L;
    private CountDownTimer resendTimer;
    private long resendRemainingMs = 0L;

    private static final int SEND_CODE_MAX_RETRY = 1;
    private static final long SEND_CODE_RETRY_DELAY_MS = 1500L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        viewFlipper = findViewById(R.id.viewFlipper);
        MaterialButton btnBack = findViewById(R.id.btnBack);

        // Step 1
        etInput = findViewById(R.id.etEmailPhone);
        btnSendCode = findViewById(R.id.btnSendCode);

        // Step 2
        etCode = findViewById(R.id.etCode);
        btnVerifyCode = findViewById(R.id.btnVerifyCode);
        btnResendCode = findViewById(R.id.btnResendCode);
        tvResendCountdown = findViewById(R.id.tvResendCountdown);

        // Step 3
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnResetPassword = findViewById(R.id.btnResetPassword);
        sendCodeDefaultText = btnSendCode.getText();
        verifyCodeDefaultText = btnVerifyCode.getText();
        resetPasswordDefaultText = btnResetPassword.getText();
        if (btnResendCode != null) {
            resendCodeDefaultText = btnResendCode.getText();
        }

        // Password toggle — New Password
        setupPasswordToggle(etNewPassword, () -> newPassVisible, v -> newPassVisible = v);

        // Password toggle — Confirm Password
        setupPasswordToggle(etConfirmPassword, () -> confirmPassVisible, v -> confirmPassVisible = v);

        btnBack.setOnClickListener(v -> handleBack());

        btnSendCode.setOnClickListener(v -> handleSendCode());
        btnVerifyCode.setOnClickListener(v -> handleVerifyCode());
        btnResetPassword.setOnClickListener(v -> handleResetPassword());
        if (btnResendCode != null) {
            btnResendCode.setOnClickListener(v -> handleResendCode());
        }
        updateResendUiState();
    }

    private interface BoolGetter {
        boolean get();
    }

    private interface BoolSetter {
        void set(boolean v);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupPasswordToggle(EditText et, BoolGetter getter, BoolSetter setter) {
        et.setCompoundDrawablesRelativeWithIntrinsicBounds(
                et.getCompoundDrawablesRelative()[0], null,
                AppCompatResources.getDrawable(this, R.drawable.ic_baseline_visibility_off_24), null);
        et.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                Drawable end = et.getCompoundDrawablesRelative()[2];
                if (end != null && event.getRawX() >= (et.getRight() - end.getBounds().width() - et.getPaddingEnd())) {
                    v.performClick();
                    boolean visible = !getter.get();
                    setter.set(visible);
                    if (visible) {
                        et.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                        et.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                et.getCompoundDrawablesRelative()[0], null,
                                AppCompatResources.getDrawable(this, R.drawable.ic_baseline_visibility_24), null);
                    } else {
                        et.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        et.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                et.getCompoundDrawablesRelative()[0], null,
                                AppCompatResources.getDrawable(this, R.drawable.ic_baseline_visibility_off_24), null);
                    }
                    et.setSelection(et.getText().length());
                    return true;
                }
            }
            return false;
        });
    }

    private void handleBack() {
        if (viewFlipper.getDisplayedChild() > 0) {
            viewFlipper.showPrevious();
            updateResendUiState();
        } else {
            finish();
        }
    }

    private void handleSendCode() {
        String email = etInput.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        if (email.isEmpty()) {
            etInput.setError(getString(R.string.common_required));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etInput.setError(getString(R.string.forgot_invalid_email));
            return;
        }
        if (!isNetworkAvailable()) {
            showToast(R.string.forgot_network_required);
            return;
        }

        setLoading(btnSendCode, true);
        requestCodeByEmail(email, true, 0);
    }

    private void handleResendCode() {
        if (pendingEmail == null || pendingEmail.isEmpty()) {
            showToast(R.string.forgot_email_missing);
            viewFlipper.setDisplayedChild(0);
            updateResendUiState();
            return;
        }
        if (resendRemainingMs > 0) {
            return;
        }
        if (!isNetworkAvailable()) {
            showToast(R.string.forgot_network_required);
            return;
        }

        setLoading(btnResendCode, true);
        requestCodeByEmail(pendingEmail, false, 0);
    }

    private void requestCodeByEmail(String email, boolean moveToStep2, int attempt) {
        // Forgot-password request may include SMTP latency on backend.
        ApiClient.getForgotPasswordService(this)
                .requestForgotPasswordCode(new ApiService.ForgotPasswordRequest(email))
                .enqueue(new retrofit2.Callback<java.util.Map<String, String>>() {
                    @Override
                    public void onResponse(retrofit2.Call<java.util.Map<String, String>> call,
                                           retrofit2.Response<java.util.Map<String, String>> response) {
                        setLoading(btnSendCode, false);
                        setLoading(btnResendCode, false);
                        if (response.isSuccessful()) {
                            pendingEmail = email;
                            verifiedCode = null;
                            showToast(R.string.forgot_code_sent);
                            startResendCooldown();
                            if (moveToStep2) {
                                viewFlipper.showNext();
                            }
                            updateResendUiState();
                            return;
                        }
                        showToast(getBackendError(response, getString(R.string.forgot_send_code_failed)));
                        updateResendUiState();
                    }

                    @Override
                    public void onFailure(retrofit2.Call<java.util.Map<String, String>> call, Throwable t) {
                        if (isTimeoutError(t) && attempt < SEND_CODE_MAX_RETRY) {
                            showToast(R.string.forgot_retrying);
                            if (viewFlipper != null) {
                                viewFlipper.postDelayed(() -> requestCodeByEmail(email, moveToStep2, attempt + 1),
                                        SEND_CODE_RETRY_DELAY_MS);
                            } else {
                                requestCodeByEmail(email, moveToStep2, attempt + 1);
                            }
                            return;
                        }

                        setLoading(btnSendCode, false);
                        setLoading(btnResendCode, false);
                        showToast(getFriendlyNetworkError(t));
                        updateResendUiState();
                    }
                });
    }

    private void handleVerifyCode() {
        String code = etCode.getText().toString().trim();
        if (code.isEmpty()) {
            etCode.setError(getString(R.string.common_required));
            return;
        }
        if (pendingEmail == null || pendingEmail.isEmpty()) {
            showToast(R.string.forgot_email_missing);
            viewFlipper.setDisplayedChild(0);
            updateResendUiState();
            return;
        }
        if (!isNetworkAvailable()) {
            showToast(R.string.forgot_network_required);
            return;
        }

        setLoading(btnVerifyCode, true);
        ApiClient.getForgotPasswordService(this)
                .verifyForgotPasswordCode(new ApiService.ForgotPasswordVerifyCodeRequest(pendingEmail, code))
                .enqueue(new retrofit2.Callback<java.util.Map<String, String>>() {
                    @Override
                    public void onResponse(retrofit2.Call<java.util.Map<String, String>> call,
                                           retrofit2.Response<java.util.Map<String, String>> response) {
                        setLoading(btnVerifyCode, false);
                        if (response.isSuccessful()) {
                            verifiedCode = code;
                            showToast(R.string.forgot_code_verified);
                            viewFlipper.showNext();
                            updateResendUiState();
                            return;
                        }
                        etCode.setError(getBackendError(response, getString(R.string.forgot_invalid_code)));
                    }

                    @Override
                    public void onFailure(retrofit2.Call<java.util.Map<String, String>> call, Throwable t) {
                        setLoading(btnVerifyCode, false);
                        showToast(getFriendlyNetworkError(t));
                    }
                });
    }

    private void handleResetPassword() {
        String newPass = etNewPassword.getText().toString().trim();
        String confirmPass = etConfirmPassword.getText().toString().trim();

        if (!com.example.mysoftpos.utils.security.PasswordPolicy.isValid(newPass)) {
            etNewPassword.setError(getString(R.string.register_password_policy));
            return;
        }
        if (!newPass.equals(confirmPass)) {
            etConfirmPassword.setError(getString(R.string.register_passwords_do_not_match));
            return;
        }

        String code = verifiedCode != null ? verifiedCode : etCode.getText().toString().trim();
        if (pendingEmail == null || pendingEmail.isEmpty()) {
            showToast(R.string.forgot_email_missing);
            viewFlipper.setDisplayedChild(0);
            updateResendUiState();
            return;
        }
        if (code.isEmpty()) {
            showToast(R.string.forgot_invalid_code);
            viewFlipper.setDisplayedChild(1);
            updateResendUiState();
            return;
        }
        if (!isNetworkAvailable()) {
            showToast(R.string.forgot_network_required);
            return;
        }

        setLoading(btnResetPassword, true);
        ApiClient.getForgotPasswordService(this)
                .resetForgotPassword(new ApiService.ForgotPasswordResetRequest(
                        pendingEmail, code, newPass, confirmPass))
                .enqueue(new retrofit2.Callback<java.util.Map<String, String>>() {
                    @Override
                    public void onResponse(retrofit2.Call<java.util.Map<String, String>> call,
                                           retrofit2.Response<java.util.Map<String, String>> response) {
                        setLoading(btnResetPassword, false);
                        if (response.isSuccessful()) {
                            updateLocalPasswordCacheAsync(pendingEmail, newPass);
                            showToast(R.string.forgot_password_reset_success);
                            finish();
                            return;
                        }
                        showToast(getBackendError(response, getString(R.string.forgot_reset_failed)));
                    }

                    @Override
                    public void onFailure(retrofit2.Call<java.util.Map<String, String>> call, Throwable t) {
                        setLoading(btnResetPassword, false);
                        showToast(getFriendlyNetworkError(t));
                    }
                });
    }

    private void updateLocalPasswordCacheAsync(String email, String newPassword) {
        new Thread(() -> {
            try {
                com.example.mysoftpos.data.local.dao.UserDao userDao =
                        com.example.mysoftpos.data.local.AppDatabase.getInstance(this).userDao();
                com.example.mysoftpos.data.local.entity.UserEntity user = userDao.findByEmail(email);
                if (user != null) {
                    user.passwordHash = com.example.mysoftpos.utils.security.PasswordUtils.hashPassword(newPassword);
                    user.failedLoginAttempts = 0;
                    user.lockedUntil = 0;
                    userDao.update(user);
                }
            } catch (Exception ignored) {
                // Local cache sync failure should not block reset success.
            }
        }).start();
    }

    private String getBackendError(retrofit2.Response<?> response, String fallback) {
        String message = fallback;
        try (okhttp3.ResponseBody errorBody = response.errorBody()) {
            if (errorBody != null) {
                String raw = errorBody.string();
                if (!raw.trim().isEmpty()) {
                    try {
                        JSONObject json = new JSONObject(raw);
                        String apiError = json.optString("error", "").trim();
                        message = apiError.isEmpty() ? raw : apiError;
                    } catch (Exception ignored) {
                        message = raw;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        String normalized = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("timed out") || normalized.contains("timeout")) {
            return getString(R.string.forgot_timeout);
        }
        if (normalized.contains("unable to send verification email")) {
            return getString(R.string.forgot_send_code_failed);
        }
        return message;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager
                .getNetworkCapabilities(connectivityManager.getActiveNetwork());
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private void setLoading(MaterialButton button, boolean loading) {
        if (button == null) {
            return;
        }
        button.setEnabled(!loading);
        if (loading) {
            button.setText(R.string.processing);
            return;
        }
        if (button == btnSendCode && sendCodeDefaultText != null) {
            button.setText(sendCodeDefaultText);
        } else if (button == btnVerifyCode && verifyCodeDefaultText != null) {
            button.setText(verifyCodeDefaultText);
        } else if (button == btnResetPassword && resetPasswordDefaultText != null) {
            button.setText(resetPasswordDefaultText);
        } else if (button == btnResendCode && resendCodeDefaultText != null) {
            button.setText(resendCodeDefaultText);
        }
        updateResendUiState();
    }

    private void startResendCooldown() {
        resendRemainingMs = RESEND_COOLDOWN_MS;
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        resendTimer = new CountDownTimer(RESEND_COOLDOWN_MS, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                resendRemainingMs = millisUntilFinished;
                updateResendUiState();
            }

            @Override
            public void onFinish() {
                resendRemainingMs = 0L;
                updateResendUiState();
            }
        };
        resendTimer.start();
        updateResendUiState();
    }

    private void updateResendUiState() {
        if (btnResendCode == null || tvResendCountdown == null) {
            return;
        }

        boolean isCodeStepVisible = viewFlipper != null && viewFlipper.getDisplayedChild() == 1;
        if (!isCodeStepVisible) {
            btnResendCode.setVisibility(android.view.View.GONE);
            tvResendCountdown.setVisibility(android.view.View.GONE);
            return;
        }

        btnResendCode.setVisibility(android.view.View.VISIBLE);
        if (resendRemainingMs > 0) {
            btnResendCode.setEnabled(false);
            tvResendCountdown.setVisibility(android.view.View.VISIBLE);
            long seconds = (resendRemainingMs + 999L) / 1000L;
            tvResendCountdown.setText(getString(R.string.forgot_resend_in_seconds, formatAsMinutesSeconds(seconds)));
            return;
        }

        boolean canResend = pendingEmail != null && !pendingEmail.isEmpty() && isNetworkAvailable();
        btnResendCode.setEnabled(canResend);
        tvResendCountdown.setVisibility(android.view.View.GONE);
    }

    private String formatAsMinutesSeconds(long totalSeconds) {
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onDestroy() {
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        super.onDestroy();
    }

    private void showToast(int messageResId) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String getFriendlyNetworkError(Throwable throwable) {
        if (throwable == null) {
            return getString(R.string.forgot_send_code_failed);
        }
        if (isTimeoutError(throwable)) {
            return getString(R.string.forgot_timeout);
        }
        String detail = throwable.getMessage();
        if (detail == null || detail.trim().isEmpty()) {
            return getString(R.string.forgot_send_code_failed);
        }
        return getString(R.string.common_error_with_reason, detail);
    }

    private boolean isTimeoutError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.io.InterruptedIOException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(java.util.Locale.ROOT);
                if (normalized.contains("timed out") || normalized.contains("timeout")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
