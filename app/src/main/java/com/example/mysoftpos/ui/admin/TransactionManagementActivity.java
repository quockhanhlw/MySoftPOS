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
import com.example.mysoftpos.data.local.dao.UserDao;
import com.example.mysoftpos.data.local.entity.TransactionEntity;
import com.example.mysoftpos.data.local.entity.UserEntity;
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;
import com.example.mysoftpos.ui.BaseActivity;
import com.example.mysoftpos.utils.security.PasswordUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final String FILTER_ALL_USERS = "All Users";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable retryLoadRunnable = this::loadUsersAndTransactions;

    private TxnAdapter adapter;
    private TextView tvTxnCount, tvApprovedCount, tvDeclinedCount, tvOtherCount;
    private Spinner spinnerUserFilter;
    private View layoutEmpty;

    private List<ApiService.TransactionSummaryDto> userTransactions = new ArrayList<>();
    private Map<Long, String> userIdToName = new LinkedHashMap<>();
    private int tokenWaitRetryCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_management);

        tvTxnCount = findViewById(R.id.tvTxnCount);
        tvApprovedCount = findViewById(R.id.tvApprovedCount);
        tvDeclinedCount = findViewById(R.id.tvDeclinedCount);
        tvOtherCount = findViewById(R.id.tvOtherCount);
        spinnerUserFilter = findViewById(R.id.spinnerUserFilter);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvTransactions);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TxnAdapter();
        rv.setAdapter(adapter);

        spinnerUserFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                filterByUser((String) parent.getItemAtPosition(pos));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

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

    private void loadUsersAndTransactions() {
        loadCachedTransactions();

        String token = ApiClient.bearerToken(this);
        if (token.isEmpty() || "Bearer ".equals(token) || !ApiClient.isLoggedIn(this)) {
            if (!userTransactions.isEmpty()) {
                showEmptyMessage(isNetworkAvailable()
                        ? "Showing cached transactions while backend session is restoring"
                        : "Offline mode: showing cached transactions only");
            } else if (isNetworkAvailable() && tokenWaitRetryCount < MAX_TOKEN_WAIT_RETRIES) {
                tokenWaitRetryCount++;
                showEmptyMessage("Preparing backend session…");
                mainHandler.removeCallbacks(retryLoadRunnable);
                mainHandler.postDelayed(retryLoadRunnable, TOKEN_WAIT_RETRY_DELAY_MS);
            } else {
                showEmptyMessage(isNetworkAvailable()
                        ? "Unable to restore backend session. Please try again."
                        : "Connect to the network to load transactions.");
            }
            applyCurrentFilter();
            return;
        }

        tokenWaitRetryCount = 0;
        ApiClient.getService(this).getUsers(token).enqueue(
                new Callback<>() {
                    @Override
                    public void onResponse(Call<List<ApiService.UserDto>> call,
                            Response<List<ApiService.UserDto>> resp) {
                        if (resp.isSuccessful() && resp.body() != null) {
                            userIdToName.clear();
                            for (ApiService.UserDto u : resp.body()) {
                                String displayName = u.fullName != null && !u.fullName.isEmpty() ? u.fullName : u.phone;
                                userIdToName.put(u.id, displayName);
                            }
                            syncUsersToLocal(resp.body());
                            loadTransactions(token);
                        } else {
                            Toast.makeText(TransactionManagementActivity.this,
                                    "Failed to load users", Toast.LENGTH_SHORT).show();
                            applyCurrentFilter();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<ApiService.UserDto>> call, Throwable t) {
                        Toast.makeText(TransactionManagementActivity.this,
                                "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        applyCurrentFilter();
                    }
                });
    }

    private void loadTransactions(String token) {
        ApiClient.getService(this).getAllTransactions(token).enqueue(
                new Callback<>() {
                    @Override
                    public void onResponse(Call<List<ApiService.TransactionSummaryDto>> call,
                            Response<List<ApiService.TransactionSummaryDto>> resp) {
                        if (resp.isSuccessful() && resp.body() != null) {
                            List<ApiService.TransactionSummaryDto> rawTransactions = resp.body();
                            userTransactions = new ArrayList<>();
                            for (ApiService.TransactionSummaryDto txn : rawTransactions) {
                                if (txn.userId != null && userIdToName.containsKey(txn.userId)) {
                                    ApiService.TransactionSummaryDto displayTxn = copyTransaction(txn);
                                    displayTxn.username = userIdToName.get(txn.userId);
                                    userTransactions.add(displayTxn);
                                }
                            }
                            syncTransactionsToLocal(rawTransactions);
                            populateUserFilter();
                            applyCurrentFilter();
                        } else {
                            Toast.makeText(TransactionManagementActivity.this,
                                    "Failed to load transactions", Toast.LENGTH_SHORT).show();
                            applyCurrentFilter();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<ApiService.TransactionSummaryDto>> call, Throwable t) {
                        Toast.makeText(TransactionManagementActivity.this,
                                "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        applyCurrentFilter();
                    }
                });
    }

    private void loadCachedTransactions() {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(TransactionManagementActivity.this);
                UserDao userDao = db.userDao();
                TransactionDao transactionDao = db.transactionDao();

                List<UserEntity> managedUsers = userDao.getAllByAdminIdSync(getCurrentAdminHash());
                Map<Long, String> localUserNamesById = new LinkedHashMap<>();
                Set<String> managedUserIdentifiers = new HashSet<>();
                for (UserEntity user : managedUsers) {
                    String displayName = user.displayName != null && !user.displayName.isEmpty()
                            ? user.displayName
                            : (user.phone != null && !user.phone.isEmpty() ? user.phone : user.email);
                    localUserNamesById.put(user.id, displayName != null ? displayName : "Unknown");
                    if (user.phone != null && !user.phone.isEmpty()) {
                        managedUserIdentifiers.add(user.phone);
                    }
                    if (user.email != null && !user.email.isEmpty()) {
                        managedUserIdentifiers.add(user.email);
                    }
                }

                List<ApiService.TransactionSummaryDto> cachedTransactions = new ArrayList<>();
                for (TransactionEntity txn : transactionDao.getAllTransactions()) {
                    if (!belongsToManagedUser(txn, localUserNamesById.keySet(), managedUserIdentifiers)) {
                        continue;
                    }
                    ApiService.TransactionSummaryDto dto = new ApiService.TransactionSummaryDto();
                    dto.traceNumber = txn.traceNumber;
                    dto.amount = txn.amount;
                    dto.status = txn.status;
                    dto.terminalCode = txn.terminalId != null ? String.valueOf(txn.terminalId) : null;
                    dto.txnTimestamp = formatTimestamp(txn.timestamp);
                    dto.userId = txn.userId;
                    dto.username = resolveLocalUsername(txn, localUserNamesById);
                    cachedTransactions.add(dto);
                }

                runOnUiThread(() -> {
                    userTransactions = cachedTransactions;
                    populateUserFilter();
                    applyCurrentFilter();
                });
            } catch (Exception e) {
                android.util.Log.w("TxnMgmt", "Failed to load cached transactions: " + e.getMessage());
            }
        }).start();
    }

    private void syncUsersToLocal(List<ApiService.UserDto> remoteUsers) {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(TransactionManagementActivity.this);
                UserDao userDao = db.userDao();
                String adminHash = getCurrentAdminHash();

                for (ApiService.UserDto remoteUser : remoteUsers) {
                    UserEntity localUser = remoteUser.id > 0 ? userDao.findByBackendId(remoteUser.id) : null;
                    if (localUser == null && remoteUser.phone != null) {
                        localUser = userDao.findByPhone(remoteUser.phone);
                    }
                    if (localUser == null && remoteUser.email != null) {
                        localUser = userDao.findByEmail(remoteUser.email);
                    }

                    if (localUser == null) {
                        localUser = new UserEntity();
                        localUser.usernameHash = PasswordUtils.hashSHA256(
                                remoteUser.phone != null && !remoteUser.phone.isEmpty()
                                        ? remoteUser.phone
                                        : String.valueOf(remoteUser.id));
                        localUser.passwordHash = "";
                        localUser.createdAt = System.currentTimeMillis();
                    }

                    localUser.displayName = remoteUser.fullName;
                    localUser.role = remoteUser.role;
                    localUser.email = remoteUser.email;
                    localUser.phone = remoteUser.phone;
                    localUser.backendId = remoteUser.id;
                    localUser.terminalId = remoteUser.terminalId;
                    localUser.serverIp = remoteUser.serverIp;
                    localUser.serverPort = remoteUser.serverPort != null ? remoteUser.serverPort : 0;
                    localUser.adminId = adminHash;

                    if (localUser.id > 0) {
                        userDao.update(localUser);
                    } else {
                        userDao.insert(localUser);
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("TxnMgmt", "Failed to sync users to local: " + e.getMessage());
            }
        }).start();
    }

    private void syncTransactionsToLocal(List<ApiService.TransactionSummaryDto> remoteTransactions) {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(TransactionManagementActivity.this);
                UserDao userDao = db.userDao();
                TransactionDao transactionDao = db.transactionDao();

                for (ApiService.TransactionSummaryDto txn : remoteTransactions) {
                    if (txn.userId == null || !userIdToName.containsKey(txn.userId)) {
                        continue;
                    }

                    UserEntity localUser = userDao.findByBackendId(txn.userId);
                    TransactionEntity localTxn = transactionDao.getByTraceNumber(txn.traceNumber);
                    if (localTxn == null) {
                        localTxn = new TransactionEntity();
                        localTxn.traceNumber = txn.traceNumber;
                    }

                    localTxn.amount = txn.amount;
                    localTxn.status = txn.status;
                    localTxn.timestamp = parseBackendTimestamp(txn.txnTimestamp);
                    localTxn.userId = localUser != null ? localUser.id : null;
                    localTxn.ownerUsername = txn.username;

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

    private void populateUserFilter() {
        String selectedUser = getSelectedUserFilter();
        List<String> names = new ArrayList<>();
        names.add(FILTER_ALL_USERS);
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (ApiService.TransactionSummaryDto txn : userTransactions) {
            String name = txn.username != null ? txn.username : "Unknown";
            seen.put(name, true);
        }
        names.addAll(seen.keySet());

        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUserFilter.setAdapter(a);

        int selectedIndex = names.indexOf(selectedUser);
        spinnerUserFilter.setSelection(selectedIndex >= 0 ? selectedIndex : 0, false);
    }

    private void filterByUser(String selectedUser) {
        List<ApiService.TransactionSummaryDto> filtered;
        if (selectedUser == null || FILTER_ALL_USERS.equals(selectedUser)) {
            filtered = userTransactions;
        } else {
            filtered = new ArrayList<>();
            for (ApiService.TransactionSummaryDto txn : userTransactions) {
                String name = txn.username != null ? txn.username : "Unknown";
                if (name.equals(selectedUser))
                    filtered.add(txn);
            }
        }
        adapter.setData(filtered);
        updateStats(filtered);
        tvTxnCount.setText(String.valueOf(filtered.size()));
        layoutEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        if (filtered.isEmpty()) {
            showEmptyMessage("No transactions found");
        }
    }

    private void updateStats(List<ApiService.TransactionSummaryDto> txns) {
        int approved = 0, declined = 0, other = 0;
        for (ApiService.TransactionSummaryDto t : txns) {
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

    private boolean belongsToManagedUser(TransactionEntity txn, Set<Long> managedUserIds, Set<String> managedUserIdentifiers) {
        if (txn.userId != null && managedUserIds.contains(txn.userId)) {
            return true;
        }
        return txn.ownerUsername != null && managedUserIdentifiers.contains(txn.ownerUsername);
    }

    private String resolveLocalUsername(TransactionEntity txn, Map<Long, String> localUserNamesById) {
        if (txn.userId != null && localUserNamesById.containsKey(txn.userId)) {
            return localUserNamesById.get(txn.userId);
        }
        return txn.ownerUsername != null ? txn.ownerUsername : "Unknown";
    }

    private ApiService.TransactionSummaryDto copyTransaction(ApiService.TransactionSummaryDto source) {
        ApiService.TransactionSummaryDto copy = new ApiService.TransactionSummaryDto();
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
        copy.userId = source.userId;
        copy.username = source.username;
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

    private String formatTimestamp(long timestamp) {
        try {
            return java.time.Instant.ofEpochMilli(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return "—";
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
        return selected != null ? selected.toString() : FILTER_ALL_USERS;
    }

    private void applyCurrentFilter() {
        if (spinnerUserFilter.getAdapter() == null || spinnerUserFilter.getAdapter().getCount() == 0) {
            populateUserFilter();
        }
        filterByUser(getSelectedUserFilter());
    }

    private void showEmptyMessage(String message) {
        layoutEmpty.setVisibility(View.VISIBLE);
        if (layoutEmpty instanceof android.widget.LinearLayout) {
            View text = ((android.widget.LinearLayout) layoutEmpty).getChildAt(1);
            if (text instanceof TextView) {
                ((TextView) text).setText(message);
            }
        }
    }

    // ====== Adapter ======
    static class TxnAdapter extends RecyclerView.Adapter<TxnAdapter.VH> {
        private List<ApiService.TransactionSummaryDto> data = new ArrayList<>();

        void setData(List<ApiService.TransactionSummaryDto> data) {
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
            final TextView tvTrace, tvAmount, tvStatus, tvCardInfo, tvTerminal, tvTime, tvUsername;

            VH(View v) {
                super(v);
                tvTrace = v.findViewById(R.id.tvTraceNumber);
                tvAmount = v.findViewById(R.id.tvAmount);
                tvStatus = v.findViewById(R.id.tvStatus);
                tvCardInfo = v.findViewById(R.id.tvCardInfo);
                tvTerminal = v.findViewById(R.id.tvTerminal);
                tvTime = v.findViewById(R.id.tvTime);
                tvUsername = v.findViewById(R.id.tvUsername);
            }

            void bind(ApiService.TransactionSummaryDto txn) {
                tvTrace.setText("#" + (txn.traceNumber != null ? txn.traceNumber : "—"));
                tvAmount.setText(txn.amount != null ? txn.amount : "—");
                tvCardInfo.setText((txn.maskedPan != null ? txn.maskedPan : "") +
                        (txn.cardScheme != null ? " (" + txn.cardScheme + ")" : ""));
                tvTerminal.setText(txn.terminalCode != null ? txn.terminalCode : "—");
                tvTime.setText(txn.txnTimestamp != null ? txn.txnTimestamp : "—");

                if (tvUsername != null) {
                    tvUsername.setText(txn.username != null ? txn.username : "Unknown");
                }

                // Status pill with colored background
                String status = txn.status != null ? txn.status : "UNKNOWN";
                tvStatus.setText(status);
                if ("APPROVED".equalsIgnoreCase(status)) {
                    tvStatus.setBackgroundResource(R.drawable.bg_status_approved);
                    tvStatus.setTextColor(0xFFFFFFFF);
                } else if (status.toUpperCase().startsWith("DECLINED")) {
                    tvStatus.setBackgroundResource(R.drawable.bg_status_declined);
                    tvStatus.setTextColor(0xFFFFFFFF);
                } else {
                    tvStatus.setBackgroundResource(R.drawable.bg_status_other);
                    tvStatus.setTextColor(0xFFFFFFFF);
                }
            }
        }
    }
}
