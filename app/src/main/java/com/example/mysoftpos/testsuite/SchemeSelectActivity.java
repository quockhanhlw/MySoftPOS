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
import com.example.mysoftpos.ui.BaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class SchemeSelectActivity extends BaseActivity implements SchemeAdapter.OnSchemeListener {

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

        // Swipe back
        com.example.mysoftpos.testsuite.util.SwipeBackHelper.attach(this);
        com.example.mysoftpos.testsuite.util.StepDotsHelper.setActiveStep(this, 1);

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
        // New fields
        EditText etTerminalId = dialogView.findViewById(R.id.etTerminalId);
        EditText etMerchantId = dialogView.findViewById(R.id.etMerchantId);
        EditText etMcc = dialogView.findViewById(R.id.etMcc);
        EditText etPosCondition = dialogView.findViewById(R.id.etPosCondition);
        EditText etAcquirerId = dialogView.findViewById(R.id.etAcquirerId);
        EditText etCurrencyCode = dialogView.findViewById(R.id.etCurrencyCode);
        EditText etCountryCode = dialogView.findViewById(R.id.etCountryCode);
        EditText etMerchantName = dialogView.findViewById(R.id.etMerchantName);
        EditText etMerchantLocation = dialogView.findViewById(R.id.etMerchantLocation);
        EditText etMerchantCountry = dialogView.findViewById(R.id.etMerchantCountry);
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
            // Terminal / Merchant
            if (existing.getTerminalId() != null && !existing.getTerminalId().isEmpty())
                etTerminalId.setText(existing.getTerminalId());
            if (existing.getMerchantId() != null && !existing.getMerchantId().isEmpty())
                etMerchantId.setText(existing.getMerchantId());
            if (existing.getMcc() != null && !existing.getMcc().isEmpty())
                etMcc.setText(existing.getMcc());
            if (existing.getPosConditionCode() != null && !existing.getPosConditionCode().isEmpty())
                etPosCondition.setText(existing.getPosConditionCode());
            if (existing.getAcquirerId() != null && !existing.getAcquirerId().isEmpty())
                etAcquirerId.setText(existing.getAcquirerId());
            if (existing.getCurrencyCode() != null && !existing.getCurrencyCode().isEmpty())
                etCurrencyCode.setText(existing.getCurrencyCode());
            if (existing.getCountryCode() != null && !existing.getCountryCode().isEmpty())
                etCountryCode.setText(existing.getCountryCode());
            if (existing.getMerchantName() != null && !existing.getMerchantName().isEmpty())
                etMerchantName.setText(existing.getMerchantName());
            if (existing.getMerchantLocation() != null && !existing.getMerchantLocation().isEmpty())
                etMerchantLocation.setText(existing.getMerchantLocation());
            if (existing.getMerchantCountry() != null && !existing.getMerchantCountry().isEmpty())
                etMerchantCountry.setText(existing.getMerchantCountry());
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
                String error = validateAndApply(existing, etServerIp, etServerPort, etTimeout,
                        etTerminalId, etMerchantId, etMcc, etPosCondition,
                        etAcquirerId, etCurrencyCode, etCountryCode,
                        etMerchantName, etMerchantLocation, etMerchantCountry);
                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                    return;
                }
                repository.update(existing);
            } else {
                Scheme newScheme = new Scheme(name, prefix, icon, selectedColor[0], false);
                String error = validateAndApply(newScheme, etServerIp, etServerPort, etTimeout,
                        etTerminalId, etMerchantId, etMcc, etPosCondition,
                        etAcquirerId, etCurrencyCode, etCountryCode,
                        etMerchantName, etMerchantLocation, etMerchantCountry);
                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                    return;
                }
                repository.add(newScheme);
            }

            loadSchemes();
            dialog.dismiss();
            Toast.makeText(this, isEdit ? "Updated" : "Added", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    /**
     * Validate all fields and return error message, or null if OK.
     * Auto-formats values to ISO 8583 spec before saving.
     */
    private String validateAndApply(Scheme scheme,
            EditText etIp, EditText etPort, EditText etTimeout,
            EditText etTid, EditText etMid, EditText etMcc, EditText etPosCond,
            EditText etAcq, EditText etCurrency, EditText etCountry,
            EditText etMName, EditText etMLoc, EditText etMCountry) {

        // ═══ Connection ═══
        String ip = etIp.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();
        String timeoutStr = etTimeout.getText().toString().trim();

        // IP: validate format if not empty
        if (!ip.isEmpty() && !ip.matches("^\\d{1,3}(\\.\\d{1,3}){3}$") && !ip.matches("^[a-zA-Z0-9.-]+$")) {
            etIp.setError("IP không hợp lệ");
            etIp.requestFocus();
            return "IP không hợp lệ";
        }
        scheme.setServerIp(ip);

        // Port: 1–65535
        if (!portStr.isEmpty()) {
            try {
                int port = Integer.parseInt(portStr);
                if (port < 1 || port > 65535) {
                    etPort.setError("1 – 65535");
                    etPort.requestFocus();
                    return "Port phải từ 1 đến 65535";
                }
                scheme.setServerPort(port);
            } catch (NumberFormatException e) {
                etPort.setError("Số không hợp lệ");
                etPort.requestFocus();
                return "Port không hợp lệ";
            }
        } else {
            scheme.setServerPort(0);
        }

        // Timeout: 1000–120000 ms
        if (!timeoutStr.isEmpty()) {
            try {
                int timeout = Integer.parseInt(timeoutStr);
                if (timeout < 1000) timeout = 1000;
                if (timeout > 120000) timeout = 120000;
                scheme.setTimeout(timeout);
            } catch (NumberFormatException e) {
                scheme.setTimeout(30000);
            }
        } else {
            scheme.setTimeout(30000);
        }

        // If IP is set, port is required
        if (!ip.isEmpty() && scheme.getServerPort() == 0) {
            etPort.setError("Bắt buộc khi có IP");
            etPort.requestFocus();
            return "Cần nhập Port khi đã nhập IP";
        }

        // ═══ Terminal / Merchant — auto-format to ISO 8583 ═══
        String tid = etTid.getText().toString().trim().toUpperCase();
        String mid = etMid.getText().toString().trim().toUpperCase();
        String mcc = etMcc.getText().toString().trim();
        String posCond = etPosCond.getText().toString().trim();
        String acq = etAcq.getText().toString().trim();
        String currency = etCurrency.getText().toString().trim();
        String country = etCountry.getText().toString().trim();
        String mName = etMName.getText().toString().trim().toUpperCase();
        String mLoc = etMLoc.getText().toString().trim().toUpperCase();
        String mCountry = etMCountry.getText().toString().trim().toUpperCase();

        // Terminal ID: max 8, only alphanumeric
        if (!tid.isEmpty()) {
            if (!tid.matches("[A-Z0-9]+")) {
                etTid.setError("Chỉ chữ và số");
                etTid.requestFocus();
                return "Terminal ID chỉ được chứa chữ cái và số";
            }
            if (tid.length() > 8) tid = tid.substring(0, 8);
        }
        scheme.setTerminalId(tid);

        // Merchant ID: max 15, only alphanumeric
        if (!mid.isEmpty()) {
            if (!mid.matches("[A-Z0-9]+")) {
                etMid.setError("Chỉ chữ và số");
                etMid.requestFocus();
                return "Merchant ID chỉ được chứa chữ cái và số";
            }
            if (mid.length() > 15) mid = mid.substring(0, 15);
        }
        scheme.setMerchantId(mid);

        // MCC: 4 digits
        if (!mcc.isEmpty()) {
            if (!mcc.matches("\\d+")) {
                etMcc.setError("Chỉ nhập số");
                etMcc.requestFocus();
                return "MCC chỉ được chứa số";
            }
            if (mcc.length() > 4) mcc = mcc.substring(0, 4);
            mcc = padLeft(mcc, 4);  // "12" → "0012"
        }
        scheme.setMcc(mcc);

        // POS Condition: 2 digits
        if (!posCond.isEmpty()) {
            if (!posCond.matches("\\d+")) {
                etPosCond.setError("Chỉ nhập số");
                etPosCond.requestFocus();
                return "POS Condition chỉ được chứa số";
            }
            if (posCond.length() > 2) posCond = posCond.substring(0, 2);
            posCond = padLeft(posCond, 2);  // "0" → "00"
        }
        scheme.setPosConditionCode(posCond);

        // Acquirer ID: only digits
        if (!acq.isEmpty() && !acq.matches("\\d+")) {
            etAcq.setError("Chỉ nhập số");
            etAcq.requestFocus();
            return "Acquirer ID chỉ được chứa số";
        }
        scheme.setAcquirerId(acq);


        // Currency Code: 3 digits
        if (!currency.isEmpty()) {
            if (!currency.matches("\\d+")) {
                etCurrency.setError("Chỉ nhập số");
                etCurrency.requestFocus();
                return "Currency Code chỉ được chứa số";
            }
            currency = padLeft(currency, 3);  // "4" → "004"
        }
        scheme.setCurrencyCode(currency);

        // Country Code: 3 digits
        if (!country.isEmpty()) {
            if (!country.matches("\\d+")) {
                etCountry.setError("Chỉ nhập số");
                etCountry.requestFocus();
                return "Country Code chỉ được chứa số";
            }
            country = padLeft(country, 3);
        }
        scheme.setCountryCode(country);

        // Merchant Name: max 22 chars
        if (mName.length() > 22) mName = mName.substring(0, 22);
        scheme.setMerchantName(mName);

        // Location: max 13 chars
        if (mLoc.length() > 13) mLoc = mLoc.substring(0, 13);
        scheme.setMerchantLocation(mLoc);

        // Merchant Country: 3 alpha chars (e.g. VNM, USA)
        if (!mCountry.isEmpty()) {
            if (!mCountry.matches("[A-Z]+")) {
                etMCountry.setError("Chỉ nhập chữ cái");
                etMCountry.requestFocus();
                return "Country chỉ được chứa chữ cái (VNM, USA...)";
            }
            if (mCountry.length() > 3) mCountry = mCountry.substring(0, 3);
        }
        scheme.setMerchantCountry(mCountry);

        return null; // OK
    }

    /** Pad string with leading zeros */
    private static String padLeft(String s, int length) {
        if (s == null) s = "";
        while (s.length() < length) s = "0" + s;
        return s;
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
