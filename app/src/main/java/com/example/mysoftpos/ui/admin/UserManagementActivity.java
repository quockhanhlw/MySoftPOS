package com.example.mysoftpos.ui.admin;

import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.dao.UserDao;
import com.example.mysoftpos.data.local.entity.UserEntity;
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;
import com.example.mysoftpos.ui.BaseActivity;
import com.example.mysoftpos.utils.security.PasswordUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Admin: User Management — CRUD via Backend API.
 * Each user has a unique TID (Terminal ID).
 */
public class UserManagementActivity extends BaseActivity implements UserAdapter.OnUserListener {

    private static final int MAX_TOKEN_WAIT_RETRIES = 15;
    private static final long TOKEN_WAIT_RETRY_DELAY_MS = 1200L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable retryLoadRunnable = this::loadUsers;

    private UserAdapter adapter;
    private TextView tvUserCount;
    private View layoutEmpty;
    private EditText etSearch;
    private List<ApiService.UserDto> allUsers = new ArrayList<>();
    private int tokenWaitRetryCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_management);

        tvUserCount = findViewById(R.id.tvUserCount);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        etSearch = findViewById(R.id.etSearch);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvUsers);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserAdapter(this);
        rv.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> showAddEditDialog(null));

        // Search
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                filterUsers(s.toString().trim());
            }
        });

        androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipeRefresh);
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> {
                loadUsers();
                swipeRefresh.setRefreshing(false);
                Toast.makeText(this, "Refreshed list", Toast.LENGTH_SHORT).show();
            });
        }

        loadUsers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUsers();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(retryLoadRunnable);
    }

    // ====== Load from API ======
    private void loadUsers() {
        loadCachedUsers();

        String token = ApiClient.bearerToken(this);
        android.util.Log.d("UserMgmt", "loadUsers: token=" +
                (token.length() > 15 ? token.substring(0, 15) + "..." : token));

        if (token.isEmpty() || "Bearer ".equals(token) || !ApiClient.isLoggedIn(this)) {
            if (!allUsers.isEmpty()) {
                showEmptyStateText("Showing cached users",
                        isNetworkAvailable()
                                ? "Syncing backend session… pull to refresh in a moment"
                                : "Offline mode: cached users only");
            } else if (isNetworkAvailable() && tokenWaitRetryCount < MAX_TOKEN_WAIT_RETRIES) {
                tokenWaitRetryCount++;
                showEmptyStateText("Preparing backend session",
                        "Please wait while your admin session is restored");
                mainHandler.removeCallbacks(retryLoadRunnable);
                mainHandler.postDelayed(retryLoadRunnable, TOKEN_WAIT_RETRY_DELAY_MS);
            } else {
                showEmptyStateText("Backend Offline",
                        isNetworkAvailable()
                                ? "Unable to restore backend session. Pull to retry."
                                : "Connect to the network to manage users");
            }
            filterUsers(etSearch.getText().toString().trim());
            return;
        }

        tokenWaitRetryCount = 0;
        showEmptyStateText("No users yet", "Tap + to create a new user");

        ApiClient.getService(this).getUsers(token).enqueue(new Callback<List<ApiService.UserDto>>() {
            @Override
            public void onResponse(Call<List<ApiService.UserDto>> call, Response<List<ApiService.UserDto>> resp) {
                android.util.Log.d("UserMgmt", "getUsers response: code=" + resp.code()
                        + " body=" + (resp.body() != null ? resp.body().size() + " users" : "null"));
                if (resp.isSuccessful() && resp.body() != null) {
                    allUsers = resp.body();
                    syncUsersToLocal(resp.body());
                    filterUsers(etSearch.getText().toString().trim());
                } else {
                    String errMsg = "Failed to load users (HTTP " + resp.code() + ")";
                    try (okhttp3.ResponseBody errorBody = resp.errorBody()) {
                        if (errorBody != null) {
                            errMsg += ": " + errorBody.string();
                        }
                    } catch (Exception ignored) {
                    }
                    android.util.Log.w("UserMgmt", errMsg);
                    Toast.makeText(UserManagementActivity.this, errMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<List<ApiService.UserDto>> call, Throwable t) {
                android.util.Log.e("UserMgmt", "getUsers failed: " + t.getMessage());
                if (allUsers.isEmpty()) {
                    showEmptyStateText("Network error",
                            "Could not load users from backend. Cached users are unavailable.");
                    filterUsers(etSearch.getText().toString().trim());
                }
                Toast.makeText(UserManagementActivity.this,
                        "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterUsers(String query) {
        if (query.isEmpty()) {
            adapter.setUsers(allUsers);
        } else {
            List<ApiService.UserDto> filtered = new ArrayList<>();
            String q = query.toLowerCase();
            for (ApiService.UserDto u : allUsers) {
                if ((u.fullName != null && u.fullName.toLowerCase().contains(q)) ||
                        (u.phone != null && u.phone.toLowerCase().contains(q)) ||
                        (u.terminalId != null && u.terminalId.toLowerCase().contains(q))) {
                    filtered.add(u);
                }
            }
            adapter.setUsers(filtered);
        }
        int count = adapter.getItemCount();
        tvUserCount.setText(count + " user(s)");
        layoutEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onUserClick(ApiService.UserDto user) {
        showAddEditDialog(user);
    }

    @Override
    public void onUserLongClick(ApiService.UserDto user) {
        confirmDelete(user);
    }

    // ====== Add / Edit Dialog ======
    private void showAddEditDialog(ApiService.UserDto existing) {
        boolean isEdit = existing != null;
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_edit_user, null);

        EditText etName = dialogView.findViewById(R.id.etName);
        EditText etPhone = dialogView.findViewById(R.id.etPhone);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        EditText etTerminalId = dialogView.findViewById(R.id.etTerminalId);
        EditText etServerIp = dialogView.findViewById(R.id.etServerIp);
        EditText etServerPort = dialogView.findViewById(R.id.etServerPort);

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvDialogSubtitle);

        if (isEdit) {
            if (tvTitle != null)
                tvTitle.setText("Edit User");
            if (tvSubtitle != null)
                tvSubtitle.setText("Update user details");
            etName.setText(existing.fullName);
            etPhone.setText(existing.phone);
            etEmail.setText(existing.email);
            if (etTerminalId != null)
                etTerminalId.setText(existing.terminalId);
            if (etServerIp != null && existing.serverIp != null)
                etServerIp.setText(existing.serverIp);
            if (etServerPort != null && existing.serverPort != null)
                etServerPort.setText(String.valueOf(existing.serverPort));
            etPassword.setHint("New password (leave blank to keep)");
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        // Test Connection button
        View btnTestConnection = dialogView.findViewById(R.id.btnTestConnection);
        TextView tvStatus = dialogView.findViewById(R.id.tvConnectionStatus);
        if (btnTestConnection != null) {
            btnTestConnection.setVisibility(View.VISIBLE);
            btnTestConnection.setOnClickListener(v -> {
                String ip = etServerIp != null ? etServerIp.getText().toString().trim() : "";
                String portStr = etServerPort != null ? etServerPort.getText().toString().trim() : "";
                if (ip.isEmpty() || portStr.isEmpty()) {
                    if (tvStatus != null) {
                        tvStatus.setVisibility(View.VISIBLE);
                        tvStatus.setText("Enter IP and Port first");
                        tvStatus.setTextColor(0xFFEF4444);
                    }
                    return;
                }
                int port;
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    if (tvStatus != null) {
                        tvStatus.setVisibility(View.VISIBLE);
                        tvStatus.setText("Invalid port number");
                        tvStatus.setTextColor(0xFFEF4444);
                    }
                    return;
                }
                if (tvStatus != null) {
                    tvStatus.setVisibility(View.VISIBLE);
                    tvStatus.setText("Testing...");
                    tvStatus.setTextColor(0xFF64748B);
                }
                final int finalPort = port;
                new Thread(() -> {
                    boolean ok = false;
                    try {
                        java.net.Socket socket = new java.net.Socket();
                        socket.connect(new java.net.InetSocketAddress(ip, finalPort), 5000);
                        socket.close();
                        ok = true;
                    } catch (Exception ignored) {
                    }
                    final boolean result = ok;
                    runOnUiThread(() -> {
                        if (tvStatus != null) {
                            tvStatus.setVisibility(View.VISIBLE);
                            if (result) {
                                tvStatus.setText("✓ Connected");
                                tvStatus.setTextColor(0xFF16A34A);
                            } else {
                                tvStatus.setText("✗ Connection failed");
                                tvStatus.setTextColor(0xFFEF4444);
                            }
                        }
                    });
                }).start();
            });
        }

        // Save button
        View btnSave = dialogView.findViewById(R.id.btnDialogSave);
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                String fullName = etName.getText().toString().trim();
                String phone = etPhone.getText().toString().trim();
                String email = etEmail.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                String terminalId = etTerminalId != null ? etTerminalId.getText().toString().trim() : "";
                String serverIp = etServerIp != null ? etServerIp.getText().toString().trim() : "";
                String serverPortStr = etServerPort != null ? etServerPort.getText().toString().trim() : "";
                int serverPort = 0;
                try {
                    serverPort = Integer.parseInt(serverPortStr);
                } catch (NumberFormatException ignored) {
                }

                if (!isEdit && phone.isEmpty()) {
                    Toast.makeText(this, "Phone number is required", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isEdit && password.isEmpty()) {
                    Toast.makeText(this, "Password is required", Toast.LENGTH_SHORT).show();
                    return;
                }

                String token = ApiClient.bearerToken(this);
                final String fServerIp = serverIp;
                final int fServerPort = serverPort;
                final String fTerminalId = terminalId;

                if (isEdit) {
                    ApiService.CreateUserRequest req = new ApiService.CreateUserRequest(
                            password.isEmpty() ? null : password,
                            fullName, phone, email, terminalId, serverIp, serverPort > 0 ? serverPort : null);
                    ApiClient.getService(this).updateUser(token, existing.id, req)
                            .enqueue(new SimpleCallbackWithLocalSync("User updated", existing.id, fServerIp,
                                    fServerPort, fTerminalId));
                } else {
                    ApiService.CreateUserRequest req = new ApiService.CreateUserRequest(
                            password, fullName, phone, email, terminalId, serverIp, serverPort > 0 ? serverPort : null);
                    ApiClient.getService(this).createUser(token, req)
                            .enqueue(new SimpleCallbackWithLocalSync("User created", -1, fServerIp, fServerPort,
                                    fTerminalId));
                }
                dialog.dismiss();
            });
        }

        // Cancel button
        View btnCancel = dialogView.findViewById(R.id.btnDialogCancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        // Delete button (edit mode only)
        View btnDelete = dialogView.findViewById(R.id.btnDelete);
        if (btnDelete != null) {
            if (isEdit) {
                btnDelete.setVisibility(View.VISIBLE);
                btnDelete.setOnClickListener(v -> {
                    dialog.dismiss();
                    confirmDelete(existing);
                });
            } else {
                btnDelete.setVisibility(View.GONE);
            }
        }

        dialog.show();
    }

    // ====== Delete ======
    private void confirmDelete(ApiService.UserDto user) {
        new AlertDialog.Builder(this)
                .setTitle("Delete User")
                .setMessage("Delete " + (user.fullName != null ? user.fullName : user.phone) + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    String token = ApiClient.bearerToken(this);
                    ApiClient.getService(this).deleteUser(token, user.id)
                            .enqueue(new Callback<Map<String, String>>() {
                                @Override
                                public void onResponse(Call<Map<String, String>> c, Response<Map<String, String>> r) {
                                    Toast.makeText(UserManagementActivity.this, "User deleted", Toast.LENGTH_SHORT)
                                            .show();
                                    loadUsers();
                                }

                                @Override
                                public void onFailure(Call<Map<String, String>> c, Throwable t) {
                                    Toast.makeText(UserManagementActivity.this, "Error: " + t.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Helper callback that shows a toast, reloads, and saves server config locally
    private class SimpleCallbackWithLocalSync implements Callback<ApiService.UserDto> {
        private final String successMsg;
        private final long existingBackendId;
        private final String serverIp;
        private final int serverPort;
        private final String terminalId;

        SimpleCallbackWithLocalSync(String msg, long existingBackendId, String serverIp, int serverPort,
                String terminalId) {
            this.successMsg = msg;
            this.existingBackendId = existingBackendId;
            this.serverIp = serverIp;
            this.serverPort = serverPort;
            this.terminalId = terminalId;
        }

        @Override
        public void onResponse(Call<ApiService.UserDto> call, Response<ApiService.UserDto> resp) {
            if (resp.isSuccessful() && resp.body() != null) {
                Toast.makeText(UserManagementActivity.this, successMsg, Toast.LENGTH_SHORT).show();

                ApiService.UserDto savedUser = resp.body();
                new Thread(() -> {
                    try {
                        AppDatabase db = AppDatabase.getInstance(UserManagementActivity.this);
                        UserDao userDao = db.userDao();

                        String phoneHash = PasswordUtils.hashSHA256(savedUser.phone);
                        UserEntity localUser = userDao.findByUsernameHash(phoneHash);

                        if (localUser != null) {
                            localUser.serverIp = serverIp;
                            localUser.serverPort = serverPort;
                            localUser.terminalId = terminalId;
                            localUser.backendId = savedUser.id;
                            localUser.adminId = getCurrentAdminHash();
                            userDao.update(localUser);
                        } else {
                            localUser = new UserEntity();
                            localUser.usernameHash = phoneHash;
                            localUser.passwordHash = "";
                            localUser.displayName = savedUser.fullName;
                            localUser.role = savedUser.role;
                            localUser.email = savedUser.email;
                            localUser.phone = savedUser.phone;
                            localUser.backendId = savedUser.id;
                            localUser.terminalId = terminalId;
                            localUser.serverIp = serverIp;
                            localUser.serverPort = serverPort;
                            localUser.adminId = getCurrentAdminHash();
                            localUser.createdAt = System.currentTimeMillis();
                            userDao.insert(localUser);
                        }
                    } catch (Exception e) {
                        android.util.Log.w("UserMgmt", "Failed to sync local: " + e.getMessage());
                    }
                }).start();

                loadUsers();
            } else {
                Toast.makeText(UserManagementActivity.this, "Error: " + resp.code(), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onFailure(Call<ApiService.UserDto> call, Throwable t) {
            Toast.makeText(UserManagementActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadCachedUsers() {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(UserManagementActivity.this);
                UserDao userDao = db.userDao();
                List<UserEntity> cached = userDao.getAllByAdminIdSync(getCurrentAdminHash());
                List<ApiService.UserDto> mapped = new ArrayList<>();
                for (UserEntity entity : cached) {
                    if (entity.backendId <= 0) {
                        continue;
                    }
                    mapped.add(toUserDto(entity));
                }
                runOnUiThread(() -> {
                    allUsers = mapped;
                    filterUsers(etSearch.getText().toString().trim());
                });
            } catch (Exception e) {
                android.util.Log.w("UserMgmt", "Failed to load cached users: " + e.getMessage());
            }
        }).start();
    }

    private void syncUsersToLocal(List<ApiService.UserDto> remoteUsers) {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(UserManagementActivity.this);
                UserDao userDao = db.userDao();
                String adminHash = getCurrentAdminHash();

                for (ApiService.UserDto remoteUser : remoteUsers) {
                    UserEntity localUser = userDao.findByBackendId(remoteUser.id);
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
                android.util.Log.w("UserMgmt", "Failed to cache remote users: " + e.getMessage());
            }
        }).start();
    }

    private ApiService.UserDto toUserDto(UserEntity entity) {
        ApiService.UserDto dto = new ApiService.UserDto();
        dto.id = entity.backendId;
        dto.role = entity.role;
        dto.fullName = entity.displayName;
        dto.phone = entity.phone;
        dto.email = entity.email;
        dto.terminalId = entity.terminalId;
        dto.serverIp = entity.serverIp;
        dto.serverPort = entity.serverPort > 0 ? entity.serverPort : null;
        dto.active = true;
        dto.online = false;
        return dto;
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

    private void showEmptyStateText(String titleText, String subtitleText) {
        layoutEmpty.setVisibility(View.VISIBLE);
        if (layoutEmpty instanceof android.widget.LinearLayout) {
            View title = ((android.widget.LinearLayout) layoutEmpty).getChildAt(1);
            View subtitle = ((android.widget.LinearLayout) layoutEmpty).getChildAt(2);
            if (title instanceof TextView) {
                ((TextView) title).setText(titleText);
            }
            if (subtitle instanceof TextView) {
                ((TextView) subtitle).setText(subtitleText);
            }
        }
    }
}
