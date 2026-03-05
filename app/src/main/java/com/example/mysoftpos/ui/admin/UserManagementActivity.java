package com.example.mysoftpos.ui.admin;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;
import com.example.mysoftpos.ui.BaseActivity;
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

    private UserAdapter adapter;
    private TextView tvUserCount;
    private View layoutEmpty;
    private EditText etSearch;
    private List<ApiService.UserDto> allUsers = new ArrayList<>();

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

        loadUsers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUsers();
    }

    // ====== Load from API ======
    private void loadUsers() {
        String token = ApiClient.bearerToken(this);
        // If no token (offline login), show clear message
        if (token.isEmpty() || "Bearer ".equals(token) || !ApiClient.isLoggedIn(this)) {
            Toast.makeText(this,
                    "Not authenticated with backend server. Please log out and log in again with backend connected.",
                    Toast.LENGTH_LONG).show();
            allUsers.clear();
            filterUsers("");
            return;
        }
        ApiClient.getService(this).getUsers(token).enqueue(new Callback<List<ApiService.UserDto>>() {
            @Override
            public void onResponse(Call<List<ApiService.UserDto>> call, Response<List<ApiService.UserDto>> resp) {
                if (resp.isSuccessful() && resp.body() != null) {
                    allUsers = resp.body();
                    filterUsers(etSearch.getText().toString().trim());
                } else {
                    Toast.makeText(UserManagementActivity.this, "Failed to load users", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<ApiService.UserDto>> call, Throwable t) {
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
                            .enqueue(new SimpleCallbackWithLocalSync("User created", -1, fServerIp, fServerPort, fTerminalId));
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

        SimpleCallbackWithLocalSync(String msg, long existingBackendId, String serverIp, int serverPort, String terminalId) {
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

                // Save serverIp/serverPort/terminalId to local Room DB
                ApiService.UserDto savedUser = resp.body();
                new Thread(() -> {
                    try {
                        com.example.mysoftpos.data.local.AppDatabase db = com.example.mysoftpos.data.local.AppDatabase
                                .getInstance(UserManagementActivity.this);
                        com.example.mysoftpos.data.local.dao.UserDao userDao = db.userDao();

                        String phoneHash = com.example.mysoftpos.utils.security.PasswordUtils
                                .hashSHA256(savedUser.phone);
                        com.example.mysoftpos.data.local.entity.UserEntity localUser = userDao
                                .findByUsernameHash(phoneHash);

                        if (localUser != null) {
                            localUser.serverIp = serverIp;
                            localUser.serverPort = serverPort;
                            localUser.terminalId = terminalId;
                            localUser.backendId = savedUser.id;
                            userDao.update(localUser);
                        } else {
                            // Create local entry
                            localUser = new com.example.mysoftpos.data.local.entity.UserEntity();
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

}
