package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.testsuite.adapter.SchemeAdapter;
import com.example.mysoftpos.testsuite.model.Scheme;
import com.example.mysoftpos.testsuite.storage.SchemeRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class SchemeSelectActivity extends AppCompatActivity implements SchemeAdapter.OnSchemeListener {

    private SchemeRepository repository;
    private SchemeAdapter adapter;
    private final List<Scheme> schemes = new ArrayList<>();
    private TextView tvSchemeCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scheme_select);

        repository = new SchemeRepository(this);
        tvSchemeCount = findViewById(R.id.tvSchemeCount);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvSchemes);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SchemeAdapter(schemes, this);
        rv.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> showAddEditDialog(null));

        loadSchemes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSchemes();
    }

    private void loadSchemes() {
        schemes.clear();
        schemes.addAll(repository.getAll());
        adapter.notifyDataSetChanged();
        tvSchemeCount.setText(schemes.size() + " schemes");
    }

    @Override
    public void onSchemeClick(Scheme scheme) {
        Intent intent = new Intent(this, ChannelSelectActivity.class);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.SCHEME, scheme.getName());
        startActivity(intent);
    }

    @Override
    public void onSchemeLongClick(Scheme scheme) {
        // Long-press → open edit dialog directly
        showAddEditDialog(scheme);
    }

    private void showAddEditDialog(Scheme existing) {
        boolean isEdit = existing != null;
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_edit_scheme, null);

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvDialogSubtitle);
        EditText etName = dialogView.findViewById(R.id.etSchemeName);
        EditText etPrefix = dialogView.findViewById(R.id.etPrefix);
        EditText etIcon = dialogView.findViewById(R.id.etIconLetter);
        EditText etServerIp = dialogView.findViewById(R.id.etServerIp);
        EditText etServerPort = dialogView.findViewById(R.id.etServerPort);
        EditText etTimeout = dialogView.findViewById(R.id.etTimeout);
        LinearLayout colorPicker = dialogView.findViewById(R.id.colorPicker);
        MaterialButton btnDelete = dialogView.findViewById(R.id.btnDelete);
        MaterialButton btnTestConn = dialogView.findViewById(R.id.btnTestConnection);
        // Live preview
        FrameLayout previewContainer = dialogView.findViewById(R.id.previewIconContainer);
        TextView tvPreviewLetter = dialogView.findViewById(R.id.tvPreviewLetter);
        TextView tvPreviewName = dialogView.findViewById(R.id.tvPreviewName);

        tvTitle.setText(isEdit ? "Edit Scheme" : "Add Scheme");
        tvSubtitle.setText(isEdit ? "Modify card network settings" : "Configure a new card network");

        final String[] selectedColor = { isEdit ? existing.getColor() : "#1565C0" };
        applyPreviewColor(previewContainer, selectedColor[0]);

        // Setup color circles
        for (int i = 0; i < colorPicker.getChildCount(); i++) {
            View circle = colorPicker.getChildAt(i);
            String color = (String) circle.getTag();

            applyColorCircle(circle, color, false);

            circle.setOnClickListener(v -> {
                selectedColor[0] = color;
                applyPreviewColor(previewContainer, color);
                for (int j = 0; j < colorPicker.getChildCount(); j++) {
                    View c = colorPicker.getChildAt(j);
                    applyColorCircle(c, (String) c.getTag(), c.getTag().equals(color));
                }
            });
        }

        // Live text watchers
        etName.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String name = s.toString().trim();
                tvPreviewName.setText(name.isEmpty() ? "New Scheme" : name);
            }
        });

        etIcon.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String letter = s.toString().trim().toUpperCase();
                tvPreviewLetter.setText(letter.isEmpty() ? "?" : letter);
            }
        });

        // Pre-fill for edit
        if (isEdit) {
            etName.setText(existing.getName());
            etPrefix.setText(existing.getPrefix());
            etIcon.setText(existing.getIconLetter());
            // Connection config
            if (existing.getServerIp() != null && !existing.getServerIp().isEmpty()) {
                etServerIp.setText(existing.getServerIp());
            }
            if (existing.getServerPort() > 0) {
                etServerPort.setText(String.valueOf(existing.getServerPort()));
            }
            if (existing.getTimeout() > 0) {
                etTimeout.setText(String.valueOf(existing.getTimeout()));
            }
            // Trigger color selection
            for (int i = 0; i < colorPicker.getChildCount(); i++) {
                View c = colorPicker.getChildAt(i);
                if (existing.getColor().equals(c.getTag())) {
                    c.performClick();
                    break;
                }
            }
        }

        // Connection status indicator
        TextView tvConnStatus = dialogView.findViewById(R.id.tvConnectionStatus);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        // Make dialog background transparent to show rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Show delete button for non-built-in schemes in edit mode
        if (isEdit && !existing.isBuiltIn()) {
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> {
                dialog.dismiss();
                confirmDelete(existing);
            });
        }

        // Inline Cancel
        dialogView.findViewById(R.id.btnDialogCancel).setOnClickListener(v -> dialog.dismiss());

        // Test Connection — show result inline
        btnTestConn.setOnClickListener(v -> {
            String ip = etServerIp.getText().toString().trim();
            String portStr = etServerPort.getText().toString().trim();
            performPingTest(ip, portStr, tvConnStatus);
        });

        // Inline Save
        dialogView.findViewById(R.id.btnDialogSave).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String prefix = etPrefix.getText().toString().trim();
            String icon = etIcon.getText().toString().trim().toUpperCase();

            if (name.isEmpty()) {
                etName.setError("Required");
                return;
            }
            if (icon.isEmpty()) {
                icon = String.valueOf(name.charAt(0)).toUpperCase();
            }

            if (isEdit) {
                existing.setName(name);
                existing.setPrefix(prefix);
                existing.setIconLetter(icon);
                existing.setColor(selectedColor[0]);
                // Connection
                existing.setServerIp(etServerIp.getText().toString().trim());
                try {
                    existing.setServerPort(Integer.parseInt(etServerPort.getText().toString().trim()));
                } catch (NumberFormatException e) {
                    existing.setServerPort(0);
                }
                try {
                    existing.setTimeout(Integer.parseInt(etTimeout.getText().toString().trim()));
                } catch (NumberFormatException e) {
                    existing.setTimeout(30000);
                }
                repository.update(existing);
            } else {
                Scheme newScheme = new Scheme(name, prefix, icon, selectedColor[0], false);
                newScheme.setServerIp(etServerIp.getText().toString().trim());
                try {
                    newScheme.setServerPort(Integer.parseInt(etServerPort.getText().toString().trim()));
                } catch (NumberFormatException e) {
                    newScheme.setServerPort(0);
                }
                try {
                    newScheme.setTimeout(Integer.parseInt(etTimeout.getText().toString().trim()));
                } catch (NumberFormatException e) {
                    newScheme.setTimeout(30000);
                }
                repository.add(newScheme);
            }

            loadSchemes();
            dialog.dismiss();
            Toast.makeText(this, isEdit ? "Updated" : "Added", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void confirmDelete(Scheme scheme) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + scheme.getName() + "?")
                .setMessage("This will permanently remove this scheme.")
                .setPositiveButton("Delete", (d, w) -> {
                    repository.delete(scheme.getId());
                    loadSchemes();
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyPreviewColor(FrameLayout container, String color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        try {
            bg.setColor(Color.parseColor(color));
        } catch (Exception e) {
            bg.setColor(Color.parseColor("#607D8B"));
        }
        container.setBackground(bg);
    }

    private void applyColorCircle(View view, String color, boolean selected) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.parseColor(color));
        if (selected) {
            d.setStroke(4, Color.parseColor("#0F172A"));
        }
        view.setBackground(d);
    }

    // ── Ping Test (same logic as SettingsActivity) ──

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

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
