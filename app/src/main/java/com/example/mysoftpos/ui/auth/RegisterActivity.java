package com.example.mysoftpos.ui.auth;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.dao.UserDao;
import com.example.mysoftpos.data.local.dao.MerchantDao;
import com.example.mysoftpos.data.local.entity.UserEntity;
import com.example.mysoftpos.data.local.entity.MerchantEntity;
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;
import com.example.mysoftpos.ui.BaseActivity;
import com.example.mysoftpos.utils.mcc.BusinessTypeMccMapper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends BaseActivity {
    private static final String TAG = "RegisterActivity";
    private static final String REGISTERED_USER_ROLE = "USER";
    private static final int MIN_STORE_NAME_LENGTH = 3;
    private static final int MAX_STORE_NAME_LENGTH = 80;
    private static final int MIN_BANK_NAME_LENGTH = 2;
    private static final int MAX_BANK_NAME_LENGTH = 22;
    private static final int MIN_STORE_ADDRESS_LENGTH = 8;
    private static final int MAX_STORE_ADDRESS_LENGTH = 160;
    private static final int MIN_FULL_NAME_LENGTH = 2;
    private static final int MAX_FULL_NAME_LENGTH = 60;
    private static final int MAX_EMAIL_LENGTH = 100;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MIN_REGISTER_AGE = 18;
    private static final int MAX_BRANCH_COUNT = 50;
    private static final int MIN_ACCOUNT_COUNT = 1;
    private static final int MAX_ACCOUNT_COUNT = 500;

    private EditText etStoreName;
    private EditText etBankName;
    private AutoCompleteTextView etBusinessType;
    private EditText etStoreAddress;
    private EditText etBranchCount;
    private EditText etBranchAddresses;
    private TextView tvBranchAddressesLabel;
    private TextInputLayout tilBranchAddresses;
    private EditText etAccountCount;
    private EditText etFullName;
    private EditText etDob;
    private AutoCompleteTextView etGender;
    private EditText etPhone;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private CheckBox cbTerms;
    private TextView tvTermsText;
    private MaterialButton btnRegister;
    private boolean forceFocusOnValidationError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        bindViews();
        setupBusinessTypeDropdown();
        setupDropdown(etGender, R.array.register_gender_options);
        setupDobPicker();
        setupTermsText();
        setupValidationListeners();
        setupBranchAddressVisibility();

        View btnBack = findViewById(R.id.btnBack);
        TextView tvLogin = findViewById(R.id.tvLogin);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
        if (tvLogin != null) {
            tvLogin.setOnClickListener(v -> {
                startActivity(new android.content.Intent(this, LoginActivity.class));
                finish();
            });
        }

        // Language toggle
        View btnLanguageToggle = findViewById(R.id.btnLanguageToggle);
        if (btnLanguageToggle != null) {
            btnLanguageToggle.setOnClickListener(v -> {
                String current = com.example.mysoftpos.utils.LocaleHelper.getLanguage(this);
                String next = "vi".equals(current) ? "en" : "vi";
                com.example.mysoftpos.utils.LocaleHelper.setLocale(getApplicationContext(), next);
                recreate();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        }

        btnRegister.setOnClickListener(v -> handleRegister());
    }

    private void bindViews() {
        etStoreName = findViewById(R.id.etStoreName);
        etBankName = findViewById(R.id.etBankName);
        etBusinessType = findViewById(R.id.etBusinessType);
        etStoreAddress = findViewById(R.id.etStoreAddress);
        etBranchCount = findViewById(R.id.etBranchCount);
        etBranchAddresses = findViewById(R.id.etBranchAddresses);
        tvBranchAddressesLabel = findViewById(R.id.tvBranchAddressesLabel);
        tilBranchAddresses = findViewById(R.id.tilBranchAddresses);
        etAccountCount = findViewById(R.id.etAccountCount);
        etFullName = findViewById(R.id.etFullName);
        etDob = findViewById(R.id.etDob);
        etGender = findViewById(R.id.etGender);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        cbTerms = findViewById(R.id.cbTerms);
        tvTermsText = findViewById(R.id.tvTermsText);
        btnRegister = findViewById(R.id.btnRegister);
    }

    private void setupDropdown(AutoCompleteTextView view, int arrayResId) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                arrayResId, android.R.layout.simple_list_item_1);
        view.setAdapter(adapter);
        view.setOnClickListener(v -> view.showDropDown());
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                view.showDropDown();
            }
        });
    }

    private void setupBusinessTypeDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                BusinessTypeMccMapper.getDisplayOptions(this));
        etBusinessType.setAdapter(adapter);
        etBusinessType.setOnClickListener(v -> etBusinessType.showDropDown());
        etBusinessType.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                etBusinessType.showDropDown();
            }
        });
    }

    private void setupDobPicker() {
        View.OnClickListener listener = v -> showDobPicker();
        etDob.setOnClickListener(listener);
        etDob.setFocusable(false);
        etDob.setClickable(true);
    }

    private void setupValidationListeners() {
        attachRealtimeErrorClear(etStoreName);
        attachRealtimeErrorClear(etBankName);
        attachRealtimeErrorClear(etStoreAddress);
        attachRealtimeErrorClear(etBranchCount);
        attachRealtimeErrorClear(etBranchAddresses);
        attachRealtimeErrorClear(etAccountCount);
        attachRealtimeErrorClear(etFullName);
        attachRealtimeErrorClear(etPhone);
        attachRealtimeErrorClear(etEmail);
        attachRealtimeErrorClear(etPassword);
        attachRealtimeErrorClear(etConfirmPassword);
        attachRealtimeErrorClear(etBusinessType);
        attachRealtimeErrorClear(etGender);

        etPassword.addTextChangedListener(new SimpleAfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                // Clear confirm-password error when password changes; validation stays submit-only.
                clearFieldError(etConfirmPassword);
            }
        });
    }

    private void setupBranchAddressVisibility() {
        updateBranchAddressVisibility();
        etBranchCount.addTextChangedListener(new SimpleAfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateBranchAddressVisibility();
            }
        });
    }

    private void updateBranchAddressVisibility() {
        int branchCount = parsePositiveIntOrZero(getTrimmedText(etBranchCount));
        boolean showBranchAddress = branchCount > 0;
        int visibility = showBranchAddress ? View.VISIBLE : View.GONE;

        if (tvBranchAddressesLabel != null) {
            tvBranchAddressesLabel.setVisibility(visibility);
        }
        if (tilBranchAddresses != null) {
            tilBranchAddresses.setVisibility(visibility);
        }

        if (!showBranchAddress) {
            applyNormalizedText(etBranchAddresses, "");
            clearFieldError(etBranchAddresses);
        }
    }

    private void attachRealtimeErrorClear(EditText view) {
        view.addTextChangedListener(new SimpleAfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                clearFieldError(view);
            }
        });
    }

    private void attachRealtimeErrorClear(AutoCompleteTextView view) {
        view.addTextChangedListener(new SimpleAfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                clearFieldError(view);
            }
        });
    }

    private void showDobPicker() {
        Calendar calendar = Calendar.getInstance();
        if (isValidDateOfBirth(getTrimmedText(etDob))) {
            try {
                Date parsed = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        .parse(getTrimmedText(etDob));
                if (parsed != null) {
                    calendar.setTime(parsed);
                }
            } catch (ParseException ignored) {
            }
        } else {
            calendar.add(Calendar.YEAR, -MIN_REGISTER_AGE);
        }

        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    etDob.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d",
                            dayOfMonth, month + 1, year));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void handleRegister() {
        RegistrationForm form = validateForm();
        if (form == null) {
            return;
        }
        if (!cbTerms.isChecked()) {
            showToast(R.string.register_agree_terms);
            return;
        }
        if (!isNetworkAvailable()) {
            showToast(R.string.register_backend_required, Toast.LENGTH_LONG);
            return;
        }

        setRegisterLoading(true);
        ApiService.RegisterRequest request = new ApiService.RegisterRequest(
                form.password,
                form.fullName,
                form.phone,
                form.email,
                form.dob,
                form.gender,
                form.storeName,
                form.businessType,
                form.storeAddress,
                form.branchCount > 0 ? form.branchCount : null,
                form.branchAddresses.isEmpty() ? null : form.branchAddresses,
                form.accountCount,
                form.bankName);

        ApiClient.getRegisterService(this).register(request).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiService.LoginResponse> call,
                    @NonNull Response<ApiService.LoginResponse> response) {
                 if (response.isSuccessful()) {
                     ApiService.LoginResponse payload = response.body();
                    if (payload != null && payload.user != null
                            && payload.user.merchantCode != null
                            && payload.user.merchantCode.matches("^[A-Z0-9]{15}$")) {
                        com.example.mysoftpos.utils.config.ConfigManager
                                .getInstance(RegisterActivity.this)
                                .setMerchantId(payload.user.merchantCode);
                    }
                    com.example.mysoftpos.utils.config.ConfigManager
                            .getInstance(RegisterActivity.this)
                            .setMcc18(form.businessType);
                    com.example.mysoftpos.utils.config.ConfigManager
                            .getInstance(RegisterActivity.this)
                            .setBankName(form.bankName);
                    cacheUserLocally(form, payload != null ? payload.user : null, saved -> {
                        setRegisterLoading(false);
                        if (saved) {
                            showToast(R.string.register_success);
                            navigateToLogin(buildAccountPhoneIdentifier(form.phone, 1));
                        } else {
                            showToast(R.string.register_local_cache_failed, Toast.LENGTH_LONG);
                        }
                    });
                    return;
                }
                setRegisterLoading(false);
                handleRegisterError(response);
            }

            @Override
            public void onFailure(@NonNull Call<ApiService.LoginResponse> call, @NonNull Throwable t) {
                Log.w(TAG, "User registration requires backend connectivity", t);
                setRegisterLoading(false);
                if (t instanceof SocketTimeoutException) {
                    showToast(R.string.register_backend_required, Toast.LENGTH_LONG);
                    return;
                }
                String reason = t.getMessage() == null ? "" : t.getMessage();
                if (reason.isEmpty()) {
                    showToast(R.string.register_backend_required, Toast.LENGTH_LONG);
                } else {
                    Toast.makeText(RegisterActivity.this,
                            getString(R.string.common_error_with_reason, reason),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private RegistrationForm validateForm() {
        boolean previousFocusMode = forceFocusOnValidationError;
        forceFocusOnValidationError = true;
        try {
            if (!validateStoreNameField()
                    || !validateBankNameField()
                    || !validateBusinessTypeField()
                    || !validateStoreAddressField()
                    || !validateBranchCountField()
                    || !validateBranchAddressesField()
                    || !validateAccountCountField()
                    || !validateFullNameField()
                    || !validateDobField()
                    || !validateGenderField()
                    || !validatePhoneField()
                    || !validateEmailField()
                    || !validatePasswordField()
                    || !validateConfirmPasswordField()) {
                return null;
            }
        } finally {
            forceFocusOnValidationError = previousFocusMode;
        }

        RegistrationForm form = new RegistrationForm();
        form.storeName = getTrimmedText(etStoreName);
        form.bankName = getTrimmedText(etBankName);
        form.businessTypeSelection = getTrimmedText(etBusinessType);
        form.storeAddress = getTrimmedText(etStoreAddress);
        String branchCountValue = getTrimmedText(etBranchCount);
        form.branchCount = branchCountValue.isEmpty() ? 0 : parsePositiveIntOrZero(branchCountValue);
        form.branchAddresses = sanitizeBranchAddresses(getTrimmedText(etBranchAddresses));
        form.accountCount = parsePositiveIntOrZero(getTrimmedText(etAccountCount));
        form.fullName = getTrimmedText(etFullName);
        form.dob = getTrimmedText(etDob);
        form.gender = getTrimmedText(etGender);
        form.phone = normalizePhone(getTrimmedText(etPhone));
        form.email = getTrimmedText(etEmail).toLowerCase(Locale.ROOT);
        form.password = getTrimmedText(etPassword);
        form.confirmPassword = getTrimmedText(etConfirmPassword);
        form.businessType = BusinessTypeMccMapper.toMcc(form.businessTypeSelection);
        return form;
    }

    private boolean validateStoreNameField() {
        String value = sanitizeAndApply(etStoreName);
        if (requireValue(etStoreName, value)) {
            return false;
        }
        if (value.length() < MIN_STORE_NAME_LENGTH || value.length() > MAX_STORE_NAME_LENGTH
                || !value.matches(".*[\\p{L}\\p{N}].*")) {
            setFieldError(etStoreName, R.string.register_invalid_store_name);
            return false;
        }
        clearFieldError(etStoreName);
        return true;
    }

    private boolean validateBusinessTypeField() {
        String value = sanitizeAndApply(etBusinessType);
        if (requireValue(etBusinessType, value)) {
            return false;
        }
        if (!BusinessTypeMccMapper.isSupportedSelection(value)) {
            setFieldError(etBusinessType, R.string.register_invalid_business_type);
            return false;
        }
        etBusinessType.setText(BusinessTypeMccMapper.toDisplay(this, value), false);
        clearFieldError(etBusinessType);
        return true;
    }

    private boolean validateStoreAddressField() {
        String value = sanitizeAndApply(etStoreAddress);
        if (requireValue(etStoreAddress, value)) {
            return false;
        }
        if (value.length() < MIN_STORE_ADDRESS_LENGTH || value.length() > MAX_STORE_ADDRESS_LENGTH
                || !value.matches(".*[\\p{L}\\p{N}].*")) {
            setFieldError(etStoreAddress, R.string.register_invalid_store_address);
            return false;
        }
        clearFieldError(etStoreAddress);
        return true;
    }

    private boolean validateBankNameField() {
        String value = sanitizeAndApply(etBankName).toUpperCase(Locale.ROOT);
        applyNormalizedText(etBankName, value);
        if (requireValue(etBankName, value)) {
            return false;
        }
        if (value.length() < MIN_BANK_NAME_LENGTH || value.length() > MAX_BANK_NAME_LENGTH
                || !value.matches("^[A-Z0-9]{2,22}$")) {
            setFieldError(etBankName, R.string.register_invalid_bank_name);
            return false;
        }
        clearFieldError(etBankName);
        return true;
    }

    private boolean validateBranchCountField() {
        String value = getTrimmedText(etBranchCount);
        if (value.isEmpty()) {
            clearFieldError(etBranchCount);
            updateBranchAddressVisibility();
            return true;
        }

        int branchCount = parsePositiveIntOrZero(value);
        if (branchCount < 0 || branchCount > MAX_BRANCH_COUNT) {
            setFieldError(etBranchCount, R.string.register_invalid_branch_count);
            return false;
        }
        applyNormalizedText(etBranchCount, String.valueOf(branchCount));
        clearFieldError(etBranchCount);
        updateBranchAddressVisibility();
        return true;
    }

    private boolean validateBranchAddressesField() {
        String normalizedAddresses = sanitizeBranchAddresses(getTrimmedText(etBranchAddresses));
        applyNormalizedText(etBranchAddresses, normalizedAddresses);

        int branchCount = parsePositiveIntOrZero(getTrimmedText(etBranchCount));
        if (branchCount <= 0) {
            clearFieldError(etBranchAddresses);
            return true;
        }


        clearFieldError(etBranchAddresses);
        return true;
    }

    private boolean validateAccountCountField() {
        String value = getTrimmedText(etAccountCount);
        if (value.isEmpty()) {
            setFieldError(etAccountCount, R.string.common_required);
            return false;
        }
        int accountCount = parsePositiveIntOrZero(value);
        if (accountCount < MIN_ACCOUNT_COUNT || accountCount > MAX_ACCOUNT_COUNT) {
            setFieldError(etAccountCount, R.string.register_invalid_account_count);
            return false;
        }
        applyNormalizedText(etAccountCount, String.valueOf(accountCount));
        clearFieldError(etAccountCount);
        return true;
    }

    private boolean validateFullNameField() {
        String value = sanitizeAndApply(etFullName);
        if (requireValue(etFullName, value)) {
            return false;
        }
        if (value.length() < MIN_FULL_NAME_LENGTH
                || value.length() > MAX_FULL_NAME_LENGTH
                || !value.matches("^[\\p{L}][\\p{L}\\s.'’-]*$")
                || !value.matches(".*\\p{L}.*")) {
            setFieldError(etFullName, R.string.register_invalid_full_name);
            return false;
        }
        clearFieldError(etFullName);
        return true;
    }

    private boolean validateDobField() {
        String value = sanitizeAndApply(etDob);
        if (requireValue(etDob, value)) {
            return false;
        }
        if (!isValidDateOfBirth(value)) {
            setFieldError(etDob, R.string.register_invalid_dob);
            return false;
        }
        clearFieldError(etDob);
        return true;
    }

    private boolean validateGenderField() {
        String value = sanitizeAndApply(etGender);
        if (requireValue(etGender, value)) {
            return false;
        }
        if (!isSupportedSelection(R.array.register_gender_options, value)) {
            setFieldError(etGender, R.string.register_invalid_gender);
            return false;
        }
        clearFieldError(etGender);
        return true;
    }

    private boolean validatePhoneField() {
        String normalizedPhone = normalizePhone(getTrimmedText(etPhone));
        applyNormalizedText(etPhone, normalizedPhone);
        if (normalizedPhone.isEmpty()) {
            setFieldError(etPhone, R.string.common_required);
            return false;
        }
        if (!normalizedPhone.matches("^\\+?[0-9]{9,15}$")) {
            setFieldError(etPhone, R.string.register_invalid_phone);
            return false;
        }
        clearFieldError(etPhone);
        return true;
    }

    private boolean validateEmailField() {
        String normalizedEmail = getTrimmedText(etEmail).toLowerCase(Locale.ROOT);
        applyNormalizedText(etEmail, normalizedEmail);
        if (requireValue(etEmail, normalizedEmail)) {
            return false;
        }
        if (normalizedEmail.length() > MAX_EMAIL_LENGTH || !Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            setFieldError(etEmail, R.string.register_invalid_email);
            return false;
        }
        clearFieldError(etEmail);
        return true;
    }

    private boolean validatePasswordField() {
        String password = getTrimmedText(etPassword);
        if (requireValue(etPassword, password)) {
            return false;
        }
        if (!isValidPassword(password)) {
            setFieldError(etPassword, R.string.register_password_policy);
            return false;
        }
        clearFieldError(etPassword);
        return true;
    }

    private boolean validateConfirmPasswordField() {
        String confirmPassword = getTrimmedText(etConfirmPassword);
        if (requireValue(etConfirmPassword, confirmPassword)) {
            return false;
        }
        if (!validatePasswordField()) {
            return false;
        }
        if (!getTrimmedText(etPassword).equals(confirmPassword)) {
            setFieldError(etConfirmPassword, R.string.register_passwords_do_not_match);
            return false;
        }
        clearFieldError(etConfirmPassword);
        return true;
    }

    private boolean validatePhone(String phone) {
        return validatePhoneField();
    }

    private boolean isValidPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            }
            if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        return hasLetter && hasDigit;
    }

    private boolean isValidDateOfBirth(String value) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            format.setLenient(false);
            Date date = format.parse(value);
            if (date == null || date.after(new Date())) {
                return false;
            }
            Calendar birth = Calendar.getInstance();
            birth.setTime(date);
            Calendar today = Calendar.getInstance();
            int age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR);
            if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
                age--;
            }
            return age >= MIN_REGISTER_AGE;
        } catch (Exception e) {
            return false;
        }
    }

    private void handleRegisterError(Response<ApiService.LoginResponse> response) {
        String body = readErrorBody(response).toLowerCase(Locale.ROOT);
        int messageRes = R.string.register_failed;
        if (body.contains("already registered") || body.contains("already exists")) {
            messageRes = body.contains("phone")
                    ? R.string.register_error_exists_phone
                    : R.string.register_admin_exists;
        } else if (body.contains("email")) {
            messageRes = R.string.register_error_exists_email;
        }
        showToast(messageRes, Toast.LENGTH_LONG);
    }

    private String readErrorBody(Response<?> response) {
        try (okhttp3.ResponseBody errorBody = response.errorBody()) {
            return errorBody != null ? errorBody.string() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private void cacheUserLocally(RegistrationForm form, ApiService.UserDto userDto) {
        cacheUserLocally(form, userDto, null);
    }

    private void cacheUserLocally(RegistrationForm form, ApiService.UserDto userDto, LocalCacheCallback callback) {
        new Thread(() -> {
            boolean saved = false;
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                UserDao userDao = db.userDao();
                MerchantDao merchantDao = db.merchantDao();
                String usernameHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(form.phone);
                String passwordHash = com.example.mysoftpos.utils.security.PasswordUtils.hashPassword(form.password);

                UserEntity user = userDao.findByUsernameHash(usernameHash);
                if (user == null) {
                    user = userDao.findByPhone(form.phone);
                }
                if (user == null) {
                    user = userDao.findByEmail(form.email);
                }
                if (user == null) {
                    user = new UserEntity(usernameHash, passwordHash, form.fullName,
                            userDto != null && userDto.role != null ? userDto.role : REGISTERED_USER_ROLE,
                            form.email, form.phone, form.dob);
                }

                user.usernameHash = usernameHash;
                user.username = form.phone;
                user.passwordHash = passwordHash;
                user.displayName = form.fullName;
                user.role = userDto != null && userDto.role != null && !userDto.role.trim().isEmpty()
                        ? userDto.role
                        : REGISTERED_USER_ROLE;
                user.email = form.email;
                user.phone = form.phone;
                user.dob = form.dob;
                user.gender = form.gender;
                user.merchantBackendId = userDto != null && userDto.merchantId != null ? userDto.merchantId : 0L;
                user.branchBackendId = userDto != null && userDto.branchId != null ? userDto.branchId : 0L;
                user.phoneVerified = userDto == null || userDto.phoneVerified == null || userDto.phoneVerified;
                user.adminId = "";
                if (user.createdAt <= 0) {
                    user.createdAt = System.currentTimeMillis();
                }
                user.failedLoginAttempts = 0;
                user.lockedUntil = 0;
                user.backendId = userDto != null ? userDto.id : 0L;
                if (userDto != null) {
                    if (userDto.terminalId != null) {
                        user.terminalId = userDto.terminalId;
                    }
                    if (userDto.serverIp != null) {
                        user.serverIp = userDto.serverIp;
                    }
                    if (userDto.serverPort != null) {
                        user.serverPort = userDto.serverPort;
                    }
                }

                if (user.id > 0) {
                    userDao.update(user);
                } else {
                    userDao.insert(user);
                }

                cacheOrUpdateMerchantLocally(merchantDao, form, userDto);
                cacheDerivedAccounts(userDao, form, userDto);
                saved = true;
            } catch (Exception e) {
                Log.w(TAG, "Failed to cache user locally", e);
            } finally {
                if (callback != null) {
                    boolean result = saved;
                    runOnUiThread(() -> callback.onComplete(result));
                }
            }
        }).start();
    }

    private void cacheOrUpdateMerchantLocally(MerchantDao merchantDao,
                                              RegistrationForm form,
                                              ApiService.UserDto userDto) {
        if (merchantDao == null) {
            return;
        }

        String merchantCode = userDto != null ? trimToEmpty(userDto.merchantCode) : "";
        if (merchantCode.isEmpty()) {
            return;
        }

        MerchantEntity merchant = merchantDao.getByCode(merchantCode);
        if (merchant == null && userDto != null && userDto.merchantId != null && userDto.merchantId > 0) {
            merchant = merchantDao.getByBackendId(userDto.merchantId);
        }
        if (merchant == null) {
            merchant = new MerchantEntity();
        }

        merchant.merchantCode = merchantCode;
        merchant.merchantNameLocation = userDto != null && userDto.storeName != null
                ? userDto.storeName
                : form.storeName;
        merchant.businessType = userDto != null
                ? BusinessTypeMccMapper.toMcc(userDto.businessType)
                : form.businessType;
        if (merchant.businessType == null || merchant.businessType.isEmpty()) {
            merchant.businessType = form.businessType;
        }
        merchant.storeAddress = userDto != null && userDto.storeAddress != null
                ? userDto.storeAddress
                : form.storeAddress;
        merchant.bankName = userDto != null && userDto.bankName != null ? userDto.bankName : form.bankName;
        merchant.branchCount = form.branchCount;
        merchant.branchAddresses = form.branchAddresses;
        merchant.accountCount = form.accountCount;

        if (userDto != null) {
            merchant.backendId = userDto.merchantId != null ? userDto.merchantId : 0L;
        }

        if (merchant.id > 0) {
            merchantDao.update(merchant);
        } else {
            merchantDao.insert(merchant);
        }
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private void cacheDerivedAccounts(UserDao userDao, RegistrationForm form, ApiService.UserDto ownerDto) {
        if (form.accountCount <= 0) {
            return;
        }

        String sharedPasswordHash = com.example.mysoftpos.utils.security.PasswordUtils.hashPassword(form.password);
        String basePhone = normalizePhone(form.phone);

        for (int accountIndex = 1; accountIndex <= form.accountCount; accountIndex++) {
            String accountPhone = buildAccountPhoneIdentifier(basePhone, accountIndex);
            String accountUsernameHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(accountPhone);

            UserEntity accountUser = userDao.findByUsernameHash(accountUsernameHash);
            if (accountUser == null) {
                accountUser = userDao.findByPhone(accountPhone);
            }

            if (accountUser == null) {
                accountUser = new UserEntity(
                        accountUsernameHash,
                        sharedPasswordHash,
                        form.fullName,
                        REGISTERED_USER_ROLE,
                        form.email,
                        accountPhone,
                        form.dob);
            }

            accountUser.usernameHash = accountUsernameHash;
            accountUser.username = accountPhone;
            accountUser.passwordHash = sharedPasswordHash;
            accountUser.displayName = form.fullName;
            accountUser.role = REGISTERED_USER_ROLE;
            accountUser.email = form.email;
            accountUser.phone = accountPhone;
            accountUser.dob = form.dob;
            accountUser.gender = form.gender;
            accountUser.merchantBackendId = ownerDto != null && ownerDto.merchantId != null ? ownerDto.merchantId : 0L;
            accountUser.branchBackendId = ownerDto != null && ownerDto.branchId != null ? ownerDto.branchId : 0L;
            accountUser.phoneVerified = ownerDto == null || ownerDto.phoneVerified == null || ownerDto.phoneVerified;
            accountUser.adminId = "";
            accountUser.backendId = 0L;
            accountUser.failedLoginAttempts = 0;
            accountUser.lockedUntil = 0;
            if (accountUser.createdAt <= 0) {
                accountUser.createdAt = System.currentTimeMillis();
            }

            if (accountUser.id > 0) {
                userDao.update(accountUser);
            } else {
                userDao.insert(accountUser);
            }
        }
    }

    private String buildAccountPhoneIdentifier(String basePhone, int accountIndex) {
        String normalized = normalizePhone(basePhone);
        if (accountIndex <= 0) {
            return normalized;
        }
        return normalized + accountIndex;
    }

    private void setRegisterLoading(boolean loading) {
        setFormEnabled(!loading);
        btnRegister.setText(loading ? R.string.processing : R.string.register_button);
    }

    private void setFormEnabled(boolean enabled) {
        etStoreName.setEnabled(enabled);
        etBankName.setEnabled(enabled);
        etBusinessType.setEnabled(enabled);
        etStoreAddress.setEnabled(enabled);
        etBranchCount.setEnabled(enabled);
        etBranchAddresses.setEnabled(enabled);
        etAccountCount.setEnabled(enabled);
        etFullName.setEnabled(enabled);
        etDob.setEnabled(enabled);
        etGender.setEnabled(enabled);
        etPhone.setEnabled(enabled);
        etEmail.setEnabled(enabled);
        etPassword.setEnabled(enabled);
        etConfirmPassword.setEnabled(enabled);
        cbTerms.setEnabled(enabled);
        btnRegister.setEnabled(enabled);
    }

    private void setupTermsText() {
        if (tvTermsText == null)
            return;

        String fullText = getString(R.string.register_terms_text);
        SpannableString spannableString = new SpannableString(fullText);

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

        String termsTarget = getString(R.string.terms_title);
        int termsStart = fullText.indexOf(termsTarget);
        if (termsStart >= 0) {
            spannableString.setSpan(termsSpan, termsStart, termsStart + termsTarget.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

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

        if (tvTitle != null)
            tvTitle.setText(title);
        if (tvContent != null)
            tvContent.setText(readRawTextFile(rawResId));
        if (btnClose != null)
            btnClose.setOnClickListener(v -> dialog.dismiss());

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

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager
                .getNetworkCapabilities(connectivityManager.getActiveNetwork());
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
    }

    private String normalizePhone(String phone) {
        String normalized = phone.replaceAll("[\\s()-]", "");
        if (normalized.startsWith("00")) {
            normalized = "+" + normalized.substring(2);
        }
        if (normalized.indexOf('+') > 0) {
            normalized = normalized.replace("+", "");
        }
        return normalized;
    }

    private String getTrimmedText(TextView editText) {
        return String.valueOf(editText.getText()).trim();
    }

    private String sanitizeAndApply(TextView editText) {
        String normalized = sanitizeText(getTrimmedText(editText));
        applyNormalizedText(editText, normalized);
        return normalized;
    }

    private String sanitizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String sanitizeBranchAddresses(String value) {
        if (value == null) {
            return "";
        }
        String[] lines = value.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String normalized = sanitizeText(line);
            if (normalized.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(normalized);
        }
        return sb.toString();
    }

    private int countNonEmptyLines(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String line : value.split("\\n")) {
            if (!line.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private int parsePositiveIntOrZero(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return -1;
        }
    }

    private void applyNormalizedText(TextView editText, String normalized) {
        String current = String.valueOf(editText.getText());
        if (!current.equals(normalized)) {
            editText.setText(normalized);
            if (editText instanceof EditText) {
                ((EditText) editText).setSelection(normalized.length());
            }
        }
    }

    private boolean isSupportedSelection(int arrayResId, String value) {
        String normalizedValue = sanitizeText(value).toLowerCase(Locale.ROOT);
        for (String option : getResources().getStringArray(arrayResId)) {
            if (sanitizeText(option).toLowerCase(Locale.ROOT).equals(normalizedValue)) {
                return true;
            }
        }
        return false;
    }

    private void clearFieldError(TextView editText) {
        editText.setError(null);
    }

    private void setFieldError(TextView editText, int errorResId) {
        editText.setError(getString(errorResId));
        if (forceFocusOnValidationError && !editText.hasFocus()) {
            editText.requestFocus();
        }
    }

    private boolean requireValue(TextView editText, String value) {
        if (!value.isEmpty()) {
            return false;
        }
        setFieldError(editText, R.string.common_required);
        return true;
    }

    private void showToast(int stringResId) {
        showToast(stringResId, Toast.LENGTH_SHORT);
    }

    private void showToast(int stringResId, int duration) {
        Toast.makeText(this, stringResId, duration).show();
    }

    private void navigateToLogin(String prefillIdentifier) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_PREFILL_IDENTIFIER, prefillIdentifier);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private static class RegistrationForm {
        String storeName;
        String bankName;
        String businessTypeSelection;
        String businessType;
        String storeAddress;
        int branchCount;
        String branchAddresses;
        int accountCount;
        String fullName;
        String dob;
        String gender;
        String phone;
        String email;
        String password;
        String confirmPassword;
    }

    private abstract static class SimpleAfterTextChangedWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    private interface LocalCacheCallback {
        void onComplete(boolean saved);
    }
}
