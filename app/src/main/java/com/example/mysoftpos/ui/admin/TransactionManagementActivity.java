package com.example.mysoftpos.ui.admin;

import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.dao.TransactionDao;
import com.example.mysoftpos.data.local.dao.PosAccountDao;
import com.example.mysoftpos.data.local.entity.TransactionEntity;
import com.example.mysoftpos.data.local.entity.PosAccountEntity;
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;
import com.example.mysoftpos.ui.BaseActivity;
import com.example.mysoftpos.utils.IntentKeys;
import com.example.mysoftpos.utils.format.AmountFormatUtils;
import com.example.mysoftpos.utils.format.DateTimeFormatUtils;
import com.example.mysoftpos.utils.security.PasswordUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Admin: View transactions from USER accounts only.
 * Features: Summary stats, user filter, status-colored pills.
 */
public class TransactionManagementActivity extends BaseActivity {

    private static final int MAX_TOKEN_WAIT_RETRIES = 15;
    private static final long TOKEN_WAIT_RETRY_DELAY_MS = 1200L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable retryLoadRunnable = this::loadUsersAndTransactions;

    private TxnAdapter adapter;
    private TextView tvTxnCount, tvApprovedCount, tvDeclinedCount, tvOtherCount;
    private Spinner spinnerUserFilter;
    private Spinner spinnerStoreFilter;
    private View layoutEmpty;
    private RecyclerView rvTransactions;
    private View layoutStats;
    private View layoutFilterBar;
    private TextView tvEmptyTitle;
    private TextView tvEmptySubtitle;
    private View btnRetryConnection;
    private View btnBackfillTransactions;

