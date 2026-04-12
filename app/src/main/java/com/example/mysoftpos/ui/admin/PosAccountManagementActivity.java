package com.example.mysoftpos.ui.admin;

import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;
import com.example.mysoftpos.ui.BaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Admin: Merchant Management.
 * Show merchant list, then drill down to account (TID) list of selected merchant.
 */
public class PosAccountManagementActivity extends BaseActivity implements PosAccountAdapter.OnMerchantListener {

    private static final int MAX_TOKEN_WAIT_RETRIES = 15;
    private static final long TOKEN_WAIT_RETRY_DELAY_MS = 1200L;
    private static final String TID_REGEX = "^[A-Z0-9]{8}$";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable retryLoadRunnable = this::loadMerchants;
    private final ExecutorService accountNetworkExecutor = Executors.newCachedThreadPool();
    private final Map<Long, ApiService.TerminalDto> terminalByPosAccountId = new HashMap<>();
    private final Map<String, ApiService.TerminalDto> terminalByTid = new HashMap<>();
    private final Map<Long, String> passwordPreviewByAccountId = new HashMap<>();
    private final Map<Long, MerchantAccountAdapter.HostPreview> hostPreviewByAccountId = new HashMap<>();

    private PosAccountAdapter adapter;
    private TextView tvUserCount;
    private TextView tvHeaderSubtitle;
    private TextView tvBackendSource;
    private View layoutEmpty;
    private EditText etSearch;
    private RecyclerView rvUsers;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private View layoutSearch;
    private TextView tvEmptyTitle;
    private TextView tvEmptySubtitle;
    private View btnRetryConnection;

    private List<ApiService.MerchantDto> allMerchants = new ArrayList<>();
    private final Map<Long, Integer> missingTidCountByMerchantId = new HashMap<>();
    private int merchantBadgeRequestVersion = 0;
    private int tokenWaitRetryCount = 0;
    private boolean backendListAvailable = false;
    private boolean networkCallbackRegistered = false;
    private ConnectivityManager.NetworkCallback networkCallback;

    private interface OnPosAccountSavedListener {
        void onSaved(ApiService.PosAccountDto savedAccount);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pos_account_management);

