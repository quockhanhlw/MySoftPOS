package com.example.mysoftpos.ui.admin;

import android.graphics.Color;
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
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.dao.UserDao;
import com.example.mysoftpos.data.local.entity.UserEntity;
import com.example.mysoftpos.utils.security.PasswordUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserManagementActivity extends AppCompatActivity implements UserAdapter.OnUserListener {

    private UserDao userDao;
    private UserAdapter adapter;
    private String adminId;
    private TextView tvUserCount;
    private View layoutEmpty;
    private EditText etSearch;
    private java.util.List<UserEntity> allUsers = new java.util.ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_management);

        adminId = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME);
        if (adminId != null) {
            adminId = PasswordUtils.hashSHA256(adminId);
        }

        userDao = AppDatabase.getInstance(this).userDao();

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

        // Search filter
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

        // Observe users
        if (adminId != null) {
            userDao.getAllByAdminId(adminId).observe(this, users -> {
                allUsers = users != null ? users : new java.util.ArrayList<>();
                filterUsers(etSearch.getText().toString().trim());
            });
        }
    }

    private void filterUsers(String query) {
        java.util.List<UserEntity> filtered;
        if (query.isEmpty()) {
            filtered = allUsers;
        } else {
            String q = query.toLowerCase();
            filtered = new java.util.ArrayList<>();
            for (UserEntity u : allUsers) {
                boolean nameMatch = u.displayName != null && u.displayName.toLowerCase().contains(q);
                boolean phoneMatch = u.phone != null && u.phone.contains(q);
                boolean emailMatch = u.email != null && u.email.toLowerCase().contains(q);
                if (nameMatch || phoneMatch || emailMatch) {
                    filtered.add(u);
                }
            }
        }
        adapter.setUsers(filtered);
        tvUserCount.setText(filtered.size() + " users");
        layoutEmpty.setVisibility(filtered.isEmpty() && allUsers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onUserClick(UserEntity user) {
        showAddEditDialog(user);
    }

    @Override
    public void onUserLongClick(UserEntity user) {
        showAddEditDialog(user);
    }

    private void showAddEditDialog(UserEntity existing) {
        boolean isEdit = existing != null;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_edit_user, null);

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvDialogSubtitle);
        tvTitle.setText(isEdit ? "Edit User" : "Add User");
        tvSubtitle.setText(isEdit ? "Modify user account settings" : "Create a new user account");

        com.google.android.material.textfield.TextInputLayout tilName = dialogView.findViewById(R.id.tilName);
        EditText etName = dialogView.findViewById(R.id.etName);
        com.google.android.material.textfield.TextInputLayout tilEmail = dialogView.findViewById(R.id.tilEmail);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        com.google.android.material.textfield.TextInputLayout tilPhone = dialogView.findViewById(R.id.tilPhone);
        EditText etPhone = dialogView.findViewById(R.id.etPhone);
        com.google.android.material.textfield.TextInputLayout tilPassword = dialogView.findViewById(R.id.tilPassword);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        EditText etServerIp = dialogView.findViewById(R.id.etServerIp);
        EditText etServerPort = dialogView.findViewById(R.id.etServerPort);
        MaterialButton btnTestConn = dialogView.findViewById(R.id.btnTestConnection);
        TextView tvConnStatus = dialogView.findViewById(R.id.tvConnectionStatus);
        MaterialButton btnDelete = dialogView.findViewById(R.id.btnDelete);

        // Pre-fill for edit
        if (isEdit) {
            etName.setText(existing.displayName);
            etEmail.setText(existing.email);
            etPhone.setText(existing.phone);
            tilPassword.setHint("Password (leave blank to keep)");
            if (existing.serverIp != null && !existing.serverIp.isEmpty()) {
                etServerIp.setText(existing.serverIp);
            }
            if (existing.serverPort > 0) {
                etServerPort.setText(String.valueOf(existing.serverPort));
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Test Connection
        btnTestConn.setOnClickListener(v -> {
            String ip = etServerIp.getText().toString().trim();
            String portStr = etServerPort.getText().toString().trim();
            performPingTest(ip, portStr, tvConnStatus);
        });

        // Delete
        if (isEdit) {
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> {
                dialog.dismiss();
                confirmDelete(existing);
            });
        }

        // Cancel
        dialogView.findViewById(R.id.btnDialogCancel).setOnClickListener(v -> dialog.dismiss());

        // Save
        dialogView.findViewById(R.id.btnDialogSave).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String serverIp = etServerIp.getText().toString().trim();
            String portStr = etServerPort.getText().toString().trim();

            // Validation
            boolean valid = true;
            if (name.isEmpty()) {
                tilName.setError("Required");
                valid = false;
            } else
                tilName.setError(null);

            // Email is optional
            tilEmail.setError(null);

            if (phone.isEmpty()) {
                tilPhone.setError("Required");
                valid = false;
            } else
                tilPhone.setError(null);

            if (!isEdit && password.isEmpty()) {
                tilPassword.setError("Required");
                valid = false;
            } else
                tilPassword.setError(null);

            if (!valid)
                return;

            int serverPort = 0;
            try {
                serverPort = Integer.parseInt(portStr);
            } catch (Exception ignored) {
            }

            final int finalPort = serverPort;
            executor.execute(() -> {
                try {
                    if (isEdit) {
                        // Check phone uniqueness (exclude self)
                        if (!phone.equals(existing.phone) && userDao.existsByPhone(phone)) {
                            runOnUiThread(() -> tilPhone.setError("Phone already exists"));
                            return;
                        }

                        existing.displayName = name;
                        // Update usernameHash if phone changed (login uses hash(phone))
                        if (!phone.equals(existing.phone)) {
                            existing.usernameHash = PasswordUtils.hashSHA256(phone);
                        }
                        existing.email = email;
                        existing.phone = phone;
                        existing.serverIp = serverIp;
                        existing.serverPort = finalPort;
                        if (!password.isEmpty()) {
                            existing.passwordHash = PasswordUtils.hashSHA256(password);
                        }
                        userDao.update(existing);
                        runOnUiThread(() -> {
                            Toast.makeText(this, "User updated", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        });
                    } else {
                        // Check phone duplicate
                        if (userDao.existsByPhone(phone)) {
                            runOnUiThread(() -> tilPhone.setError("Phone already exists"));
                            return;
                        }

                        String usernameHash = PasswordUtils.hashSHA256(phone);
                        if (userDao.existsByUsernameHash(usernameHash)) {
                            runOnUiThread(() -> tilPhone.setError("Phone already registered"));
                            return;
                        }

                        String passwordHash = PasswordUtils.hashSHA256(password);

                        UserEntity user = new UserEntity(usernameHash, passwordHash, name, "USER", email, phone, null);
                        user.adminId = adminId;
                        user.serverIp = serverIp;
                        user.serverPort = finalPort;
                        userDao.insert(user);

                        runOnUiThread(() -> {
                            Toast.makeText(this, "User created", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
        });

        dialog.show();
    }

    private void confirmDelete(UserEntity user) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + user.displayName + "?")
                .setMessage("This will permanently remove this user account.")
                .setPositiveButton("Delete", (d, w) -> {
                    executor.execute(() -> {
                        userDao.delete(user);
                        runOnUiThread(() -> Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show());
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performPingTest(String serverIp, String portStr, TextView tvStatus) {
        if (serverIp.isEmpty() || portStr.isEmpty()) {
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setTextColor(Color.parseColor("#EF4444"));
            tvStatus.setText("⚠️ Please enter IP and Port first");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setTextColor(Color.parseColor("#EF4444"));
            tvStatus.setText("⚠️ Invalid Port");
            return;
        }

        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setTextColor(Color.parseColor("#94A3B8"));
        tvStatus.setText("⏳ Connecting to " + serverIp + ":" + port + "...");

        new Thread(() -> {
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(serverIp, port), 5000);
                runOnUiThread(() -> {
                    tvStatus.setTextColor(Color.parseColor("#10B981"));
                    tvStatus.setText("✅ Connection Successful!");
                });
            } catch (java.io.IOException e) {
                runOnUiThread(() -> {
                    tvStatus.setTextColor(Color.parseColor("#EF4444"));
                    tvStatus.setText("❌ Failed: " + e.getMessage());
                });
            }
        }).start();
    }
}
