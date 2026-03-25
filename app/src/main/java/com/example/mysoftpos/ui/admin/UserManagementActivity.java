package com.example.mysoftpos.ui.admin;

import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;
import com.example.mysoftpos.ui.BaseActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Admin: Merchant Management.
 * Show merchant list, then drill down to account (TID) list of selected merchant.
 */
public class UserManagementActivity extends BaseActivity implements UserAdapter.OnMerchantListener {

    private static final int MAX_TOKEN_WAIT_RETRIES = 15;
    private static final long TOKEN_WAIT_RETRY_DELAY_MS = 1200L;
    private static final String TID_REGEX = "^[A-Z0-9]{8}$";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable retryLoadRunnable = this::loadMerchants;
    private final ExecutorService accountNetworkExecutor = Executors.newCachedThreadPool();

    private UserAdapter adapter;
    private TextView tvUserCount;
    private View layoutEmpty;
    private EditText etSearch;
    private RecyclerView rvUsers;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private FloatingActionButton fabAdd;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_management);

        tvUserCount = findViewById(R.id.tvUserCount);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        etSearch = findViewById(R.id.etSearch);
        rvUsers = findViewById(R.id.rvUsers);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        fabAdd = findViewById(R.id.fabAdd);
        layoutSearch = findViewById(R.id.layoutSearch);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);
        btnRetryConnection = findViewById(R.id.btnRetryConnection);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserAdapter(this);
        rvUsers.setAdapter(adapter);

        // Merchant list is read-only in this phase.
        fabAdd.setVisibility(View.GONE);

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

        if (!isNetworkAvailable()) {
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

        ApiClient.getService(this).getMerchants(token).enqueue(new Callback<List<ApiService.MerchantDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiService.MerchantDto>> call,
                    @NonNull Response<List<ApiService.MerchantDto>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    clearRenderedMerchants();
                    showNonContentState(getString(R.string.user_mgmt_state_backend_unavailable_title),
                            getString(R.string.user_mgmt_state_load_users_failed_subtitle), true);
                    Toast.makeText(UserManagementActivity.this,
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
                Toast.makeText(UserManagementActivity.this,
                        getString(R.string.user_mgmt_network_error, t.getMessage()), Toast.LENGTH_SHORT).show();
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
                        getString(R.string.user_mgmt_empty_subtitle), false);
            } else {
                showEmptyStateText(getString(R.string.user_mgmt_empty_filter_title),
                        getString(R.string.user_mgmt_empty_filter_subtitle), false);
            }
        } else {
            layoutEmpty.setVisibility(View.GONE);
        }
    }

    @Override
    public void onMerchantClick(ApiService.MerchantDto merchant) {
        if (!isNetworkAvailable()) {
            showOfflineState();
            return;
        }
        showMerchantAccountsDialog(merchant);
    }

    @Override
    public void onMerchantLongClick(ApiService.MerchantDto merchant) {
        onMerchantClick(merchant);
    }

    private void showMerchantAccountsDialog(ApiService.MerchantDto merchant) {
        String token = ApiClient.bearerToken(this);
        if (token.isEmpty() || "Bearer ".equals(token)) {
            Toast.makeText(this, R.string.user_mgmt_state_backend_session_unavailable_title, Toast.LENGTH_SHORT).show();
            return;
        }

        ApiClient.getService(this).getMerchantAccounts(token, merchant.id).enqueue(new Callback<List<ApiService.UserDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiService.UserDto>> call,
                    @NonNull Response<List<ApiService.UserDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    showMerchantAccountsFromUsers(merchant, response.body());
                    return;
                }
                // Fallback for older backend versions without /accounts endpoint.
                loadAccountsFromTerminals(merchant, token);
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiService.UserDto>> call, @NonNull Throwable t) {
                loadAccountsFromTerminals(merchant, token);
            }
        });
    }

    private void loadAccountsFromTerminals(ApiService.MerchantDto merchant, String token) {
        ApiClient.getService(this).getTerminals(token).enqueue(new Callback<List<ApiService.TerminalDto>>() {
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

    private void showMerchantAccountsFromUsers(ApiService.MerchantDto merchant, List<ApiService.UserDto> users) {
        if (users == null || users.isEmpty()) {
            showMerchantAccountsMessage(merchant, Collections.emptyList());
            return;
        }

        int missingTidCount = 0;
        for (ApiService.UserDto user : users) {
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

        showMerchantAccountListDialog(merchant, users);
    }

    private void showMerchantAccountListDialog(ApiService.MerchantDto merchant, List<ApiService.UserDto> users) {
        List<ApiService.UserDto> accountUsers = new ArrayList<>(users);
        String merchantName = merchant.merchantName != null && !merchant.merchantName.trim().isEmpty()
                ? merchant.merchantName
                : merchant.merchantCode;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                buildAccountListItems(accountUsers));

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.user_mgmt_accounts_dialog_title) + " - " + merchantName)
                .setAdapter(adapter, (dialog, which) -> {
                    if (which < 0 || which >= accountUsers.size()) {
                        return;
                    }
                    ApiService.UserDto selectedUser = accountUsers.get(which);
                    showAccountConfigDialog(merchant, selectedUser);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private List<String> buildAccountListItems(List<ApiService.UserDto> users) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            ApiService.UserDto user = users.get(i);
            String identity = resolveAccountIdentity(user);
            String tid = normalizeTid(user.terminalId);
            String ip = safe(user.serverIp);
            String port = user.serverPort != null && user.serverPort > 0 ? String.valueOf(user.serverPort) : "-";
            String info = getString(
                    R.string.user_mgmt_accounts_dialog_item_format,
                    i + 1,
                    identity,
                    tid.isEmpty() ? getString(R.string.txn_detail_placeholder_dash) : tid,
                    ip.isEmpty() ? getString(R.string.txn_detail_placeholder_dash) : ip,
                    port);
            items.add(info);
        }
        return items;
    }

    private void showAccountConfigDialog(ApiService.MerchantDto merchant, ApiService.UserDto user) {
        View content = getLayoutInflater().inflate(R.layout.dialog_merchant_account_config, null, false);

        TextView tvHint = content.findViewById(R.id.tvAccountHint);
        EditText etTid = content.findViewById(R.id.etAccountTid);
        EditText etServerIp = content.findViewById(R.id.etAccountServerIp);
        EditText etServerPort = content.findViewById(R.id.etAccountServerPort);

        tvHint.setText(getString(R.string.user_mgmt_account_editor_hint, resolveAccountIdentity(user)));
        etTid.setText(normalizeTid(user.terminalId));
        etServerIp.setText(safe(user.serverIp));
        etServerPort.setText(user.serverPort != null && user.serverPort > 0 ? String.valueOf(user.serverPort) : "");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.user_mgmt_account_editor_title)
                .setView(content)
                .setNegativeButton(R.string.user_mgmt_account_editor_clear, null)
                .setNeutralButton(R.string.user_mgmt_account_editor_test, null)
                .setPositiveButton(R.string.common_save, null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button btnTest = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            Button btnClear = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            btnSave.setOnClickListener(v -> saveAccountConfig(dialog, merchant, user, etTid, etServerIp, etServerPort));
            btnTest.setOnClickListener(v -> testAccountConnection(etServerIp, etServerPort));
            btnClear.setOnClickListener(v -> clearAccountConfig(dialog, merchant, user));
        });
        dialog.show();
    }


    private void saveAccountConfig(AlertDialog dialog,
            ApiService.MerchantDto merchant,
            ApiService.UserDto user,
            EditText etTid,
            EditText etServerIp,
            EditText etServerPort) {
        String token = ApiClient.bearerToken(this);
        if (token.isEmpty() || "Bearer ".equals(token)) {
            Toast.makeText(this, R.string.user_mgmt_state_backend_session_unavailable_title, Toast.LENGTH_SHORT).show();
            return;
        }

        String tid = normalizeTid(etTid.getText().toString());
        if (!tid.isEmpty() && !tid.matches(TID_REGEX)) {
            etTid.setError(getString(R.string.user_mgmt_accounts_dialog_tid_invalid, 1));
            etTid.requestFocus();
            return;
        }

        String serverIp = safe(etServerIp.getText().toString());
        String portText = safe(etServerPort.getText().toString());
        Integer serverPort = null;
        if (!portText.isEmpty()) {
            try {
                int parsed = Integer.parseInt(portText);
                if (parsed <= 0 || parsed > 65535) {
                    etServerPort.setError(getString(R.string.user_mgmt_account_editor_invalid_port));
                    etServerPort.requestFocus();
                    return;
                }
                serverPort = parsed;
            } catch (NumberFormatException ex) {
                etServerPort.setError(getString(R.string.user_mgmt_account_editor_invalid_port));
                etServerPort.requestFocus();
                return;
            }
        }

        ApiService.CreateUserRequest body = new ApiService.CreateUserRequest(
                null,
                safe(user.fullName),
                safe(user.phone),
                safe(user.email),
                safe(user.dob),
                safe(user.gender),
                safe(user.storeName),
                safe(user.businessType),
                safe(user.storeAddress),
                user.merchantId,
                tid,
                serverIp,
                serverPort);

        setDialogButtonsEnabled(dialog, false);
        ApiClient.getService(this).updateUser(token, user.id, body).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiService.UserDto> call,
                    @NonNull Response<ApiService.UserDto> response) {
                setDialogButtonsEnabled(dialog, true);
                if (!response.isSuccessful()) {
                    Toast.makeText(UserManagementActivity.this,
                            getString(R.string.user_mgmt_error_code, response.code()),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(UserManagementActivity.this,
                        R.string.user_mgmt_account_editor_save_success,
                        Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                showMerchantAccountsDialog(merchant);
            }

            @Override
            public void onFailure(@NonNull Call<ApiService.UserDto> call, @NonNull Throwable t) {
                setDialogButtonsEnabled(dialog, true);
                Toast.makeText(UserManagementActivity.this,
                        getString(R.string.user_mgmt_network_error, t.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void clearAccountConfig(AlertDialog dialog,
            ApiService.MerchantDto merchant,
            ApiService.UserDto user) {
        String token = ApiClient.bearerToken(this);
        if (token.isEmpty() || "Bearer ".equals(token)) {
            Toast.makeText(this, R.string.user_mgmt_state_backend_session_unavailable_title, Toast.LENGTH_SHORT).show();
            return;
        }

        ApiService.CreateUserRequest body = new ApiService.CreateUserRequest(
                null,
                safe(user.fullName),
                safe(user.phone),
                safe(user.email),
                safe(user.dob),
                safe(user.gender),
                safe(user.storeName),
                safe(user.businessType),
                safe(user.storeAddress),
                user.merchantId,
                "",
                "",
                null);

        setDialogButtonsEnabled(dialog, false);
        ApiClient.getService(this).updateUser(token, user.id, body).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiService.UserDto> call,
                    @NonNull Response<ApiService.UserDto> response) {
                setDialogButtonsEnabled(dialog, true);
                if (!response.isSuccessful()) {
                    Toast.makeText(UserManagementActivity.this,
                            getString(R.string.user_mgmt_error_code, response.code()),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(UserManagementActivity.this,
                        R.string.user_mgmt_account_editor_clear_success,
                        Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                showMerchantAccountsDialog(merchant);
            }

            @Override
            public void onFailure(@NonNull Call<ApiService.UserDto> call, @NonNull Throwable t) {
                setDialogButtonsEnabled(dialog, true);
                Toast.makeText(UserManagementActivity.this,
                        getString(R.string.user_mgmt_network_error, t.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void testAccountConnection(EditText etServerIp, EditText etServerPort) {
        String ip = safe(etServerIp.getText().toString());
        String portText = safe(etServerPort.getText().toString());
        if (ip.isEmpty()) {
            etServerIp.setError(getString(R.string.common_required));
            etServerIp.requestFocus();
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portText);
            if (port <= 0 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (Exception ex) {
            etServerPort.setError(getString(R.string.user_mgmt_account_editor_invalid_port));
            etServerPort.requestFocus();
            return;
        }

        Toast.makeText(this, R.string.processing, Toast.LENGTH_SHORT).show();
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

    private void showMerchantAccountsEditorDialog(ApiService.MerchantDto merchant, List<ApiService.UserDto> users) {
        String merchantName = merchant.merchantName != null && !merchant.merchantName.trim().isEmpty()
                ? merchant.merchantName
                : merchant.merchantCode;
        String mid = merchant.merchantCode != null && !merchant.merchantCode.trim().isEmpty()
                ? merchant.merchantCode
                : getString(R.string.txn_detail_placeholder_dash);

        ScrollView scrollView = new ScrollView(this);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        scrollView.setPadding(pad, pad, pad, 0);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content);

        TextView tvHeader = new TextView(this);
        tvHeader.setText(getString(R.string.user_mgmt_accounts_dialog_setup_hint, mid));
        tvHeader.setTextColor(0xFF334155);
        tvHeader.setTextSize(13);
        content.addView(tvHeader);

        List<TidInputRow> rows = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            ApiService.UserDto user = users.get(i);
            int accountIndex = i + 1;

            TextView tvAccount = new TextView(this);
            tvAccount.setPadding(0, pad, 0, 8);
            tvAccount.setText(getString(R.string.user_mgmt_accounts_dialog_account_label,
                    accountIndex,
                    resolveAccountIdentity(user)));
            tvAccount.setTextColor(0xFF0F172A);
            tvAccount.setTextSize(14);
            content.addView(tvAccount);

            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setHint(getString(R.string.user_mgmt_accounts_dialog_tid_hint, accountIndex));
            input.setText(normalizeTid(user.terminalId));
            input.setSelectAllOnFocus(true);
            input.setPadding(24, 18, 24, 18);
            input.setBackgroundResource(R.drawable.bg_input_rounded);
            content.addView(input);

            rows.add(new TidInputRow(user, accountIndex, input));
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.user_mgmt_accounts_dialog_title) + " - " + merchantName)
                .setView(scrollView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.user_mgmt_accounts_dialog_save_tid, null)
                .create();

        dialog.setOnShowListener(d -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> submitTidUpdates(dialog, rows));
        });
        dialog.show();
    }

    private void submitTidUpdates(AlertDialog dialog, List<TidInputRow> rows) {
        if (!isNetworkAvailable()) {
            showOfflineState();
            return;
        }

        String token = ApiClient.bearerToken(this);
        if (token.isEmpty() || "Bearer ".equals(token)) {
            Toast.makeText(this, R.string.user_mgmt_state_backend_session_unavailable_title, Toast.LENGTH_SHORT).show();
            return;
        }

        List<TidUpdateRequest> updates = new ArrayList<>();
        for (TidInputRow row : rows) {
            String tid = normalizeTid(row.input.getText().toString());
            if (!tid.matches(TID_REGEX)) {
                row.input.setError(getString(R.string.user_mgmt_accounts_dialog_tid_invalid, row.accountIndex));
                row.input.requestFocus();
                return;
            }

            String currentTid = normalizeTid(row.user.terminalId);
            if (!tid.equals(currentTid)) {
                updates.add(new TidUpdateRequest(row.user, row.accountIndex, tid));
            }
        }

        if (updates.isEmpty()) {
            Toast.makeText(this, R.string.user_mgmt_accounts_dialog_tid_no_change, Toast.LENGTH_SHORT).show();
            return;
        }

        setDialogButtonsEnabled(dialog, false);
        updateTidRecursive(token, updates, 0, 0, 0, dialog);
    }

    private void updateTidRecursive(String token,
            List<TidUpdateRequest> updates,
            int index,
            int successCount,
            int failCount,
            AlertDialog dialog) {
        if (index >= updates.size()) {
            setDialogButtonsEnabled(dialog, true);
            dialog.dismiss();
            loadMerchants();
            if (failCount == 0) {
                Toast.makeText(this,
                        getString(R.string.user_mgmt_accounts_dialog_tid_update_success, successCount),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        getString(R.string.user_mgmt_accounts_dialog_tid_update_partial, successCount, failCount),
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        TidUpdateRequest request = updates.get(index);
        ApiService.UserDto user = request.user;
        ApiService.CreateUserRequest body = new ApiService.CreateUserRequest(
                null,
                safe(user.fullName),
                safe(user.phone),
                safe(user.email),
                safe(user.dob),
                safe(user.gender),
                safe(user.storeName),
                safe(user.businessType),
                safe(user.storeAddress),
                user.merchantId,
                request.tid,
                user.serverIp,
                user.serverPort);

        ApiClient.getService(this).updateUser(token, user.id, body).enqueue(new Callback<ApiService.UserDto>() {
            @Override
            public void onResponse(@NonNull Call<ApiService.UserDto> call,
                    @NonNull Response<ApiService.UserDto> response) {
                int nextSuccess = successCount;
                int nextFail = failCount;
                if (response.isSuccessful()) {
                    nextSuccess++;
                } else {
                    nextFail++;
                }
                updateTidRecursive(token, updates, index + 1, nextSuccess, nextFail, dialog);
            }

            @Override
            public void onFailure(@NonNull Call<ApiService.UserDto> call, @NonNull Throwable t) {
                updateTidRecursive(token, updates, index + 1, successCount, failCount + 1, dialog);
            }
        });
    }

    private void setDialogButtonsEnabled(AlertDialog dialog, boolean enabled) {
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positive != null) {
            positive.setEnabled(enabled);
        }
        if (negative != null) {
            negative.setEnabled(enabled);
        }
    }

    private String resolveAccountIdentity(ApiService.UserDto user) {
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

    private String normalizeTid(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static class TidInputRow {
        final ApiService.UserDto user;
        final int accountIndex;
        final EditText input;

        TidInputRow(ApiService.UserDto user, int accountIndex, EditText input) {
            this.user = user;
            this.accountIndex = accountIndex;
            this.input = input;
        }
    }

    private static class TidUpdateRequest {
        final ApiService.UserDto user;
        final int accountIndex;
        final String tid;

        TidUpdateRequest(ApiService.UserDto user, int accountIndex, String tid) {
            this.user = user;
            this.accountIndex = accountIndex;
            this.tid = tid;
        }
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
                public void onResponse(@NonNull Call<List<ApiService.UserDto>> call,
                        @NonNull Response<List<ApiService.UserDto>> response) {
                    if (requestVersion != merchantBadgeRequestVersion) {
                        return;
                    }
                    if (!response.isSuccessful() || response.body() == null) {
                        fetchMissingTidFromTerminalsFallback(merchant, token, requestVersion);
                        return;
                    }

                    int missingTidCount = 0;
                    for (ApiService.UserDto user : response.body()) {
                        if (normalizeTid(user.terminalId).isEmpty()) {
                            missingTidCount++;
                        }
                    }
                    missingTidCountByMerchantId.put(merchant.id, missingTidCount);
                    runOnUiThread(() -> adapter.setMissingTidCounts(missingTidCountByMerchantId));
                }

                @Override
                public void onFailure(@NonNull Call<List<ApiService.UserDto>> call, @NonNull Throwable t) {
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

    private void showEmptyStateText(String title, String subtitle, boolean showRetryButton) {
        showContentChrome();
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
                    Toast.makeText(UserManagementActivity.this, R.string.network_restored, Toast.LENGTH_SHORT).show();
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