        tvUserCount = findViewById(R.id.tvUserCount);
        tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle);
        tvBackendSource = findViewById(R.id.tvBackendSource);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        etSearch = findViewById(R.id.etSearch);
        rvUsers = findViewById(R.id.rvUsers);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        layoutSearch = findViewById(R.id.layoutSearch);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);
        btnRetryConnection = findViewById(R.id.btnRetryConnection);

        if (tvHeaderSubtitle != null) {
            tvHeaderSubtitle.setText(R.string.user_mgmt_subtitle);
        }
        if (tvBackendSource != null) {
            String baseUrl = ApiClient.getBaseUrl(this);
            tvBackendSource.setText(getString(R.string.user_mgmt_backend_source_format, baseUrl));
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PosAccountAdapter(this);
        rvUsers.setAdapter(adapter);

        fabAdd.setVisibility(View.VISIBLE);
        fabAdd.setOnClickListener(v -> showMerchantEditorDialog(null));

        if (btnRetryConnection != null) {
            btnRetryConnection.setOnClickListener(v -> loadMerchants());
        }

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                filterMerchants(s.toString().trim());
            }
        });

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> {
                loadMerchants();
                swipeRefresh.setRefreshing(false);
            });
        }

        initNetworkCallback();
        loadMerchants();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMerchants();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(retryLoadRunnable);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerNetworkCallback();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterNetworkCallback();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        accountNetworkExecutor.shutdownNow();
    }

    private void loadMerchants() {
        mainHandler.removeCallbacks(retryLoadRunnable);

        if (hasNoNetworkConnection()) {
            tokenWaitRetryCount = 0;
            showOfflineState();
            return;
        }

        String token = ApiClient.bearerToken(this);
        if (token.isEmpty() || "Bearer ".equals(token) || !ApiClient.isLoggedIn(this)) {
            clearRenderedMerchants();
            if (tokenWaitRetryCount < MAX_TOKEN_WAIT_RETRIES) {
                tokenWaitRetryCount++;
                showNonContentState(getString(R.string.user_mgmt_state_preparing_session_title),
                        getString(R.string.user_mgmt_state_preparing_session_subtitle), false);
                mainHandler.postDelayed(retryLoadRunnable, TOKEN_WAIT_RETRY_DELAY_MS);
            } else {
                showNonContentState(getString(R.string.user_mgmt_state_backend_session_unavailable_title),
                        getString(R.string.user_mgmt_state_backend_session_unavailable_subtitle), true);
            }
            return;
        }

        tokenWaitRetryCount = 0;
        showNonContentState(getString(R.string.user_mgmt_state_loading_title),
                getString(R.string.user_mgmt_state_loading_subtitle), false);

        ApiClient.getService(this).getMerchants(token).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiService.MerchantDto>> call,
                    @NonNull Response<List<ApiService.MerchantDto>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    clearRenderedMerchants();
                    showNonContentState(getString(R.string.user_mgmt_state_backend_unavailable_title),
                            getString(R.string.user_mgmt_state_load_users_failed_subtitle), true);
                    Toast.makeText(PosAccountManagementActivity.this,
                            getString(R.string.user_mgmt_error_code, response.code()), Toast.LENGTH_LONG).show();
                    return;
                }

                allMerchants = new ArrayList<>(response.body());
                backendListAvailable = true;
                merchantBadgeRequestVersion++;
                missingTidCountByMerchantId.clear();
                showContentChrome();
                filterMerchants(etSearch.getText().toString().trim());
                fetchMissingTidCounts(allMerchants, token, merchantBadgeRequestVersion);
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiService.MerchantDto>> call, @NonNull Throwable t) {
                clearRenderedMerchants();
                showNonContentState(getString(R.string.user_mgmt_state_backend_unavailable_title),
                        getString(R.string.user_mgmt_state_load_users_network_subtitle), true);
                Toast.makeText(PosAccountManagementActivity.this,
                        buildAdminNetworkErrorMessage(t), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterMerchants(String query) {
        if (!backendListAvailable) {
            return;
        }

        List<ApiService.MerchantDto> display;
        if (query.isEmpty()) {
            display = new ArrayList<>(allMerchants);
        } else {
            String q = query.toLowerCase();
            display = new ArrayList<>();
            for (ApiService.MerchantDto merchant : allMerchants) {
                String name = merchant.merchantName != null ? merchant.merchantName.toLowerCase() : "";
                String mid = merchant.merchantCode != null ? merchant.merchantCode.toLowerCase() : "";
                String address = merchant.storeAddress != null ? merchant.storeAddress.toLowerCase() : "";
                String businessType = merchant.businessType != null ? merchant.businessType.toLowerCase() : "";
                if (name.contains(q) || mid.contains(q) || address.contains(q) || businessType.contains(q)) {
                    display.add(merchant);
                }
            }
        }

        adapter.setMerchants(display);
        adapter.setMissingTidCounts(missingTidCountByMerchantId);
        tvUserCount.setText(getString(R.string.user_mgmt_count_format, display.size()));
        showContentChrome();

        if (display.isEmpty()) {
            if (allMerchants.isEmpty()) {
                showEmptyStateText(getString(R.string.user_mgmt_empty_title),
                        getString(R.string.user_mgmt_empty_subtitle));
            } else {
                showEmptyStateText(getString(R.string.user_mgmt_empty_filter_title),
                        getString(R.string.user_mgmt_empty_filter_subtitle));
            }
        } else {
            layoutEmpty.setVisibility(View.GONE);
        }
    }

    @Override
    public void onMerchantClick(ApiService.MerchantDto merchant) {
        if (hasNoNetworkConnection()) {
            showOfflineState();
            return;
        }
        showMerchantBranchesDialog(merchant);
    }

    @Override
    public void onMerchantEdit(ApiService.MerchantDto merchant) {
        showMerchantEditorDialog(merchant);
    }

    @Override
    public void onMerchantDelete(ApiService.MerchantDto merchant) {
        confirmDeleteMerchant(merchant);
    }

    private void showMerchantBranchesDialog(ApiService.MerchantDto merchant) {
        String token = ApiClient.bearerToken(this);
        if (token.isEmpty() || "Bearer ".equals(token)) {
            Toast.makeText(this, R.string.user_mgmt_state_backend_session_unavailable_title, Toast.LENGTH_SHORT).show();
            return;
        }

        ApiClient.getService(this).getMerchantBranches(token, merchant.id).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiService.BranchDto>> call,
                    @NonNull Response<List<ApiService.BranchDto>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    showMerchantAccountsDialog(merchant);
                    return;
                }
                showBranchListDialog(merchant, response.body());
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiService.BranchDto>> call, @NonNull Throwable t) {
                showMerchantAccountsDialog(merchant);
            }
        });
    }

    private void showBranchListDialog(ApiService.MerchantDto merchant, List<ApiService.BranchDto> branches) {
        List<ApiService.BranchDto> branchList = new ArrayList<>(branches);
        String merchantName = merchant.merchantName != null && !merchant.merchantName.trim().isEmpty()
                ? merchant.merchantName
                : merchant.merchantCode;

        View content = getLayoutInflater().inflate(R.layout.dialog_branch_picker, null, false);
        RecyclerView rvBranchPicker = content.findViewById(R.id.rvBranchPicker);
        TextView tvBranchPickerTitle = content.findViewById(R.id.tvBranchPickerTitle);
        TextView tvBranchPickerHint = content.findViewById(R.id.tvBranchPickerHint);
        MaterialButton btnBranchPickerCancel = content.findViewById(R.id.btnBranchPickerCancel);
        tvBranchPickerTitle.setText(getString(R.string.user_mgmt_branch_dialog_title, merchantName));
        tvBranchPickerHint.setText(getString(R.string.user_mgmt_branch_picker_hint));

        rvBranchPicker.setLayoutManager(new LinearLayoutManager(this));
        final AlertDialog[] dialogHolder = new AlertDialog[1];
        BranchPickerAdapter pickerAdapter = new BranchPickerAdapter(branch -> {
            if (dialogHolder[0] != null && dialogHolder[0].isShowing()) {
                dialogHolder[0].dismiss();
            }
            showBranchAccountsDialog(merchant, branch);
        });
        pickerAdapter.submit(branchList);
        rvBranchPicker.setAdapter(pickerAdapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .create();
        dialogHolder[0] = dialog;
        if (btnBranchPickerCancel != null) {
            btnBranchPickerCancel.setOnClickListener(v -> {
                if (dialogHolder[0] != null && dialogHolder[0].isShowing()) {
                    dialogHolder[0].dismiss();
                }
            });
        }
        dialog.show();
        applyModernDialogStyle(dialog);
    }

    private void showBranchAccountsDialog(ApiService.MerchantDto merchant, ApiService.BranchDto branch) {
        String token = ApiClient.bearerToken(this);
        if (token.isEmpty() || "Bearer ".equals(token)) {
            Toast.makeText(this, R.string.user_mgmt_state_backend_session_unavailable_title, Toast.LENGTH_SHORT).show();
            return;
        }

        ApiClient.getService(this).getMerchantBranchAccounts(token, merchant.id, branch.id).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiService.PosAccountDto>> call,
                    @NonNull Response<List<ApiService.PosAccountDto>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    showMerchantAccountsDialog(merchant);
                    return;
                }
                showMerchantAccountsFromUsers(merchant, response.body(), branch);
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiService.PosAccountDto>> call, @NonNull Throwable t) {
                showMerchantAccountsDialog(merchant);
            }
        });
    }

    private void showMerchantAccountsDialog(ApiService.MerchantDto merchant) {
        String token = ApiClient.bearerToken(this);
        if (token.isEmpty() || "Bearer ".equals(token)) {
            Toast.makeText(this, R.string.user_mgmt_state_backend_session_unavailable_title, Toast.LENGTH_SHORT).show();
            return;
        }

        ApiClient.getService(this).getMerchantAccounts(token, merchant.id).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiService.PosAccountDto>> call,
                    @NonNull Response<List<ApiService.PosAccountDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    showMerchantAccountsFromUsers(merchant, response.body());
                    return;
                }
                // Fallback for older backend versions without /accounts endpoint.
                loadAccountsFromTerminals(merchant, token);
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiService.PosAccountDto>> call, @NonNull Throwable t) {
                loadAccountsFromTerminals(merchant, token);
            }
        });
    }

    private void loadAccountsFromTerminals(ApiService.MerchantDto merchant, String token) {
        ApiClient.getService(this).getTerminals(token).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiService.TerminalDto>> call,
                    @NonNull Response<List<ApiService.TerminalDto>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    showMerchantAccountsMessage(merchant, Collections.emptyList());
                    return;
                }
                List<String> lines = new ArrayList<>();
                int index = 1;
                for (ApiService.TerminalDto terminal : response.body()) {
                    if (terminal.merchant == null || terminal.merchant.id != merchant.id) {
                        continue;
                    }
                    String tid = terminal.terminalCode != null && !terminal.terminalCode.trim().isEmpty()
                            ? terminal.terminalCode
                            : getString(R.string.txn_detail_placeholder_dash);
                    lines.add(getString(R.string.user_mgmt_accounts_dialog_line, index++, tid));
                }
                showMerchantAccountsMessage(merchant, lines);
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiService.TerminalDto>> call, @NonNull Throwable t) {
                showMerchantAccountsMessage(merchant, Collections.emptyList());
            }
        });
    }

    private void showMerchantAccountsFromUsers(ApiService.MerchantDto merchant,
            List<ApiService.PosAccountDto> users,
            ApiService.BranchDto branch) {
        if (users == null || users.isEmpty()) {
            showMerchantAccountsMessage(merchant, Collections.emptyList());
            return;
        }

        int missingTidCount = 0;
        for (ApiService.PosAccountDto user : users) {
            if (normalizeTid(user.terminalId).isEmpty()) {
                missingTidCount++;
            }
        }
        missingTidCountByMerchantId.put(merchant.id, missingTidCount);
        adapter.setMissingTidCounts(missingTidCountByMerchantId);

        if (missingTidCount > 0) {
            Toast.makeText(this,
                    getString(R.string.user_mgmt_accounts_missing_tid_notice, missingTidCount),
                    Toast.LENGTH_LONG).show();
        }

        showMerchantAccountListDialog(merchant, users, branch);
    }

    private void showMerchantAccountsFromUsers(ApiService.MerchantDto merchant, List<ApiService.PosAccountDto> users) {
        showMerchantAccountsFromUsers(merchant, users, null);
    }

    private void showMerchantAccountListDialog(ApiService.MerchantDto merchant,
            List<ApiService.PosAccountDto> users,
            ApiService.BranchDto branch) {
        List<ApiService.PosAccountDto> accountUsers = new ArrayList<>(users);
        View content = getLayoutInflater().inflate(R.layout.dialog_merchant_accounts_manage, null, false);
        TextView tvMerchantMeta = content.findViewById(R.id.tvMerchantMeta);
        TextView tvAccountsEmpty = content.findViewById(R.id.tvAccountsEmpty);
        RecyclerView rvAccounts = content.findViewById(R.id.rvAccounts);
        View btnAddAccount = content.findViewById(R.id.btnAddAccount);
        View btnCloseAccounts = content.findViewById(R.id.btnCloseAccounts);

        String branchSuffix = "";
        if (branch != null) {
            String branchName = branch.branchName != null && !branch.branchName.trim().isEmpty()
                    ? branch.branchName
                    : branch.branchCode;
            branchSuffix = " - " + branchName;
        }
        tvMerchantMeta.setText(getString(
                R.string.user_mgmt_accounts_dialog_setup_hint,
                safe(merchant.merchantCode)));
        tvAccountsEmpty.setVisibility(accountUsers.isEmpty() ? View.VISIBLE : View.GONE);

        rvAccounts.setLayoutManager(new LinearLayoutManager(this));
        final MerchantAccountAdapter[] accountAdapterHolder = new MerchantAccountAdapter[1];
        MerchantAccountAdapter accountAdapter = new MerchantAccountAdapter(new MerchantAccountAdapter.OnAccountActionListener() {
            @Override
            public void onEdit(ApiService.PosAccountDto user) {
                showAccountEditorDialog(merchant, branch, user, savedAccount -> {
                    upsertAccountInList(accountUsers, savedAccount);
                    if (accountAdapterHolder[0] != null) {
                        accountAdapterHolder[0].setHostPreviews(new HashMap<>(hostPreviewByAccountId));
                        accountAdapterHolder[0].setPasswordPreviews(new HashMap<>(passwordPreviewByAccountId));
                        accountAdapterHolder[0].submit(new ArrayList<>(accountUsers));
                    }
                    tvAccountsEmpty.setVisibility(accountUsers.isEmpty() ? View.VISIBLE : View.GONE);
                    fetchTerminalMappingsForMerchant(merchant, branch, () -> {
                        if (accountAdapterHolder[0] != null) {
                            accountAdapterHolder[0]
                                    .setTerminalMappings(new HashMap<>(terminalByPosAccountId), new HashMap<>(terminalByTid));
                            accountAdapterHolder[0].setHostPreviews(new HashMap<>(hostPreviewByAccountId));
                            accountAdapterHolder[0].setPasswordPreviews(new HashMap<>(passwordPreviewByAccountId));
                            accountAdapterHolder[0].submit(new ArrayList<>(accountUsers));
                        }
                    });
                });
            }

            @Override
            public void onDelete(ApiService.PosAccountDto user) {
                confirmDeleteAccount(merchant, user);
            }

            @Override
            public void onResetPassword(ApiService.PosAccountDto user) {
                showResetPasswordDialog(user);
            }
        });
        accountAdapter.setTerminalMappings(new HashMap<>(), new HashMap<>());
        accountAdapter.setHostPreviews(new HashMap<>(hostPreviewByAccountId));
        accountAdapter.setPasswordPreviews(new HashMap<>(passwordPreviewByAccountId));
        accountAdapter.submit(accountUsers);
        accountAdapterHolder[0] = accountAdapter;
        rvAccounts.setAdapter(accountAdapter);

        fetchTerminalMappingsForMerchant(merchant, branch, () -> {
            accountAdapter.setTerminalMappings(new HashMap<>(terminalByPosAccountId), new HashMap<>(terminalByTid));
            accountAdapter.setHostPreviews(new HashMap<>(hostPreviewByAccountId));
            accountAdapter.setPasswordPreviews(new HashMap<>(passwordPreviewByAccountId));
            accountAdapter.submit(accountUsers);
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.user_mgmt_accounts_dialog_title) + " - " + resolveMerchantName(merchant) + branchSuffix)
                .setView(content)
                .create();

        btnAddAccount.setOnClickListener(v -> showAccountEditorDialog(merchant, branch, null, savedAccount -> {
            upsertAccountInList(accountUsers, savedAccount);
            accountAdapter.setHostPreviews(new HashMap<>(hostPreviewByAccountId));
            accountAdapter.setPasswordPreviews(new HashMap<>(passwordPreviewByAccountId));
            accountAdapter.submit(new ArrayList<>(accountUsers));
            tvAccountsEmpty.setVisibility(accountUsers.isEmpty() ? View.VISIBLE : View.GONE);
            fetchTerminalMappingsForMerchant(merchant, branch, () -> {
                accountAdapter.setTerminalMappings(new HashMap<>(terminalByPosAccountId), new HashMap<>(terminalByTid));
                accountAdapter.setHostPreviews(new HashMap<>(hostPreviewByAccountId));
                accountAdapter.setPasswordPreviews(new HashMap<>(passwordPreviewByAccountId));
                accountAdapter.submit(new ArrayList<>(accountUsers));
            });
        }));
        if (btnCloseAccounts != null) {
            btnCloseAccounts.setOnClickListener(v -> dialog.dismiss());
        }
        dialog.show();
        applyModernDialogStyle(dialog);
    }

    private void showMerchantEditorDialog(ApiService.MerchantDto merchant) {
        View content = getLayoutInflater().inflate(R.layout.dialog_merchant_form, null, false);
        TextView tvMerchantEditorTitle = content.findViewById(R.id.tvMerchantEditorTitle);
        TextView tvMerchantEditorSubtitle = content.findViewById(R.id.tvMerchantEditorSubtitle);
        EditText etMerchantCode = content.findViewById(R.id.etMerchantCode);
        EditText etMerchantName = content.findViewById(R.id.etMerchantName);
        EditText etBankName = content.findViewById(R.id.etBankName);
        EditText etBusinessType = content.findViewById(R.id.etBusinessType);
        EditText etStoreAddress = content.findViewById(R.id.etStoreAddress);
        TextInputLayout tilMerchantCode = content.findViewById(R.id.tilMerchantCode);
        TextInputLayout tilMerchantName = content.findViewById(R.id.tilMerchantName);
        TextInputLayout tilBankName = content.findViewById(R.id.tilBankName);
        TextInputLayout tilBusinessType = content.findViewById(R.id.tilBusinessType);
        TextInputLayout tilStoreAddress = content.findViewById(R.id.tilStoreAddress);
        MaterialButton btnCancel = content.findViewById(R.id.btnMerchantCancel);
        MaterialButton btnSave = content.findViewById(R.id.btnMerchantSave);

        boolean isCreate = merchant == null;
        if (tvMerchantEditorTitle != null) {
            tvMerchantEditorTitle.setText(isCreate ? R.string.user_mgmt_add_merchant : R.string.user_mgmt_edit_merchant);
        }
        if (tvMerchantEditorSubtitle != null) {
            tvMerchantEditorSubtitle.setText(R.string.user_mgmt_subtitle);
        }
        if (!isCreate) {
            etMerchantCode.setText(safe(merchant.merchantCode));
            etMerchantCode.setEnabled(false);
            etMerchantName.setText(safe(merchant.merchantName));
            etBankName.setText(safe(merchant.bankName));
            etBusinessType.setText(safe(merchant.businessType));
            etStoreAddress.setText(safe(merchant.storeAddress));
        }

        bindNextFocus(etMerchantCode, etMerchantName);
        bindNextFocus(etMerchantName, etBankName);
        bindNextFocus(etBankName, etBusinessType);
        bindNextFocus(etBusinessType, etStoreAddress);

        clearErrorOnInput(etMerchantCode, tilMerchantCode);
        clearErrorOnInput(etMerchantName, tilMerchantName);
        clearErrorOnInput(etBankName, tilBankName);
        clearErrorOnInput(etBusinessType, tilBusinessType);
        clearErrorOnInput(etStoreAddress, tilStoreAddress);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .create();
        dialog.setOnShowListener(d -> {
            if (btnCancel != null) {
                btnCancel.setOnClickListener(v -> dialog.dismiss());
            }

            if (btnSave == null) {
                return;
            }

            btnSave.setOnClickListener(v -> {
                String token = ApiClient.bearerToken(this);
                if (token.isEmpty() || "Bearer ".equals(token)) {
                    Toast.makeText(this, R.string.user_mgmt_state_backend_session_unavailable_title, Toast.LENGTH_SHORT).show();
                    return;
                }

                clearFieldError(tilMerchantCode);
                clearFieldError(tilMerchantName);
                clearFieldError(tilBankName);
                clearFieldError(tilBusinessType);
                clearFieldError(tilStoreAddress);

                String merchantCode = safe(etMerchantCode.getText().toString()).toUpperCase(Locale.ROOT);
                String merchantName = safe(etMerchantName.getText().toString());
                String bankName = safe(etBankName.getText().toString()).toUpperCase(Locale.ROOT);

                if (merchantName.isEmpty()) {
                    setFieldError(tilMerchantName, getString(R.string.common_required));
                    etMerchantName.requestFocus();
                    return;
                }
                if (isCreate && merchantCode.isEmpty()) {
                    setFieldError(tilMerchantCode, getString(R.string.common_required));
                    etMerchantCode.requestFocus();
                    return;
                }

                Map<String, String> body = new HashMap<>();
                if (isCreate) {
                    body.put("merchantCode", merchantCode);
                }
                body.put("merchantName", merchantName);
                body.put("bankName", bankName);
                body.put("businessType", safe(etBusinessType.getText().toString()));
                body.put("storeAddress", safe(etStoreAddress.getText().toString()));

                btnSave.setEnabled(false);
                btnSave.setText(R.string.user_mgmt_account_editor_saving);
                if (btnCancel != null) {
                    btnCancel.setEnabled(false);
                }

                if (isCreate) {
                    ApiClient.getService(this).createMerchant(token, body).enqueue(new Callback<>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiService.MerchantDto> call,
                                @NonNull Response<ApiService.MerchantDto> response) {
                            btnSave.setEnabled(true);
                            btnSave.setText(R.string.common_save);
                            if (btnCancel != null) {
                                btnCancel.setEnabled(true);
                            }
                            if (!response.isSuccessful()) {
                                Toast.makeText(PosAccountManagementActivity.this,
                                        getString(R.string.user_mgmt_error_code, response.code()),
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            dialog.dismiss();
                            loadMerchants();
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiService.MerchantDto> call, @NonNull Throwable t) {
                            btnSave.setEnabled(true);
                            btnSave.setText(R.string.common_save);
                            if (btnCancel != null) {
                                btnCancel.setEnabled(true);
                            }
                            Toast.makeText(PosAccountManagementActivity.this,
                                    buildAdminNetworkErrorMessage(t),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }

                ApiClient.getService(this).updateMerchant(token, merchant.id, body).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiService.MerchantDto> call,
                            @NonNull Response<ApiService.MerchantDto> response) {
                        btnSave.setEnabled(true);
                        btnSave.setText(R.string.common_save);
                        if (btnCancel != null) {
                            btnCancel.setEnabled(true);
                        }
                        if (!response.isSuccessful()) {
                            Toast.makeText(PosAccountManagementActivity.this,
                                    getString(R.string.user_mgmt_error_code, response.code()),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        dialog.dismiss();
                        loadMerchants();
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiService.MerchantDto> call, @NonNull Throwable t) {
                        btnSave.setEnabled(true);
                        btnSave.setText(R.string.common_save);
                        if (btnCancel != null) {
                            btnCancel.setEnabled(true);
                        }
                        Toast.makeText(PosAccountManagementActivity.this,
                                buildAdminNetworkErrorMessage(t),
                                Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
        dialog.show();
        applyModernDialogStyle(dialog);
    }

    private void confirmDeleteMerchant(ApiService.MerchantDto merchant) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.user_mgmt_delete_merchant)
                .setMessage(getString(R.string.user_mgmt_delete_merchant_confirm, resolveMerchantName(merchant)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.common_delete, (d, w) -> deleteMerchant(merchant))
                .show();
    }

    private void deleteMerchant(ApiService.MerchantDto merchant) {
        String token = ApiClient.bearerToken(this);
        if (token.isEmpty() || "Bearer ".equals(token)) {
            Toast.makeText(this, R.string.user_mgmt_state_backend_session_unavailable_title, Toast.LENGTH_SHORT).show();
            return;
        }
        ApiClient.getService(this).deleteMerchant(token, merchant.id).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, String>> call,
                    @NonNull Response<Map<String, String>> response) {
                if (!response.isSuccessful()) {
                    if (response.code() == 409) {
                        Toast.makeText(PosAccountManagementActivity.this,
                                R.string.user_mgmt_delete_merchant_blocked,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    Toast.makeText(PosAccountManagementActivity.this,
                            getString(R.string.user_mgmt_error_code, response.code()),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(PosAccountManagementActivity.this, R.string.user_mgmt_delete_success, Toast.LENGTH_SHORT).show();
                loadMerchants();
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, String>> call, @NonNull Throwable t) {
                Toast.makeText(PosAccountManagementActivity.this,
                        buildAdminNetworkErrorMessage(t),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showAccountEditorDialog(ApiService.MerchantDto merchant,
            ApiService.BranchDto branch,
            ApiService.PosAccountDto user,
            OnPosAccountSavedListener onSavedListener) {
        View content = getLayoutInflater().inflate(R.layout.dialog_merchant_account_form, null, false);
        NestedScrollView svAccountEditor = content.findViewById(R.id.svAccountEditor);
        EditText etFullName = content.findViewById(R.id.etAccountFullName);
        EditText etPhone = content.findViewById(R.id.etAccountPhone);
        EditText etEmail = content.findViewById(R.id.etAccountEmail);
        EditText etPassword = content.findViewById(R.id.etAccountPassword);
        EditText etTid = content.findViewById(R.id.etAccountTid);
        EditText etServerIp = content.findViewById(R.id.etAccountServerIp);
        EditText etServerPort = content.findViewById(R.id.etAccountServerPort);
        TextInputLayout tilFullName = content.findViewById(R.id.tilAccountFullName);
        TextInputLayout tilPhone = content.findViewById(R.id.tilAccountPhone);
        TextInputLayout tilEmail = content.findViewById(R.id.tilAccountEmail);
        TextInputLayout tilPassword = content.findViewById(R.id.tilAccountPassword);
        TextInputLayout tilTid = content.findViewById(R.id.tilAccountTid);
        TextInputLayout tilServerIp = content.findViewById(R.id.tilAccountServerIp);
        TextInputLayout tilServerPort = content.findViewById(R.id.tilAccountServerPort);
        MaterialButton btnCancel = content.findViewById(R.id.btnAccountCancel);
        MaterialButton btnSave = content.findViewById(R.id.btnAccountSave);
        MaterialButton btnTest = content.findViewById(R.id.btnAccountTestConnection);

        boolean isCreate = user == null;
        if (!isCreate) {
            etFullName.setText(safe(user.fullName));
            String accountLogin = safe(user.username);
            etPhone.setText(!accountLogin.isEmpty() ? accountLogin : safe(user.phone));
            etEmail.setText(safe(user.email));
            String accountTid = normalizeTid(user.terminalId);
            etTid.setText(accountTid);
            ApiService.TerminalDto mappedTerminal = resolveMappedTerminal(user);
            if (mappedTerminal != null) {
                if (accountTid.isEmpty()) {
                    String mappedTid = normalizeTid(mappedTerminal.terminalCode);
                    if (!mappedTid.isEmpty()) {
                        etTid.setText(mappedTid);
                    }
                }
                if (safe(etServerIp.getText().toString()).isEmpty() && !safe(mappedTerminal.serverIp).isEmpty()) {
                    etServerIp.setText(safe(mappedTerminal.serverIp));
                }
                if (safe(etServerPort.getText().toString()).isEmpty() && mappedTerminal.serverPort != null) {
                    etServerPort.setText(String.valueOf(mappedTerminal.serverPort));
                }
            }
            if (tilPassword != null) {
                tilPassword.setVisibility(View.GONE);
            }
        }

        bindNextFocus(etFullName, etPhone);
        bindNextFocus(etPhone, etEmail);
        bindNextFocus(etEmail, isCreate ? etPassword : etTid);
        if (isCreate) {
            bindNextFocus(etPassword, etTid);
        }
        bindNextFocus(etTid, etServerIp);
        bindNextFocus(etServerIp, etServerPort);

        clearErrorOnInput(etFullName, tilFullName);
        clearErrorOnInput(etPhone, tilPhone);
        clearErrorOnInput(etEmail, tilEmail);
        clearErrorOnInput(etPassword, tilPassword);
        clearErrorOnInput(etTid, tilTid);
        clearErrorOnInput(etServerIp, tilServerIp);
        clearErrorOnInput(etServerPort, tilServerPort);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isCreate ? R.string.user_mgmt_add_account : R.string.user_mgmt_edit_account)
                .setView(content)
                .create();
        dialog.setOnShowListener(d -> {
            if (btnCancel != null) {
                btnCancel.setOnClickListener(v -> dialog.dismiss());
            }
            if (btnTest != null) {
                btnTest.setOnClickListener(v -> testAccountConnection(
                        svAccountEditor,
                        etServerIp,
                        etServerPort,
                        tilServerIp,
                        tilServerPort,
                        btnTest,
                        btnSave,
                        btnCancel));
            }
            if (btnSave == null) {
                return;
            }

            etServerPort.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
                boolean imeDone = actionId == EditorInfo.IME_ACTION_DONE;
                boolean enterUp = event != null
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_UP;
                if (imeDone || enterUp) {
                    btnSave.performClick();
                    return true;
                }
                return false;
            });

            btnSave.setOnClickListener(v -> {
                String token = ApiClient.bearerToken(this);
                if (token.isEmpty() || "Bearer ".equals(token)) {
                    Toast.makeText(this, R.string.user_mgmt_state_backend_session_unavailable_title, Toast.LENGTH_SHORT).show();
                    return;
                }

                clearFieldError(tilFullName);
                clearFieldError(tilPhone);
                clearFieldError(tilEmail);
                clearFieldError(tilPassword);
                clearFieldError(tilTid);
                clearFieldError(tilServerIp);
                clearFieldError(tilServerPort);

                String fullName = safe(etFullName.getText().toString());
                String phone = safe(etPhone.getText().toString());
                String email = safe(etEmail.getText().toString());
                String password = safe(etPassword.getText().toString());
                String tid = normalizeTid(etTid.getText().toString());
                etTid.setText(tid);
                if (fullName.isEmpty()) {
                    focusFirstError(svAccountEditor, etFullName, tilFullName, getString(R.string.common_required));
                    return;
                }
                if (phone.isEmpty()) {
                    focusFirstError(svAccountEditor, etPhone, tilPhone, getString(R.string.common_required));
                    return;
                }
                if (isCreate && password.isEmpty()) {
                    focusFirstError(svAccountEditor, etPassword, tilPassword, getString(R.string.common_required));
                    return;
                }
                if (tid.isEmpty()) {
                    focusFirstError(svAccountEditor, etTid, tilTid,
                            getString(R.string.user_mgmt_error_tid_invalid_strict));
                    return;
                }
                if (!tid.matches(TID_REGEX)) {
                    focusFirstError(svAccountEditor, etTid, tilTid,
                            getString(R.string.user_mgmt_error_tid_invalid_strict));
                    return;
                }

                String serverIp = safe(etServerIp.getText().toString());
                Integer serverPort = parseServerPort(etServerPort, tilServerPort, svAccountEditor);
                if (serverPort != null && serverPort == Integer.MIN_VALUE) {
                    return;
                }
                if ((serverIp.isEmpty() && serverPort != null) || (!serverIp.isEmpty() && serverPort == null)) {
                    if (serverIp.isEmpty()) {
                        focusFirstError(svAccountEditor, etServerIp, tilServerIp, getString(R.string.common_required));
                    } else {
                        focusFirstError(svAccountEditor, etServerPort, tilServerPort, getString(R.string.common_required));
                    }
                    return;
                }
                if (!serverIp.isEmpty() && tid.isEmpty()) {
                    focusFirstError(svAccountEditor, etTid, tilTid, getString(R.string.common_required));
                    return;
                }
                if (hasInvalidHost(serverIp)) {
                    focusFirstError(svAccountEditor, etServerIp, tilServerIp,
                            getString(R.string.user_mgmt_account_editor_invalid_host));
                    return;
                }

                ApiService.CreatePosAccountRequest body = new ApiService.CreatePosAccountRequest(
                        isCreate ? password : null,
                        fullName,
                        phone,
                        email,
                        isCreate ? null : safe(user.dob),
                        isCreate ? null : safe(user.gender),
                        safe(merchant.merchantName),
                        safe(merchant.businessType),
                        safe(merchant.storeAddress),
                        merchant.id,
                        branch != null ? Long.valueOf(branch.id) : (isCreate ? null : user.branchId),
                        tid,
                        serverIp,
                        serverPort);

                setAccountEditorLoadingState(btnSave, btnTest, btnCancel, true, false);
                if (isCreate) {
                    enqueueCreatePosAccount(token, body, new Callback<>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiService.PosAccountDto> call,
                                @NonNull Response<ApiService.PosAccountDto> response) {
                            setAccountEditorLoadingState(btnSave, btnTest, btnCancel, false, false);
                            if (!response.isSuccessful()) {
                                showAccountApiError(response, true);
                                return;
                            }
                            Toast.makeText(PosAccountManagementActivity.this,
                                    R.string.user_mgmt_user_created,
                                    Toast.LENGTH_SHORT).show();
                            if (response.body() != null && onSavedListener != null) {
                                rememberPasswordPreview(response.body().id, password);
                                rememberHostPreview(response.body().id, serverIp, serverPort);
                                onSavedListener.onSaved(response.body());
                            }
                            dialog.dismiss();
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiService.PosAccountDto> call, @NonNull Throwable t) {
                            setAccountEditorLoadingState(btnSave, btnTest, btnCancel, false, false);
                            Toast.makeText(PosAccountManagementActivity.this,
                                    buildAdminNetworkErrorMessage(t),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }

                enqueueUpdatePosAccount(token, user.id, body, new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiService.PosAccountDto> call,
                                           @NonNull Response<ApiService.PosAccountDto> response) {
                        setAccountEditorLoadingState(btnSave, btnTest, btnCancel, false, false);
                        if (!response.isSuccessful()) {
                            showAccountApiError(response, false);
                            return;
                        }
                        Toast.makeText(PosAccountManagementActivity.this,
                                R.string.user_mgmt_account_editor_save_success,
                                Toast.LENGTH_SHORT).show();
                        ApiService.PosAccountDto saved = response.body();
                        if (saved != null && onSavedListener != null) {
                            rememberPasswordPreview(saved.id, password);
                            rememberHostPreview(saved.id, serverIp, serverPort);
                            onSavedListener.onSaved(saved);
                        }
                        dialog.dismiss();
                    }


                    @Override
                    public void onFailure(@NonNull Call<ApiService.PosAccountDto> call, @NonNull Throwable t) {
                        setAccountEditorLoadingState(btnSave, btnTest, btnCancel, false, false);
                        Toast.makeText(PosAccountManagementActivity.this,
                                buildAdminNetworkErrorMessage(t),
                                Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
        dialog.show();
        applyTallDialogStyle(dialog);
    }

    private void applyModernDialogStyle(AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawableResource(android.R.color.transparent);
        int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
        window.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void applyTallDialogStyle(AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawableResource(android.R.color.transparent);
        int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.94f);
        int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.88f);
        window.setLayout(width, height);
    }

    private void setAccountEditorButtonsEnabled(View save, View cancel, View test, boolean enabled) {
        if (save != null) {
            save.setEnabled(enabled);
        }
        if (cancel != null) {
            cancel.setEnabled(enabled);
        }
        if (test != null) {
            test.setEnabled(enabled);
        }
    }

    private void setAccountEditorLoadingState(MaterialButton btnSave,
            MaterialButton btnTest,
            MaterialButton btnCancel,
            boolean isSaving,
            boolean isTesting) {
        if (btnSave != null) {
            btnSave.setText(isSaving ? R.string.user_mgmt_account_editor_saving : R.string.user_mgmt_account_editor_save_label);
            btnSave.setIcon(null);
        }
        if (btnTest != null) {
            btnTest.setText(isTesting ? R.string.user_mgmt_account_editor_testing : R.string.user_mgmt_account_editor_test_label);
            btnTest.setIcon(null);
        }
        boolean enabled = !isSaving && !isTesting;
        setAccountEditorButtonsEnabled(btnSave, btnCancel, btnTest, enabled);
    }


    private void setFieldError(TextInputLayout til, String message) {
        if (til == null) {
            return;
        }
        til.setErrorEnabled(true);
        til.setError(message);
    }

    private void clearFieldError(TextInputLayout til) {
        if (til == null) {
            return;
        }
        til.setError(null);
        til.setErrorEnabled(false);
    }

    private void clearErrorOnInput(EditText input, TextInputLayout til) {
        if (input == null || til == null) {
            return;
        }
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                clearFieldError(til);
            }
        });
    }

    private void bindNextFocus(EditText current, View next) {
        if (current == null || next == null) {
            return;
        }
        current.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            boolean imeNext = actionId == EditorInfo.IME_ACTION_NEXT;
            boolean enterUp = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (imeNext || enterUp) {
                next.requestFocus();
                return true;
            }
            return false;
        });
    }

    private void focusFirstError(NestedScrollView scrollView,
            EditText field,
            TextInputLayout container,
            String message) {
        setFieldError(container, message);
        if (field != null) {
            field.requestFocus();
            if (scrollView != null) {
                scrollView.post(() -> scrollView.smoothScrollTo(0, field.getTop()));
            }
        }
    }

    private Integer parseServerPort(EditText etServerPort,
            TextInputLayout tilServerPort,
            NestedScrollView scrollView) {
        String portText = safe(etServerPort.getText().toString());
        if (portText.isEmpty()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(portText);
            if (parsed <= 0 || parsed > 65535) {
                focusFirstError(scrollView, etServerPort, tilServerPort,
                        getString(R.string.user_mgmt_account_editor_invalid_port));
                return Integer.MIN_VALUE;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            focusFirstError(scrollView, etServerPort, tilServerPort,
                    getString(R.string.user_mgmt_account_editor_invalid_port));
            return Integer.MIN_VALUE;
        }
    }

    private void confirmDeleteAccount(ApiService.MerchantDto merchant, ApiService.PosAccountDto user) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.user_mgmt_delete_account)
                .setMessage(getString(R.string.user_mgmt_delete_account_confirm, resolveAccountIdentity(user)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.common_delete, (d, w) -> deleteAccount(merchant, user))
                .show();
    }

    private void deleteAccount(ApiService.MerchantDto merchant, ApiService.PosAccountDto user) {
        String token = ApiClient.bearerToken(this);
        if (token.isEmpty() || "Bearer ".equals(token)) {
            Toast.makeText(this, R.string.user_mgmt_state_backend_session_unavailable_title, Toast.LENGTH_SHORT).show();
            return;
        }
        enqueueDeletePosAccount(token, user.id, new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, String>> call,
                    @NonNull Response<Map<String, String>> response) {
                if (!response.isSuccessful()) {
                    showAccountApiError(response, false);
                    return;
                }
                Toast.makeText(PosAccountManagementActivity.this, R.string.user_mgmt_delete_success, Toast.LENGTH_SHORT).show();
                showMerchantAccountsDialog(merchant);
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, String>> call, @NonNull Throwable t) {
                Toast.makeText(PosAccountManagementActivity.this,
                        buildAdminNetworkErrorMessage(t),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showResetPasswordDialog(ApiService.PosAccountDto user) {
        if (user == null) {
            return;
        }
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.user_mgmt_reset_password);

        String identity = safe(user.username);
        if (identity.isEmpty()) {
            identity = resolveAccountIdentity(user);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.user_mgmt_reset_password_confirm)
                .setMessage(getString(R.string.user_mgmt_reset_password_prompt, identity))
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.common_save,
                        (dialog, which) -> resetAccountPassword(user, safe(input.getText().toString())))
                .show();
    }

    private void resetAccountPassword(ApiService.PosAccountDto user, String newPassword) {
        if (user == null) {
            return;
        }
        if (newPassword.length() < 8) {
            Toast.makeText(this, R.string.user_mgmt_error_password_required_create, Toast.LENGTH_LONG).show();
            return;
        }

        String token = ApiClient.bearerToken(this);
        if (token.isEmpty() || "Bearer ".equals(token)) {
            Toast.makeText(this, R.string.user_mgmt_state_backend_session_unavailable_title, Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, String> body = new HashMap<>();
        body.put("newPassword", newPassword);
        ApiClient.getService(this).resetPosAccountPassword(token, user.id, body).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, String>> call,
                                   @NonNull Response<Map<String, String>> response) {
                if (!response.isSuccessful()) {
                    showAccountApiError(response, false);
                    return;
                }
                rememberPasswordPreview(user.id, newPassword);
                Toast.makeText(PosAccountManagementActivity.this,
                        R.string.user_mgmt_reset_password_success,
                        Toast.LENGTH_SHORT).show();
                loadMerchants();
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, String>> call, @NonNull Throwable t) {
                Toast.makeText(PosAccountManagementActivity.this,
                        buildAdminNetworkErrorMessage(t),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void testAccountConnection(NestedScrollView scrollView,
            EditText etServerIp,
            EditText etServerPort,
            TextInputLayout tilServerIp,
            TextInputLayout tilServerPort,
            MaterialButton btnTest,
            MaterialButton btnSave,
            MaterialButton btnCancel) {
        clearFieldError(tilServerIp);
        clearFieldError(tilServerPort);

        String ip = safe(etServerIp.getText().toString());
        String portText = safe(etServerPort.getText().toString());
        if (ip.isEmpty()) {
            focusFirstError(scrollView, etServerIp, tilServerIp, getString(R.string.common_required));
            return;
        }
        if (hasInvalidHost(ip)) {
            focusFirstError(scrollView, etServerIp, tilServerIp,
                    getString(R.string.user_mgmt_account_editor_invalid_host));
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portText);
            if (port <= 0 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (Exception ex) {
            focusFirstError(scrollView, etServerPort, tilServerPort,
                    getString(R.string.user_mgmt_account_editor_invalid_port));
            return;
        }

        setAccountEditorLoadingState(btnSave, btnTest, btnCancel, false, true);
        accountNetworkExecutor.execute(() -> {
            boolean connected = false;
            String reason = null;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), 5000);
                connected = true;
            } catch (Exception e) {
                reason = e.getMessage();
            }
            final boolean ok = connected;
            final String failureReason = reason;
            runOnUiThread(() -> {
                setAccountEditorLoadingState(btnSave, btnTest, btnCancel, false, false);
                if (ok) {
                    Toast.makeText(this, R.string.user_mgmt_account_editor_test_success, Toast.LENGTH_SHORT).show();
                } else {
                    String suffix = failureReason == null || failureReason.trim().isEmpty()
                            ? ""
                            : ": " + failureReason;
                    Toast.makeText(this,
                            getString(R.string.user_mgmt_account_editor_test_failed) + suffix,
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void fetchTerminalMappingsForMerchant(ApiService.MerchantDto merchant,
            ApiService.BranchDto branch,
            Runnable onComplete) {
        terminalByPosAccountId.clear();
        terminalByTid.clear();

        String token = ApiClient.bearerToken(this);
        if (token.isEmpty() || "Bearer ".equals(token)) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        ApiClient.getService(this).getTerminals(token).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiService.TerminalDto>> call,
                    @NonNull Response<List<ApiService.TerminalDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (ApiService.TerminalDto terminal : response.body()) {
                        if (terminal.merchant == null || terminal.merchant.id != merchant.id) {
                            continue;
                        }
                        if (branch != null && terminal.branchId != null && !terminal.branchId.equals(branch.id)) {
                            continue;
                        }
                        if (terminal.posAccountId != null) {
                            terminalByPosAccountId.put(terminal.posAccountId, terminal);
                        }
                        String tid = normalizeTid(terminal.terminalCode);
                        if (!tid.isEmpty()) {
                            terminalByTid.put(tid, terminal);
                        }
                    }
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiService.TerminalDto>> call, @NonNull Throwable t) {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    private ApiService.TerminalDto resolveMappedTerminal(ApiService.PosAccountDto user) {
        if (user == null) {
            return null;
        }
        ApiService.TerminalDto byAccount = terminalByPosAccountId.get(user.id);
        if (byAccount != null) {
            return byAccount;
        }
        String tid = normalizeTid(user.terminalId);
        if (tid.isEmpty()) {
            return null;
        }
        return terminalByTid.get(tid);
    }

    private boolean hasInvalidHost(String host) {
        String normalized = safe(host);
        if (normalized.isEmpty()) {
            return true;
        }
        if (normalized.contains(" ") || normalized.contains("/") || normalized.contains("://")) {
            return true;
        }
        return !isValidIpv4(normalized) && !isValidHostname(normalized);
    }

    private boolean isValidIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidHostname(String host) {
        String normalized = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        if (normalized.isEmpty() || normalized.length() > 253) {
            return false;
        }
        if ("localhost".equalsIgnoreCase(normalized)) {
            return true;
        }
        String[] labels = normalized.split("\\.");
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63) {
                return false;
            }
            if (!Character.isLetterOrDigit(label.charAt(0))
                    || !Character.isLetterOrDigit(label.charAt(label.length() - 1))) {
                return false;
            }
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (!(Character.isLetterOrDigit(c) || c == '-')) {
                    return false;
                }
            }
        }
        return true;
    }

    private void showAccountApiError(Response<?> response, boolean isCreate) {
        String backendError = extractBackendError(response).toLowerCase(Locale.ROOT);
        int messageRes = 0;
        if (backendError.contains("phone number already registered")) {
            messageRes = R.string.user_mgmt_error_phone_exists;
        } else if (backendError.contains("username already registered")) {
            messageRes = R.string.user_mgmt_error_phone_exists;
        } else if (backendError.contains("email already registered")) {
            messageRes = R.string.user_mgmt_error_email_exists;
        } else if (backendError.contains("password is required")) {
            messageRes = R.string.user_mgmt_error_password_required_create;
        } else if (backendError.contains("terminal code already exists")) {
            messageRes = R.string.user_mgmt_error_tid_invalid_strict;
        } else if (backendError.contains("terminal id") && backendError.contains("8")) {
            messageRes = R.string.user_mgmt_error_tid_invalid_strict;
        } else if (backendError.contains("merchant not found")) {
            messageRes = R.string.user_mgmt_error_merchant_not_found;
        } else if (backendError.contains("access denied")) {
            messageRes = R.string.user_mgmt_error_access_denied;
        } else if (backendError.contains("branch not found") || backendError.contains("branch does not belong")) {
            messageRes = R.string.user_mgmt_error_branch_invalid;
        } else if (backendError.contains("pos account not found") || backendError.contains("user not found")) {
            messageRes = R.string.user_mgmt_error_account_not_found;
        }

        if (messageRes != 0) {
            Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show();
            return;
        }

        String rawError = extractBackendError(response);
        if (!safe(rawError).isEmpty()) {
            Toast.makeText(this, rawError, Toast.LENGTH_LONG).show();
            return;
        }

        if (isCreate) {
            Toast.makeText(this, R.string.user_mgmt_error_create_account_failed, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, getString(R.string.user_mgmt_error_code, response.code()), Toast.LENGTH_LONG).show();
    }

    private String extractBackendError(Response<?> response) {
        if (response == null) {
            return "";
        }
        okhttp3.ResponseBody responseBody = response.errorBody();
        if (responseBody == null) {
            return "";
        }
        try (okhttp3.ResponseBody errorBody = responseBody) {
            String raw = errorBody.string();
            if (raw.trim().isEmpty()) {
                return "";
            }
            JSONObject json = new JSONObject(raw);
            String value = json.optString("error", "");
            if (!value.trim().isEmpty()) {
                return value;
            }
            return json.optString("message", "");
        } catch (Exception ignore) {
            return "";
        }
    }


    private String resolveAccountIdentity(ApiService.PosAccountDto user) {
        if (user == null) {
            return getString(R.string.txn_detail_placeholder_dash);
        }
        if (user.fullName != null && !user.fullName.trim().isEmpty()) {
            return user.fullName.trim();
        }
        if (user.phone != null && !user.phone.trim().isEmpty()) {
            return user.phone.trim();
        }
        if (user.email != null && !user.email.trim().isEmpty()) {
            return user.email.trim();
        }
        return getString(R.string.txn_detail_placeholder_dash);
    }

    private String resolveMerchantName(ApiService.MerchantDto merchant) {
        if (merchant == null) {
            return getString(R.string.txn_detail_placeholder_dash);
        }
        if (merchant.merchantName != null && !merchant.merchantName.trim().isEmpty()) {
            return merchant.merchantName.trim();
        }
        if (merchant.merchantCode != null && !merchant.merchantCode.trim().isEmpty()) {
            return merchant.merchantCode.trim();
        }
        return getString(R.string.txn_detail_placeholder_dash);
    }

    private String normalizeTid(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildAdminNetworkErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return getString(R.string.user_mgmt_network_error_generic);
        }
        if (throwable instanceof SocketTimeoutException) {
            return getString(R.string.err_timeout_waiting);
        }
        String reason = safe(throwable.getMessage());
        if (reason.isEmpty()) {
            return getString(R.string.user_mgmt_network_error_generic);
        }
        return getString(R.string.user_mgmt_network_error, reason);
    }

    private void enqueueUpdatePosAccount(String token,
            long userId,
            ApiService.CreatePosAccountRequest body,
            Callback<ApiService.PosAccountDto> callback) {
        ApiClient.getService(this).updatePosAccount(token, userId, body).enqueue(callback);
    }

    private void enqueueCreatePosAccount(String token,
            ApiService.CreatePosAccountRequest body,
            Callback<ApiService.PosAccountDto> callback) {
        ApiClient.getService(this).createPosAccount(token, body).enqueue(callback);
    }

    private void enqueueDeletePosAccount(String token,
            long userId,
            Callback<Map<String, String>> callback) {
        ApiClient.getService(this).deletePosAccount(token, userId).enqueue(callback);
    }


    private void showMerchantAccountsMessage(ApiService.MerchantDto merchant, List<String> lines) {
        String merchantName = merchant.merchantName != null && !merchant.merchantName.trim().isEmpty()
                ? merchant.merchantName
                : merchant.merchantCode;
        String mid = merchant.merchantCode != null && !merchant.merchantCode.trim().isEmpty()
                ? merchant.merchantCode
                : getString(R.string.txn_detail_placeholder_dash);

        StringBuilder message = new StringBuilder();
        message.append(getString(R.string.user_mgmt_accounts_dialog_mid, mid));
        if (merchant.storeAddress != null && !merchant.storeAddress.trim().isEmpty()) {
            message.append("\n").append(merchant.storeAddress.trim());
        }

        if (lines.isEmpty()) {
            message.append("\n\n").append(getString(R.string.user_mgmt_accounts_dialog_empty));
        } else {
            message.append("\n\n");
            for (String line : lines) {
                message.append(line).append("\n");
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.user_mgmt_accounts_dialog_title) + " - " + merchantName)
                .setMessage(message.toString().trim())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void upsertAccountInList(List<ApiService.PosAccountDto> accountUsers, ApiService.PosAccountDto savedAccount) {
        if (accountUsers == null || savedAccount == null) {
            return;
        }
        for (int i = 0; i < accountUsers.size(); i++) {
            ApiService.PosAccountDto existing = accountUsers.get(i);
            if (existing != null && existing.id == savedAccount.id) {
                accountUsers.set(i, savedAccount);
                return;
            }
        }
        accountUsers.add(0, savedAccount);
    }

    private void rememberPasswordPreview(long accountId, String password) {
        if (accountId <= 0) {
            return;
        }
        String normalized = safe(password);
        if (normalized.isEmpty()) {
            return;
        }
        passwordPreviewByAccountId.put(accountId, normalized);
    }

    private void rememberHostPreview(long accountId, String serverIp, Integer serverPort) {
        if (accountId <= 0) {
            return;
        }
        String normalizedIp = safe(serverIp);
        if (normalizedIp.isEmpty() && serverPort == null) {
            hostPreviewByAccountId.remove(accountId);
            return;
        }
        hostPreviewByAccountId.put(accountId, new MerchantAccountAdapter.HostPreview(normalizedIp, serverPort));
    }

    private void showOfflineState() {
        backendListAvailable = false;
        clearRenderedMerchants();
        showNonContentState(getString(R.string.user_mgmt_state_backend_unavailable_title),
                getString(R.string.user_mgmt_state_load_users_network_subtitle), false);
        Toast.makeText(this, R.string.network_lost, Toast.LENGTH_SHORT).show();
    }

    private void clearRenderedMerchants() {
        adapter.setMerchants(new ArrayList<>());
        adapter.setMissingTidCounts(new HashMap<>());
        tvUserCount.setText(getString(R.string.user_mgmt_count_format, 0));
    }

    private void fetchMissingTidCounts(List<ApiService.MerchantDto> merchants,
            String token,
            int requestVersion) {
        if (merchants == null || merchants.isEmpty()) {
            return;
        }

        for (ApiService.MerchantDto merchant : merchants) {
            ApiClient.getService(this).getMerchantAccounts(token, merchant.id).enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<List<ApiService.PosAccountDto>> call,
                        @NonNull Response<List<ApiService.PosAccountDto>> response) {
                    if (requestVersion != merchantBadgeRequestVersion) {
                        return;
                    }
                    if (!response.isSuccessful() || response.body() == null) {
                        fetchMissingTidFromTerminalsFallback(merchant, token, requestVersion);
                        return;
                    }

                    int missingTidCount = 0;
                    for (ApiService.PosAccountDto user : response.body()) {
                        if (normalizeTid(user.terminalId).isEmpty()) {
                            missingTidCount++;
                        }
                    }
                    missingTidCountByMerchantId.put(merchant.id, missingTidCount);
                    runOnUiThread(() -> adapter.setMissingTidCounts(missingTidCountByMerchantId));
                }

                @Override
                public void onFailure(@NonNull Call<List<ApiService.PosAccountDto>> call, @NonNull Throwable t) {
                    fetchMissingTidFromTerminalsFallback(merchant, token, requestVersion);
                }
            });
        }
    }

    private void fetchMissingTidFromTerminalsFallback(ApiService.MerchantDto merchant,
            String token,
            int requestVersion) {
        ApiClient.getService(this).getTerminals(token).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiService.TerminalDto>> call,
                    @NonNull Response<List<ApiService.TerminalDto>> response) {
                if (requestVersion != merchantBadgeRequestVersion) {
                    return;
                }
                if (!response.isSuccessful() || response.body() == null || merchant.accountCount == null) {
                    return;
                }

                int assignedCount = 0;
                for (ApiService.TerminalDto terminal : response.body()) {
                    if (terminal.merchant != null && terminal.merchant.id == merchant.id
                            && terminal.terminalCode != null && !terminal.terminalCode.trim().isEmpty()) {
                        assignedCount++;
                    }
                }

                int missing = Math.max(merchant.accountCount - assignedCount, 0);
                missingTidCountByMerchantId.put(merchant.id, missing);
                runOnUiThread(() -> adapter.setMissingTidCounts(missingTidCountByMerchantId));
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiService.TerminalDto>> call, @NonNull Throwable t) {
                // Keep badge unknown/unset when both APIs cannot be fetched.
            }
        });
    }

    private void showNonContentState(String title, String subtitle, boolean showRetryButton) {
        layoutSearch.setVisibility(View.GONE);
        rvUsers.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
        if (tvEmptyTitle != null) {
            tvEmptyTitle.setText(title);
        }
        if (tvEmptySubtitle != null) {
            tvEmptySubtitle.setText(subtitle);
        }
        if (btnRetryConnection != null) {
            btnRetryConnection.setVisibility(showRetryButton ? View.VISIBLE : View.GONE);
        }
    }

    private void showContentChrome() {
        layoutSearch.setVisibility(View.VISIBLE);
        rvUsers.setVisibility(View.VISIBLE);
        if (btnRetryConnection != null) {
            btnRetryConnection.setVisibility(View.GONE);
        }
    }

    private void showEmptyStateText(String title, String subtitle) {
        showContentChrome();
        layoutEmpty.setVisibility(View.VISIBLE);
        if (tvEmptyTitle != null) {
            tvEmptyTitle.setText(title);
        }
        if (tvEmptySubtitle != null) {
            tvEmptySubtitle.setText(subtitle);
        }
        if (btnRetryConnection != null) {
            btnRetryConnection.setVisibility(View.GONE);
        }
    }

    private boolean hasNoNetworkConnection() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return true;
        }
        NetworkCapabilities capabilities = connectivityManager
                .getNetworkCapabilities(connectivityManager.getActiveNetwork());
        boolean connected = capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        return !connected;
    }

    private void initNetworkCallback() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            networkCallback = null;
            return;
        }

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull android.net.Network network) {
                runOnUiThread(() -> {
                    Toast.makeText(PosAccountManagementActivity.this, R.string.network_restored, Toast.LENGTH_SHORT).show();
                    loadMerchants();
                });
            }
        };
    }

    private void registerNetworkCallback() {
        if (networkCallbackRegistered || networkCallback == null) {
            return;
        }
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return;
        }
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
            networkCallbackRegistered = true;
        } catch (Exception ignored) {
        }
    }

    private void unregisterNetworkCallback() {
        if (!networkCallbackRegistered || networkCallback == null) {
            return;
        }
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return;
        }
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Exception ignored) {
        } finally {
            networkCallbackRegistered = false;
        }
    }
}