    private List<ApiService.TransactionRecordDto> userTransactions = new ArrayList<>();
    private final Map<Long, String> userIdToName = new LinkedHashMap<>();
    private final Map<Long, String> accountIdToStoreName = new LinkedHashMap<>();
    private int tokenWaitRetryCount = 0;
    private boolean backendTransactionsAvailable = false;
    private boolean networkCallbackRegistered = false;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Long filterMerchantId;
    private Long filterTerminalId;
    private boolean autoBackfillAttempted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_management);

        tvTxnCount = findViewById(R.id.tvTxnCount);
        tvApprovedCount = findViewById(R.id.tvApprovedCount);
        tvDeclinedCount = findViewById(R.id.tvDeclinedCount);
        tvOtherCount = findViewById(R.id.tvOtherCount);
        spinnerUserFilter = findViewById(R.id.spinnerUserFilter);
        spinnerStoreFilter = findViewById(R.id.spinnerStoreFilter);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        rvTransactions = findViewById(R.id.rvTransactions);
        layoutStats = findViewById(R.id.layoutStats);
        layoutFilterBar = findViewById(R.id.layoutFilterBar);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);
        btnRetryConnection = findViewById(R.id.btnRetryConnection);
        btnBackfillTransactions = findViewById(R.id.btnBackfillTransactions);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        long merchantIdArg = getIntent().getLongExtra(IntentKeys.MERCHANT_ID, -1L);
        long terminalIdArg = getIntent().getLongExtra(IntentKeys.TERMINAL_NUMERIC_ID, -1L);
        filterMerchantId = merchantIdArg > 0 ? merchantIdArg : null;
        filterTerminalId = terminalIdArg > 0 ? terminalIdArg : null;

        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TxnAdapter();
        rvTransactions.setAdapter(adapter);

        if (btnRetryConnection != null) {
            btnRetryConnection.setOnClickListener(v -> loadUsersAndTransactions());
        }
        if (btnBackfillTransactions != null) {
            btnBackfillTransactions.setOnClickListener(v -> triggerBackfillAction());
        }

        spinnerUserFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                applyCurrentFilter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        if (spinnerStoreFilter != null) {
            spinnerStoreFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    applyCurrentFilter();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        initNetworkCallback();
        loadUsersAndTransactions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUsersAndTransactions();
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

    private void loadUsersAndTransactions() {
        mainHandler.removeCallbacks(retryLoadRunnable);

        if (!isNetworkAvailable()) {
            tokenWaitRetryCount = 0;
            showOfflineState();
            return;
        }

        String token = ApiClient.bearerToken(this);
        if (token.isEmpty() || "Bearer ".equals(token) || !ApiClient.isLoggedIn(this)) {
            clearRenderedTransactions();
            if (tokenWaitRetryCount < MAX_TOKEN_WAIT_RETRIES) {
                tokenWaitRetryCount++;
                showNonContentState(getString(R.string.txn_mgmt_state_preparing_session_title),
                        getString(R.string.txn_mgmt_state_preparing_session_subtitle), false);
                mainHandler.postDelayed(retryLoadRunnable, TOKEN_WAIT_RETRY_DELAY_MS);
            } else {
                showNonContentState(getString(R.string.txn_mgmt_state_backend_session_unavailable_title),
                        getString(R.string.txn_mgmt_state_backend_session_unavailable_subtitle), true);
            }
            return;
        }

        tokenWaitRetryCount = 0;
        showNonContentState(getString(R.string.txn_mgmt_state_loading_title),
                getString(R.string.txn_mgmt_state_loading_subtitle), false);

        fetchAdminAccounts(token, new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiService.PosAccountDto>> call,
                    @NonNull Response<List<ApiService.PosAccountDto>> resp) {
                if (resp.isSuccessful() && resp.body() != null) {
                    userIdToName.clear();
                    accountIdToStoreName.clear();
                    for (ApiService.PosAccountDto u : resp.body()) {
                        String displayName = u.fullName != null && !u.fullName.isEmpty() ? u.fullName : u.phone;
                        userIdToName.put(u.id, displayName);
                        String storeName = (u.storeName != null && !u.storeName.trim().isEmpty())
                                ? u.storeName.trim()
                                : getString(R.string.txn_mgmt_filter_unknown_store);
                        accountIdToStoreName.put(u.id, storeName);
                    }
                    syncUsersToLocal(resp.body());
                    loadTransactions(token);
                } else {
                    clearRenderedTransactions();
                    showNonContentState(getString(R.string.txn_mgmt_state_backend_unavailable_title),
                            getString(R.string.txn_mgmt_state_load_users_failed_subtitle), true);
                    Toast.makeText(TransactionManagementActivity.this,
                            extractBackendError(resp, getString(R.string.txn_mgmt_toast_load_users_failed)),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiService.PosAccountDto>> call, @NonNull Throwable t) {
                clearRenderedTransactions();
                showNonContentState(getString(R.string.txn_mgmt_state_backend_unavailable_title),
                        getString(R.string.txn_mgmt_state_load_users_network_subtitle), true);
                Toast.makeText(TransactionManagementActivity.this,
                        getString(R.string.common_error_with_reason, t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchAdminAccounts(String token, Callback<List<ApiService.PosAccountDto>> callback) {
        ApiClient.getService(this).getPosAccounts(token).enqueue(callback);
    }

    private void loadTransactions(String token) {
        ApiClient.getService(this).getAllTransactions(token, filterMerchantId, filterTerminalId).enqueue(
                new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ApiService.TransactionRecordDto>> call,
                            @NonNull Response<List<ApiService.TransactionRecordDto>> resp) {
                        if (resp.isSuccessful() && resp.body() != null) {
                            List<ApiService.TransactionRecordDto> rawTransactions = resp.body();
                            if (rawTransactions.isEmpty() && !autoBackfillAttempted) {
                                autoBackfillAttempted = true;
                                triggerBackfillAndReload(token);
                                return;
                            }
                            userTransactions = new ArrayList<>();
                            for (ApiService.TransactionRecordDto txn : rawTransactions) {
                                Long accountId = resolveTxnAccountId(txn);
                                ApiService.TransactionRecordDto displayTxn = copyTransaction(txn);
                                if (accountId != null && userIdToName.containsKey(accountId)) {
                                    displayTxn.username = userIdToName.get(accountId);
                                } else if (displayTxn.username == null || displayTxn.username.trim().isEmpty()) {
                                    displayTxn.username = getString(R.string.txn_detail_unknown);
                                }
                                displayTxn.ownerUsername = getTxnStoreName(txn);
                                userTransactions.add(displayTxn);
                            }
                            backendTransactionsAvailable = true;
                            syncTransactionsToLocal(rawTransactions);
                            populateUserFilter();
                            populateStoreFilter();
                            showContentChrome();
                            applyCurrentFilter();
                        } else {
                            clearRenderedTransactions();
                            showNonContentState(getString(R.string.txn_mgmt_state_backend_unavailable_title),
                                    getString(R.string.txn_mgmt_state_load_txn_failed_subtitle), true);
                            Toast.makeText(TransactionManagementActivity.this,
                                    extractBackendError(resp, getString(R.string.txn_mgmt_toast_load_txn_failed)),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<ApiService.TransactionRecordDto>> call, @NonNull Throwable t) {
                        clearRenderedTransactions();
                        showNonContentState(getString(R.string.txn_mgmt_state_backend_unavailable_title),
                                getString(R.string.txn_mgmt_state_load_txn_network_subtitle), true);
                        Toast.makeText(TransactionManagementActivity.this,
                                getString(R.string.common_error_with_reason, t.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void syncUsersToLocal(List<ApiService.PosAccountDto> remoteUsers) {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(TransactionManagementActivity.this);
                PosAccountDao posAccountDao = db.posAccountDao();
                String adminHash = getCurrentAdminHash();

                for (ApiService.PosAccountDto remoteUser : remoteUsers) {
                    PosAccountEntity localAccount = remoteUser.id > 0 ? posAccountDao.findByBackendId(remoteUser.id) : null;
                    if (localAccount == null && remoteUser.phone != null) {
                        localAccount = posAccountDao.findByUsername(remoteUser.phone);
                    }
                    if (localAccount == null && remoteUser.email != null) {
                        localAccount = posAccountDao.findByUsername(remoteUser.email);
                    }

                    if (localAccount == null) {
                        localAccount = new PosAccountEntity();
                        localAccount.username = remoteUser.username != null && !remoteUser.username.isEmpty()
                                ? remoteUser.username
                                : remoteUser.phone;
                        localAccount.usernameHash = PasswordUtils.hashSHA256(
                                remoteUser.username != null && !remoteUser.username.isEmpty()
                                        ? remoteUser.username
                                        : (remoteUser.phone != null && !remoteUser.phone.isEmpty()
                                        ? remoteUser.phone
                                        : String.valueOf(remoteUser.id)));
                        localAccount.passwordHash = "";
                        localAccount.createdAt = System.currentTimeMillis();
                    }

                    localAccount.username = remoteUser.username != null && !remoteUser.username.isEmpty()
                            ? remoteUser.username
                            : remoteUser.phone;
                    localAccount.role = remoteUser.role;
                    localAccount.backendId = remoteUser.id;
                    localAccount.terminalId = remoteUser.terminalId;
                    localAccount.adminId = adminHash;

                    if (localAccount.id > 0) {
                        posAccountDao.update(localAccount);
                    } else {
                        posAccountDao.insert(localAccount);
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("TxnMgmt", "Failed to sync users to local: " + e.getMessage());
            }
        }).start();
    }

    private void syncTransactionsToLocal(List<ApiService.TransactionRecordDto> remoteTransactions) {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(TransactionManagementActivity.this);
                PosAccountDao posAccountDao = db.posAccountDao();
                TransactionDao transactionDao = db.transactionDao();

                for (ApiService.TransactionRecordDto txn : remoteTransactions) {
                    Long accountId = resolveTxnAccountId(txn);
                    PosAccountEntity localAccount = accountId != null ? posAccountDao.findByBackendId(accountId) : null;
                    TransactionEntity localTxn = transactionDao.getByTraceNumber(txn.traceNumber);
                    if (localTxn == null) {
                        localTxn = new TransactionEntity();
                        localTxn.traceNumber = txn.traceNumber;
                    }

                    localTxn.amount = txn.amount;
                    localTxn.status = txn.status;
                    localTxn.timestamp = parseBackendTimestamp(txn.txnTimestamp);
                    localTxn.userId = localAccount != null ? localAccount.id : null;
                    localTxn.requestHex = txn.requestHex;
                    localTxn.responseHex = txn.responseHex;
                    localTxn.processingCode = txn.processingCode;
                    localTxn.currencyCode = txn.currencyCode;
                    localTxn.rrn = txn.rrn;
                    localTxn.ownerUsername = txn.username;
                    localTxn.maskedPan = txn.maskedPan;
                    localTxn.cardScheme = txn.cardScheme;
                    localTxn.terminalCode = txn.terminalCode;
                    localTxn.deviceId = txn.deviceId;
                    localTxn.syncedAt = txn.syncedAt;
                    localTxn.backendUserId = accountId;
                    localTxn.backendUsername = txn.username;

                    if (localTxn.id > 0) {
                        transactionDao.update(localTxn);
                    } else {
                        transactionDao.insert(localTxn);
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("TxnMgmt", "Failed to cache transactions: " + e.getMessage());
            }
        }).start();
    }

    private Long resolveTxnAccountId(ApiService.TransactionRecordDto txn) {
        if (txn == null) {
            return null;
        }
        return txn.posAccountId != null ? txn.posAccountId : txn.userId;
    }

    private void populateUserFilter() {
        if (!backendTransactionsAvailable) {
            return;
        }
        String selectedUser = getSelectedUserFilter();
        List<String> names = new ArrayList<>();
        names.add(getString(R.string.txn_mgmt_filter_all_users));
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (ApiService.TransactionRecordDto txn : userTransactions) {
            String name = txn.username != null ? txn.username : getString(R.string.txn_detail_unknown);
            seen.put(name, true);
        }
        names.addAll(seen.keySet());

        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUserFilter.setAdapter(a);

        int selectedIndex = names.indexOf(selectedUser);
        spinnerUserFilter.setSelection(selectedIndex >= 0 ? selectedIndex : 0, false);
    }

    private void populateStoreFilter() {
        if (!backendTransactionsAvailable || spinnerStoreFilter == null) {
            return;
        }
        String selectedStore = getSelectedStoreFilter();
        List<String> stores = new ArrayList<>();
        stores.add(getString(R.string.txn_mgmt_filter_all_stores));
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (ApiService.TransactionRecordDto txn : userTransactions) {
            seen.put(getTxnStoreName(txn), true);
        }
        stores.addAll(seen.keySet());

        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, stores);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStoreFilter.setAdapter(a);

        int selectedIndex = stores.indexOf(selectedStore);
        spinnerStoreFilter.setSelection(selectedIndex >= 0 ? selectedIndex : 0, false);
    }

    private void filterByUser(String selectedUser) {
        filterByUserAndStore(selectedUser, getSelectedStoreFilter());
    }

    private void filterByUserAndStore(String selectedUser, String selectedStore) {
        if (!backendTransactionsAvailable) {
            return;
        }

        List<ApiService.TransactionRecordDto> filtered;
        if (selectedUser == null || getString(R.string.txn_mgmt_filter_all_users).equals(selectedUser)) {
            filtered = userTransactions;
        } else {
            filtered = new ArrayList<>();
            for (ApiService.TransactionRecordDto txn : userTransactions) {
                String name = txn.username != null ? txn.username : getString(R.string.txn_detail_unknown);
                if (name.equals(selectedUser))
                    filtered.add(txn);
            }
        }

        if (selectedStore != null && !getString(R.string.txn_mgmt_filter_all_stores).equals(selectedStore)) {
            List<ApiService.TransactionRecordDto> narrowed = new ArrayList<>();
            for (ApiService.TransactionRecordDto txn : filtered) {
                if (selectedStore.equals(getTxnStoreName(txn))) {
                    narrowed.add(txn);
                }
            }
            filtered = narrowed;
        }

        adapter.setData(filtered);
        updateStats(filtered);
        tvTxnCount.setText(String.valueOf(filtered.size()));
        showContentChrome();
        if (filtered.isEmpty()) {
            if (userTransactions.isEmpty()) {
                showEmptyMessage(getString(R.string.txn_mgmt_empty_no_transactions_title),
                        getString(R.string.txn_mgmt_empty_no_transactions_subtitle), false);
            } else {
                showEmptyMessage(getString(R.string.txn_mgmt_empty_filter_title),
                        getString(R.string.txn_mgmt_empty_filter_subtitle_store), false);
            }
        } else {
            layoutEmpty.setVisibility(View.GONE);
        }
    }

    private String getTxnStoreName(ApiService.TransactionRecordDto txn) {
        Long accountId = resolveTxnAccountId(txn);
        if (accountId != null) {
            String mapped = accountIdToStoreName.get(accountId);
            if (mapped != null && !mapped.trim().isEmpty()) {
                return mapped;
            }
        }
        return getString(R.string.txn_mgmt_filter_unknown_store);
    }

    private void updateStats(List<ApiService.TransactionRecordDto> txns) {
        int approved = 0, declined = 0, other = 0;
        for (ApiService.TransactionRecordDto t : txns) {
            if ("APPROVED".equalsIgnoreCase(t.status))
                approved++;
            else if (t.status != null && t.status.toUpperCase().startsWith("DECLINED"))
                declined++;
            else
                other++;
        }
        tvApprovedCount.setText(String.valueOf(approved));
        tvDeclinedCount.setText(String.valueOf(declined));
        tvOtherCount.setText(String.valueOf(other));
    }

    private ApiService.TransactionRecordDto copyTransaction(ApiService.TransactionRecordDto source) {
        ApiService.TransactionRecordDto copy = new ApiService.TransactionRecordDto();
        copy.id = source.id;
        copy.traceNumber = source.traceNumber;
        copy.amount = source.amount;
        copy.status = source.status;
        copy.maskedPan = source.maskedPan;
        copy.cardScheme = source.cardScheme;
        copy.terminalCode = source.terminalCode;
        copy.deviceId = source.deviceId;
        copy.txnTimestamp = source.txnTimestamp;
        copy.syncedAt = source.syncedAt;
        copy.posAccountId = source.posAccountId;
        copy.userId = source.userId;
        copy.username = source.username;
        copy.requestHex = source.requestHex;
        copy.responseHex = source.responseHex;
        copy.processingCode = source.processingCode;
        copy.currencyCode = source.currencyCode;
        copy.rrn = source.rrn;
        return copy;
    }

    private long parseBackendTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return System.currentTimeMillis();
        }
        try {
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private String getCurrentAdminHash() {
        String adminIdentifier = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME);
        if (adminIdentifier == null) {
            adminIdentifier = "";
        }
        return PasswordUtils.hashSHA256(adminIdentifier.trim());
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private String getSelectedUserFilter() {
        Object selected = spinnerUserFilter.getSelectedItem();
        return selected != null ? selected.toString() : getString(R.string.txn_mgmt_filter_all_users);
    }

    private String getSelectedStoreFilter() {
        if (spinnerStoreFilter == null) {
            return getString(R.string.txn_mgmt_filter_all_stores);
        }
        Object selected = spinnerStoreFilter.getSelectedItem();
        return selected != null ? selected.toString() : getString(R.string.txn_mgmt_filter_all_stores);
    }

    private void applyCurrentFilter() {
        if (!backendTransactionsAvailable) {
            return;
        }
        if (spinnerUserFilter.getAdapter() == null || spinnerUserFilter.getAdapter().getCount() == 0) {
            populateUserFilter();
        }
        if (spinnerStoreFilter != null &&
                (spinnerStoreFilter.getAdapter() == null || spinnerStoreFilter.getAdapter().getCount() == 0)) {
            populateStoreFilter();
        }
        filterByUserAndStore(getSelectedUserFilter(), getSelectedStoreFilter());
    }

    private void showOfflineState() {
        clearRenderedTransactions();
        showNonContentState(getString(R.string.txn_mgmt_state_offline_title),
                getString(R.string.txn_mgmt_state_offline_subtitle), true);
    }

    private void showContentChrome() {
        backendTransactionsAvailable = true;
        if (layoutStats != null) {
            layoutStats.setVisibility(View.VISIBLE);
        }
        if (layoutFilterBar != null) {
            layoutFilterBar.setVisibility(View.VISIBLE);
        }
        if (spinnerUserFilter != null) {
            spinnerUserFilter.setEnabled(true);
        }
        if (spinnerStoreFilter != null) {
            spinnerStoreFilter.setEnabled(true);
        }
        if (rvTransactions != null) {
            rvTransactions.setVisibility(View.VISIBLE);
        }
    }

    private void clearRenderedTransactions() {
        backendTransactionsAvailable = false;
        userTransactions = new ArrayList<>();
        userIdToName.clear();
        accountIdToStoreName.clear();
        adapter.setData(new ArrayList<>());
        tvTxnCount.setText(R.string.common_zero);
        updateStats(new ArrayList<>());
        if (spinnerUserFilter != null) {
            spinnerUserFilter.setAdapter(null);
        }
        if (spinnerStoreFilter != null) {
            spinnerStoreFilter.setAdapter(null);
        }
    }

    private void showNonContentState(String title, String subtitle, boolean showRetry) {
        if (layoutStats != null) {
            layoutStats.setVisibility(View.GONE);
        }
        if (layoutFilterBar != null) {
            layoutFilterBar.setVisibility(View.GONE);
        }
        if (spinnerUserFilter != null) {
            spinnerUserFilter.setEnabled(false);
        }
        if (spinnerStoreFilter != null) {
            spinnerStoreFilter.setEnabled(false);
        }
        if (rvTransactions != null) {
            rvTransactions.setVisibility(View.GONE);
        }
        showEmptyMessage(title, subtitle, showRetry);
    }

    private void showEmptyMessage(String title, String subtitle, boolean showRetry) {
        layoutEmpty.setVisibility(View.VISIBLE);
        if (tvEmptyTitle != null) {
            tvEmptyTitle.setText(title);
        }
        if (tvEmptySubtitle != null) {
            tvEmptySubtitle.setText(subtitle);
        }
        if (btnRetryConnection != null) {
            btnRetryConnection.setVisibility(showRetry ? View.VISIBLE : View.GONE);
        }
    }

    private void triggerBackfillAction() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, R.string.txn_mgmt_backfill_requires_network, Toast.LENGTH_SHORT).show();
            return;
        }

        String token = ApiClient.bearerToken(this);
        if (token.isEmpty() || "Bearer ".equals(token) || !ApiClient.isLoggedIn(this)) {
            Toast.makeText(this, R.string.txn_mgmt_backfill_session_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (btnBackfillTransactions != null) {
            btnBackfillTransactions.setEnabled(false);
        }

        ApiClient.getService(this).backfillAdminTransactions(token, filterMerchantId)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<Map<String, Integer>> call,
                            @NonNull Response<Map<String, Integer>> response) {
                        if (btnBackfillTransactions != null) {
                            btnBackfillTransactions.setEnabled(true);
                        }

                        if (response.isSuccessful() && response.body() != null) {
                            int relinked = response.body().get("relinked") != null
                                    ? response.body().get("relinked")
                                    : 0;
                            int total = response.body().get("totalScoped") != null
                                    ? response.body().get("totalScoped")
                                    : 0;
                            Toast.makeText(TransactionManagementActivity.this,
                                    getString(R.string.txn_mgmt_backfill_success, relinked, total),
                                    Toast.LENGTH_LONG).show();
                            loadUsersAndTransactions();
                        } else {
                            Toast.makeText(TransactionManagementActivity.this,
                                    extractBackendError(response, getString(R.string.txn_mgmt_backfill_failed)),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Map<String, Integer>> call, @NonNull Throwable t) {
                        if (btnBackfillTransactions != null) {
                            btnBackfillTransactions.setEnabled(true);
                        }
                        Toast.makeText(TransactionManagementActivity.this,
                                getString(R.string.common_error_with_reason, t.getMessage()),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void triggerBackfillAndReload(String token) {
        ApiClient.getService(this).backfillAdminTransactions(token, filterMerchantId)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<Map<String, Integer>> call,
                            @NonNull Response<Map<String, Integer>> response) {
                        loadTransactions(token);
                    }

                    @Override
                    public void onFailure(@NonNull Call<Map<String, Integer>> call, @NonNull Throwable t) {
                        loadTransactions(token);
                    }
                });
    }

    private String extractBackendError(Response<?> response, String fallback) {
        if (response == null) {
            return fallback;
        }
        try (okhttp3.ResponseBody body = response.errorBody()) {
            if (body == null) {
                return fallback;
            }
            String raw = body.string();
            if (raw.trim().isEmpty()) {
                return fallback;
            }
            try {
                org.json.JSONObject json = new org.json.JSONObject(raw);
                String error = json.optString("error", "").trim();
                String message = json.optString("message", "").trim();
                android.util.Log.w("TxnMgmt", "Backend error payload: " + (!error.isEmpty() ? error : message));
            } catch (Exception ignored) {
                android.util.Log.w("TxnMgmt", "Backend error payload(raw): " + raw.trim());
            }
            // Keep UI fully localized by showing app-side fallback text.
            return fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void initNetworkCallback() {
        if (networkCallback != null) {
            return;
        }
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull android.net.Network network) {
                runOnUiThread(() -> {
                    if (!backendTransactionsAvailable && isNetworkAvailable()) {
                        loadUsersAndTransactions();
                    }
                });
            }

            @Override
            public void onLost(@NonNull android.net.Network network) {
                runOnUiThread(() -> showOfflineState());
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
        } catch (Exception e) {
            android.util.Log.w("TxnMgmt", "Failed to register network callback: " + e.getMessage());
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
        } catch (Exception e) {
            android.util.Log.w("TxnMgmt", "Failed to unregister network callback: " + e.getMessage());
        }
        networkCallbackRegistered = false;
    }

    // ====== Adapter ======
    static class TxnAdapter extends RecyclerView.Adapter<TxnAdapter.VH> {
        private List<ApiService.TransactionRecordDto> data = new ArrayList<>();

        void setData(List<ApiService.TransactionRecordDto> data) {
            this.data = data != null ? data : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transaction_summary, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(data.get(position));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvTrace, tvAmount, tvStatus, tvCardInfo, tvTerminal, tvTime, tvUsername, tvStore;

            VH(View v) {
                super(v);
                tvTrace = v.findViewById(R.id.tvTraceNumber);
                tvAmount = v.findViewById(R.id.tvAmount);
                tvStatus = v.findViewById(R.id.tvStatus);
                tvCardInfo = v.findViewById(R.id.tvCardInfo);
                tvTerminal = v.findViewById(R.id.tvTerminal);
                tvTime = v.findViewById(R.id.tvTime);
                tvUsername = v.findViewById(R.id.tvUsername);
                tvStore = v.findViewById(R.id.tvStore);
            }

            void bind(ApiService.TransactionRecordDto txn) {
                String trace = txn.traceNumber != null ? txn.traceNumber :
                        itemView.getContext().getString(R.string.txn_mgmt_placeholder_dash);
                tvTrace.setText(itemView.getContext().getString(R.string.txn_mgmt_trace_format, trace));
                tvAmount.setText(formatAmountDisplay(txn.amount, txn.currencyCode));
                tvCardInfo.setText((txn.maskedPan != null ? txn.maskedPan : "") +
                        (txn.cardScheme != null ? " (" + txn.cardScheme + ")" : ""));
                tvTerminal.setText(txn.terminalCode != null ? txn.terminalCode : itemView.getContext().getString(R.string.txn_mgmt_placeholder_dash));
                tvTime.setText(formatTimestamp(txn.txnTimestamp));

                if (tvUsername != null) {
                    tvUsername.setText(txn.username != null ? txn.username : itemView.getContext().getString(R.string.txn_detail_unknown));
                }
                if (tvStore != null) {
                    tvStore.setText(txn.ownerUsername != null && !txn.ownerUsername.trim().isEmpty()
                            ? txn.ownerUsername
                            : itemView.getContext().getString(R.string.txn_mgmt_filter_unknown_store));
                }

                // Status pill with localized text while keeping backend code matching.
                String rawStatus = txn.status != null ? txn.status : itemView.getContext().getString(R.string.txn_mgmt_status_unknown);
                String upper = rawStatus.toUpperCase(java.util.Locale.ROOT);
                String displayStatus = rawStatus;
                if ("APPROVED".equals(upper) || "SUCCESS".equals(upper)) {
                    displayStatus = itemView.getContext().getString(R.string.status_approved);
                } else if (upper.startsWith("DECLINED")) {
                    String suffix = rawStatus.length() > 8 ? rawStatus.substring(8).trim() : "";
                    displayStatus = suffix.isEmpty()
                            ? itemView.getContext().getString(R.string.status_declined)
                            : itemView.getContext().getString(R.string.status_declined) + " " + suffix;
                } else if ("PENDING".equals(upper)) {
                    displayStatus = itemView.getContext().getString(R.string.status_pending);
                } else if ("REVERSED".equals(upper)) {
                    displayStatus = itemView.getContext().getString(R.string.status_reversed);
                }
                tvStatus.setText(displayStatus);
                if ("APPROVED".equals(upper) || "SUCCESS".equals(upper)) {
                    tvStatus.setBackgroundResource(R.drawable.bg_status_approved);
                    tvStatus.setTextColor(0xFFFFFFFF);
                } else if (upper.startsWith("DECLINED")) {
                    tvStatus.setBackgroundResource(R.drawable.bg_status_declined);
                    tvStatus.setTextColor(0xFFFFFFFF);
                } else {
                    tvStatus.setBackgroundResource(R.drawable.bg_status_other);
                    tvStatus.setTextColor(0xFFFFFFFF);
                }
            }

            private String formatTimestamp(String raw) {
                if (raw == null || raw.trim().isEmpty()) {
                    return itemView.getContext().getString(R.string.txn_mgmt_placeholder_dash);
                }
                String out = DateTimeFormatUtils.formatBackendTimestamp(raw);
                return (out == null || out.trim().isEmpty())
                        ? itemView.getContext().getString(R.string.txn_mgmt_placeholder_dash)
                        : out;
            }

            private String formatAmountDisplay(String rawAmount, String currencyCode) {
                String out = AmountFormatUtils.formatAmountDisplay(rawAmount, currencyCode);
                if ("-".equals(out)) {
                    return itemView.getContext().getString(R.string.txn_mgmt_placeholder_dash);
                }
                return out;
            }
        }
    }
}
