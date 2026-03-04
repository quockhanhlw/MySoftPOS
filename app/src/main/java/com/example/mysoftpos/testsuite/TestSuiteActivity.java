package com.example.mysoftpos.testsuite;

import com.example.mysoftpos.iso8583.spec.NapasFieldSpecConfig;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import com.example.mysoftpos.R;
import com.example.mysoftpos.testsuite.model.TestScenario;
import com.example.mysoftpos.ui.BaseActivity;
import com.google.android.material.button.MaterialButton;
import java.util.List;
import java.util.ArrayList;

public class TestSuiteActivity extends BaseActivity {

    private String channel;
    private String txnType;
    private String perfMode;
    private String scheme;
    private List<TestScenario> allScenarios = new ArrayList<>(); // Built-in
    private List<TestScenario> customScenarios = new ArrayList<>(); // DB-loaded
    private List<TestScenario> displayedScenarios = new ArrayList<>();
    private com.example.mysoftpos.testsuite.adapter.TestScenarioAdapter adapter;
    private ArrayList<TestScenario> preSelectedScenarios;

    // Multi-thread UI
    private View layoutSelectAll;
    private CheckBox cbSelectAll;
    private MaterialButton btnRunAll;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabAdd;
    private android.widget.ImageView btnDeleteMode;
    private boolean deleteMode = false;

    // Queue for sequential card/PIN config in "Select All"
    private int selectAllIndex = -1;

    // Data Access
    private com.example.mysoftpos.data.repository.TestCaseRepository repository;
    private java.util.concurrent.ExecutorService executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_suite);

        // Init Data Access
        repository = new com.example.mysoftpos.data.repository.TestCaseRepository(this);
        executor = java.util.concurrent.Executors.newSingleThreadExecutor();

        channel = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.CHANNEL);
        txnType = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.TXN_TYPE);
        perfMode = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.PERF_MODE);
        if (perfMode == null)
            perfMode = "SINGLE";

        // Restore selection if available
        if (getIntent().hasExtra(com.example.mysoftpos.utils.IntentKeys.SELECTED_SCENARIOS)) {
            preSelectedScenarios = (ArrayList<TestScenario>) getIntent()
                    .getSerializableExtra(com.example.mysoftpos.utils.IntentKeys.SELECTED_SCENARIOS);
        }

        boolean isMulti = "MULTI".equals(perfMode);

        // TextView tvTitle = findViewById(R.id.tvTitle); // Unused

        // Bind Summary Card
        TextView tvSummaryScheme = findViewById(R.id.tvSummaryScheme);
        TextView tvSummaryChannel = findViewById(R.id.tvSummaryChannel);
        TextView tvSummaryType = findViewById(R.id.tvSummaryType);

        scheme = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.SCHEME);
        if (scheme == null)
            scheme = "Napas";

        tvSummaryScheme.setText(scheme);
        tvSummaryChannel.setText(channel);
        tvSummaryType.setText(txnType);

        androidx.recyclerview.widget.RecyclerView recyclerView = findViewById(R.id.recyclerViewCases);

        // Load Built-in Data
        allScenarios = TestDataProvider.generateScenarios(this, scheme);
        for (TestScenario s : allScenarios) {
            s.setTxnType(txnType);
        }

        adapter = new com.example.mysoftpos.testsuite.adapter.TestScenarioAdapter(
                this::onScenarioClicked,
                this::onScenarioLongClicked,
                this::onScenarioToggle);

        adapter.setMultiMode(isMulti);

        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Controls
        layoutSelectAll = findViewById(R.id.layoutSelectAll);
        cbSelectAll = findViewById(R.id.cbSelectAll);
        btnRunAll = findViewById(R.id.btnRunAll);
        fabAdd = findViewById(R.id.fabAdd);
        btnDeleteMode = findViewById(R.id.btnDeleteMode);

        if (isMulti) {
            setupMultiMode();
        } else {
            // Single Mode
            fabAdd.setVisibility(View.VISIBLE);
            fabAdd.setOnClickListener(v -> showAddEditDialog(null));

            btnDeleteMode.setVisibility(View.VISIBLE);
            btnDeleteMode.setOnClickListener(v -> toggleDeleteMode());

            // Setup selection controls (hidden initially, shown on long-press)
            setupSingleModeSelection();
        }

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (deleteMode) {
                    toggleDeleteMode(); // Exit delete mode
                    return;
                }

                if (selectionMode) {
                    // Save selections and return to TransactionSelectActivity
                    returnSelectedScenarios();
                    return;
                }

                // Default back behavior
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // Swipe back
        com.example.mysoftpos.testsuite.util.SwipeBackHelper.attach(this);
        com.example.mysoftpos.testsuite.util.StepDotsHelper.setActiveStep(this, 4);

        // Custom Cases Observer (TestCaseEntity)
        repository.getCustomCasesBySchemeAndType(scheme, txnType).observe(this, entities -> {
            customScenarios.clear();
            if (entities != null) {
                for (com.example.mysoftpos.data.local.entity.TestCaseEntity entity : entities) {
                    TestScenario s = new TestScenario("0200", entity.name);
                    s.setDescription(entity.name);
                    s.setTxnType(entity.transactionType);
                    s.setId(entity.id);
                    s.setCustom(true);

                    if (entity.de22 != null)   s.setField(22, entity.de22);
                    if (entity.amount != null) s.setField(4,  entity.amount);
                    if (entity.pan != null)    s.setField(2,  entity.pan);
                    if (entity.expiry != null) s.setField(14, entity.expiry);
                    if (entity.track2 != null) s.setField(35, entity.track2);

                    // Load custom field overrides from JSON
                    if (entity.fieldConfigJson != null && !entity.fieldConfigJson.isEmpty()) {
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(entity.fieldConfigJson);
                            java.util.Iterator<String> keys = json.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                int fieldNum = Integer.parseInt(key);
                                s.setField(fieldNum, json.getString(key));
                            }
                        } catch (Exception ignored) {}
                    }

                    customScenarios.add(s);
                }
            }
            refreshList();
        });
    }

    private void onScenarioToggle(TestScenario scenario) {
        // In Delete Mode, checkbox simply toggles selection for deletion
        if (deleteMode) {
            if (scenario.isCustom()) {
                scenario.setSelected(!scenario.isSelected());
                adapter.notifyDataSetChanged();
            } else {
                Toast.makeText(this, "Cannot delete built-in scenarios", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Toggle from Unchecked -> Checked
        if (!scenario.isSelected()) {
            // Revert visual toggle immediately (so it stays unchecked until config is done)
            adapter.notifyDataSetChanged();

            if (!configuringInProgress) {
                configureScenario(scenario);
            }
            return;
        }

        // Toggle from Checked -> Unchecked
        scenario.setSelected(false);
        adapter.notifyDataSetChanged();
    }

    private void checkPinAndConfig(TestScenario scenario) {
        String de22 = scenario.getField(22);
        if ("011".equals(de22) || "021".equals(de22)) {
            showPinDialog(scenario, pin -> {
                scenario.setUserPin(pin);
                // Auto-select after full config
                scenario.setSelected(true);
                adapter.notifyDataSetChanged();
                configuringInProgress = false;
                updateRunAllButton();
                Toast.makeText(this, "Configured & Selected!", Toast.LENGTH_SHORT).show();
            });
        } else {
            // Auto-select after full config
            scenario.setSelected(true);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Configured & Selected!", Toast.LENGTH_SHORT).show();

            configuringInProgress = false;
            updateRunAllButton();
        }
    }

    private void updateRunAllButton() {
        if (displayedScenarios == null)
            return;
        long count = displayedScenarios.stream().filter(TestScenario::isSelected).count();
        String label = "MULTI".equals(perfMode) ? "Run Transaction" : "Confirm Selection";
        if (count > 0) {
            btnRunAll.setText(label + " (" + count + ")");
        } else {
            btnRunAll.setText(label);
        }
    }

    private void setupMultiMode() {
        // Initially hide selection controls in Multi-thread mode too
        layoutSelectAll.setVisibility(View.GONE);
        btnRunAll.setVisibility(View.GONE);

        btnRunAll.setText("Run Transaction");
        btnRunAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF0F172A)); // Dark Blue

        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectAllIndex = 0; // Only relevant for Multi-thread
                configureNextForSelectAll();
            } else {
                for (TestScenario s : displayedScenarios) {
                    s.setSelected(false);
                    s.setUserPin(null);
                }
                adapter.notifyDataSetChanged();
            }
        });

        btnRunAll.setOnClickListener(v -> returnSelectedScenarios());
    }

    private void setupSingleModeSelection() {
        // Always show selection controls in single mode
        selectionMode = true;
        adapter.setMultiMode(true);
        adapter.setSelectionMode(true);
        layoutSelectAll.setVisibility(View.VISIBLE);
        btnRunAll.setVisibility(View.VISIBLE);
        btnRunAll.setText("Confirm Selection");
        btnRunAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF0F172A));

        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (TestScenario s : displayedScenarios) {
                s.setSelected(isChecked);
            }
            adapter.notifyDataSetChanged();
            updateRunAllButton();
        });

        btnRunAll.setOnClickListener(v -> {
            ArrayList<TestScenario> selected = new ArrayList<>();
            for (TestScenario s : displayedScenarios) {
                if (s.isSelected())
                    selected.add(s);
            }
            if (selected.isEmpty()) {
                Toast.makeText(this, "No test cases selected", Toast.LENGTH_SHORT).show();
                return;
            }
            // Return selected scenarios to caller
            returnSelectedScenarios();
        });
    }

    private void exitSelectionMode() {
        selectionMode = false;
        adapter.setMultiMode("MULTI".equals(perfMode));
        adapter.setSelectionMode(false);
        layoutSelectAll.setVisibility(View.GONE);
        btnRunAll.setVisibility(View.GONE);
        cbSelectAll.setChecked(false);

        for (TestScenario s : displayedScenarios) {
            s.setSelected(false);
        }
        adapter.notifyDataSetChanged();

        // Restore SINGLE mode controls
        if (!"MULTI".equals(perfMode)) {
            fabAdd.setVisibility(View.VISIBLE);
            btnDeleteMode.setVisibility(View.VISIBLE);
        }
    }

    private void toggleDeleteMode() {
        deleteMode = !deleteMode;

        if (deleteMode) {
            // Enter Delete Mode
            fabAdd.setVisibility(View.GONE);
            btnDeleteMode.setImageResource(R.drawable.ic_close); // Change icon to close
            btnDeleteMode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEF4444)); // Red bg

            layoutSelectAll.setVisibility(View.VISIBLE);
            btnRunAll.setVisibility(View.VISIBLE);
            btnRunAll.setText("Delete Selected");
            btnRunAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEF4444)); // Red button

            // IMPORTANT: Set BOTH multiMode and selectionMode to true for checkboxes to
            // appear
            adapter.setMultiMode(true);
            adapter.setSelectionMode(true);

            // Logic for "Select All" in Delete Mode
            cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
                for (TestScenario s : displayedScenarios) {
                    // Only select Custom cases for deletion
                    if (s.isCustom())
                        s.setSelected(isChecked);
                }
                adapter.notifyDataSetChanged();
            });

            btnRunAll.setOnClickListener(v -> executeBatchDelete());

        } else {
            // Exit Delete Mode
            fabAdd.setVisibility(View.VISIBLE);
            btnDeleteMode.setImageResource(R.drawable.ic_delete);
            btnDeleteMode.setBackgroundTintList(null); // Reset

            layoutSelectAll.setVisibility(View.GONE);
            btnRunAll.setVisibility(View.GONE);

            // Reset Adapter State — restore to single-mode selection
            if ("MULTI".equals(perfMode)) {
                adapter.setMultiMode(false);
                adapter.setSelectionMode(false);
            } else {
                // Single mode: re-enable selection mode
                adapter.setMultiMode(true);
                adapter.setSelectionMode(true);
                layoutSelectAll.setVisibility(View.VISIBLE);
                btnRunAll.setVisibility(View.VISIBLE);
                selectionMode = true;
            }

            // clear selection
            for (TestScenario s : displayedScenarios)
                s.setSelected(false);
            adapter.notifyDataSetChanged();
            updateRunAllButton();
        }
    }

    private void executeBatchDelete() {
        List<TestScenario> toDelete = new ArrayList<>();
        for (TestScenario s : displayedScenarios) {
            if (s.isSelected() && s.isCustom()) {
                toDelete.add(s);
            }
        }

        if (toDelete.isEmpty()) {
            Toast.makeText(this, "No custom cases selected", Toast.LENGTH_SHORT).show();
            return;
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("Confirm Delete")
                .setMessage("Delete " + toDelete.size() + " test cases?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    executor.execute(() -> {
                        for (TestScenario s : toDelete) {
                            com.example.mysoftpos.data.local.entity.TestCaseEntity entity = new com.example.mysoftpos.data.local.entity.TestCaseEntity();
                            entity.id = s.getId();
                            repository.delete(entity);
                        }
                        runOnUiThread(this::toggleDeleteMode); // Exit mode after delete
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshList() {
        displayedScenarios = new ArrayList<>();
        displayedScenarios.addAll(allScenarios); // Built-in first
        displayedScenarios.addAll(customScenarios); // Custom appended
        adapter.setScenarios(displayedScenarios);

        // Restore selections if this is the first load or if we have
        // preSelectedScenarios
        if (preSelectedScenarios != null && !preSelectedScenarios.isEmpty()) {
            for (TestScenario ds : displayedScenarios) {
                for (TestScenario ps : preSelectedScenarios) {
                    boolean match = false;
                    if (ds.isCustom() && ps.isCustom()) {
                        match = ds.getId() == ps.getId();
                    } else if (!ds.isCustom() && !ps.isCustom()) {
                        match = ds.getDescription().equals(ps.getDescription());
                    }

                    if (match) {
                        ds.setSelected(true);
                        // Restore config if available
                        if (ps.getField(4) != null)
                            ds.setField(4, ps.getField(4)); // Amount
                        if (ps.getField(19) != null)
                            ds.setField(19, ps.getField(19)); // Country
                        if (ps.getField(49) != null)
                            ds.setField(49, ps.getField(49)); // Currency
                        if (ps.getField(2) != null)
                            ds.setField(2, ps.getField(2)); // PAN
                        if (ps.getField(22) != null)
                            ds.setField(22, ps.getField(22)); // DE22
                        if (ps.getUserPin() != null)
                            ds.setUserPin(ps.getUserPin());
                        break;
                    }
                }
            }

            // AUTO-ENABLE Selection Mode UI if we have any matches
            long matchCount = 0;
            for (TestScenario s : displayedScenarios) {
                if (s.isSelected())
                    matchCount++;
            }

            if (matchCount > 0 && adapter != null) {
                selectionMode = true; // Enable activity-level selection mode (for back press)
                if ("MULTI".equals(perfMode)) {
                    // Only strictly enforce in Multi Mode if needed,
                    // but Delete Mode also uses this.
                    // Here we are in likely multi mode or just restoring selection.
                }

                adapter.setSelectionMode(true); // Show checkboxes
                layoutSelectAll.setVisibility(View.VISIBLE);
                btnRunAll.setVisibility(View.VISIBLE);
                updateRunAllButton();

                // Also update checkbox "Select All" state if all are selected?
                if (matchCount == displayedScenarios.size()) {
                    cbSelectAll.setChecked(true);
                }
            }

            // Clear after restoring to avoid re-applying logic if not needed,
            // OR keep it? Better clear it so it doesn't interfere with future refreshes
            // (e.g. after adding custom case)
            // But wait, if user adds a custom case, refreshList is called. We probably want
            // to keep selections?
            // Existing selections in displayedScenarios are preserved?
            // displayedScenarios is recreated: `displayedScenarios = new ArrayList<>();`
            // So we DO need to persist selection state or re-apply it.
            // But `allScenarios` (built-in) might lose state if we don't save it back to
            // `allScenarios`?
            // Actually `allScenarios` content is static/generated.
            // The `displayedScenarios` are what the adapter shows.

            // If I clear `preSelectedScenarios`, subsequent refreshes won't restore.
            // But subsequent refreshes usually happen when data changes.
            // Let's clear it to be safe, users can re-select if they add new items.
            // preSelectedScenarios = null;
        }
    }

    private boolean selectionMode = false;
    private boolean configuringInProgress = false;

    private void onScenarioClicked(TestScenario scenario) {
        if (deleteMode) {
            if (scenario.isCustom()) {
                scenario.setSelected(!scenario.isSelected());
                adapter.notifyDataSetChanged();
            } else {
                Toast.makeText(this, "Cannot delete built-in scenarios", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (scenario.isCustom()) {
            // Custom test case → open edit dialog
            showAddEditDialog(scenario);
        } else {
            // Built-in test case → configure & run
            configureScenario(scenario);
        }
    }

    private void configureScenario(TestScenario scenario) {
        configuringInProgress = true;
        Runnable onAmountConfigured = () -> {
            // 2. Card
            if (scenario.isCustom() && hasCardData(scenario)) {
                checkPinAndConfig(scenario);
                return;
            }

            String de22 = scenario.getField(22);
            if ("011".equals(de22) || "012".equals(de22)) {
                showPanSelectionDialog(scenario, () -> checkPinAndConfig(scenario));
            } else {
                showCardSelectionDialog(scenario, () -> checkPinAndConfig(scenario));
            }
        };

        // Skip Amount Input for Balance Inquiry (check scenario's own txnType)
        String scenarioTxnType = scenario.getTxnType() != null ? scenario.getTxnType() : txnType;
        if ("BALANCE".equals(scenarioTxnType)) {
            onAmountConfigured.run();
            return;
        }

        // Similar to openRunnerSingle but doesn't launch
        // 1. Amount
        showAmountInput(scenario, onAmountConfigured);
    }

    private void onScenarioLongClicked(TestScenario scenario) {
        if (deleteMode) {
            return;
        }
        // In SINGLE mode, selection is always active — long press does nothing extra
        // In MULTI mode, long press activates selection mode
        if ("MULTI".equals(perfMode) && !selectionMode) {
            selectionMode = true;
            adapter.setMultiMode(true);
            adapter.setSelectionMode(true);
            layoutSelectAll.setVisibility(View.VISIBLE);
            btnRunAll.setVisibility(View.VISIBLE);
            fabAdd.setVisibility(View.GONE);
            btnDeleteMode.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Selection Mode. Tap to select test cases.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAddEditDialog(TestScenario existing) {
        boolean isEdit = existing != null;
        boolean isBuiltIn = isEdit && !existing.isCustom();

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_edit_test_case, null);
        builder.setView(view);
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        // ─── Header ───
        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvDialogSubtitle);
        tvTitle.setText(isBuiltIn ? "Configure Scenario"
                : isEdit ? "Edit Test Case" : "New Test Case");
        tvSubtitle.setText(isBuiltIn ? existing.getDescription()
                : isEdit ? "Modify transaction parameters" : "Configure transaction parameters");

        // Close button
        view.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());

        // ─── Basic Fields ───
        com.google.android.material.textfield.TextInputLayout tilName = view.findViewById(R.id.tilName);
        android.widget.EditText etName = view.findViewById(R.id.etName);
        android.widget.EditText etDe22 = view.findViewById(R.id.etDe22);
        android.widget.EditText etAmount = view.findViewById(R.id.etAmount);

        View layoutManual = view.findViewById(R.id.layoutManual);
        com.google.android.material.textfield.TextInputLayout tilPan = view.findViewById(R.id.tilPan);
        android.widget.EditText etPan = view.findViewById(R.id.etPan);
        com.google.android.material.textfield.TextInputLayout tilExpiry = view.findViewById(R.id.tilExpiry);
        android.widget.EditText etExpiry = view.findViewById(R.id.etExpiry);

        View layoutTrack2 = view.findViewById(R.id.layoutTrack2);
        com.google.android.material.textfield.TextInputLayout tilTrack2 = view.findViewById(R.id.tilTrack2);
        android.widget.EditText etTrack2 = view.findViewById(R.id.etTrack2);

        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);
        MaterialButton btnSave = view.findViewById(R.id.btnSave);

        // ─── Entry Mode (DE 22 free input) ───
        View layoutDe22Display = view.findViewById(R.id.layoutDe22Display);
        TextView tvEntryModeName = view.findViewById(R.id.tvEntryModeName);

        // Update field visibility and label based on DE22 value
        Runnable updateEntryModeUI = () -> {
            String code = etDe22.getText().toString().trim();
            boolean isManual = code.startsWith("01");
            boolean hasTrack2 = code.startsWith("02") || code.startsWith("05")
                    || code.startsWith("07") || code.startsWith("91");

            layoutManual.setVisibility(isManual ? View.VISIBLE : View.GONE);
            layoutTrack2.setVisibility(hasTrack2 ? View.VISIBLE : View.GONE);

            // Resolve label
            String label;
            switch (code) {
                case "011": label = "Manual Key-in + PIN"; break;
                case "012": label = "Manual Key-in"; break;
                case "021": label = "Magstripe (Swipe) + PIN"; break;
                case "022": label = "Magstripe (Swipe)"; break;
                case "051": label = "NFC / Chip + PIN"; break;
                case "052": label = "NFC / Chip"; break;
                case "071": label = "Contactless + PIN"; break;
                case "072": label = "Contactless"; break;
                case "911": label = "Fallback + PIN"; break;
                case "912": label = "Fallback"; break;
                default:    label = code.length() == 3 ? "Custom (" + code + ")" : ""; break;
            }

            if (!label.isEmpty()) {
                layoutDe22Display.setVisibility(View.VISIBLE);
                tvEntryModeName.setText(label);
            } else {
                layoutDe22Display.setVisibility(View.GONE);
            }
        };

        // Listen for text changes on DE22 field
        etDe22.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                updateEntryModeUI.run();
            }
        });

        // ─── Advanced Field Configuration ───
        View layoutAdvancedHeader = view.findViewById(R.id.layoutAdvancedHeader);
        android.widget.ImageView ivExpandArrow = view.findViewById(R.id.ivExpandArrow);
        TextView tvFieldCount = view.findViewById(R.id.tvFieldCount);
        android.widget.LinearLayout layoutAdvancedFields = view.findViewById(R.id.layoutAdvancedFields);
        android.widget.LinearLayout containerCustomFields = view.findViewById(R.id.containerCustomFields);
        MaterialButton btnAddField = view.findViewById(R.id.btnAddField);

        final java.util.Set<Integer> reservedFields = new java.util.HashSet<>(java.util.Arrays.asList(2, 4, 14, 22, 35));

        Runnable updateFieldCount = () -> {
            int count = containerCustomFields.getChildCount();
            tvFieldCount.setText(String.valueOf(count));
        };

        // Toggle expand/collapse with animation
        layoutAdvancedHeader.setOnClickListener(hdr -> {
            boolean isExpanded = layoutAdvancedFields.getVisibility() == View.VISIBLE;
            layoutAdvancedFields.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
            ivExpandArrow.animate().rotation(isExpanded ? 0f : 180f).setDuration(200).start();
        });

        // Styled field row builder
        float dp = getResources().getDisplayMetrics().density;
        java.util.function.BiConsumer<String, String> addFieldRowWithData = (fieldNum, fieldVal) -> {
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_field_row);
            row.setPadding((int)(10*dp), (int)(8*dp), (int)(6*dp), (int)(8*dp));
            android.widget.LinearLayout.LayoutParams rowLp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = (int)(8*dp);
            row.setLayoutParams(rowLp);

            // "DE" label
            TextView tvDe = new TextView(this);
            tvDe.setText("DE");
            tvDe.setTextColor(0xFF92400E);
            tvDe.setTextSize(11);
            tvDe.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            android.widget.LinearLayout.LayoutParams deLp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            deLp.setMarginEnd((int)(4*dp));
            tvDe.setLayoutParams(deLp);

            // Field number input
            android.widget.EditText etFieldNum = new android.widget.EditText(this);
            etFieldNum.setHint("#");
            etFieldNum.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            etFieldNum.setTextSize(14);
            etFieldNum.setTypeface(android.graphics.Typeface.MONOSPACE);
            etFieldNum.setTextColor(0xFF0F172A);
            etFieldNum.setHintTextColor(0xFFCBD5E1);
            etFieldNum.setBackground(null);
            etFieldNum.setPadding((int)(4*dp), (int)(6*dp), (int)(4*dp), (int)(6*dp));
            etFieldNum.setMinEms(2);
            etFieldNum.setMaxEms(3);
            android.widget.LinearLayout.LayoutParams numLp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            numLp.setMarginEnd((int)(8*dp));
            etFieldNum.setLayoutParams(numLp);
            if (fieldNum != null) etFieldNum.setText(fieldNum);

            // Separator
            View sep = new View(this);
            sep.setBackgroundColor(0xFFE2E8F0);
            android.widget.LinearLayout.LayoutParams sepLp = new android.widget.LinearLayout.LayoutParams(
                    (int)(1*dp), (int)(24*dp));
            sepLp.setMarginEnd((int)(8*dp));
            sep.setLayoutParams(sepLp);

            // Field value input
            android.widget.EditText etFieldValue = new android.widget.EditText(this);
            etFieldValue.setHint("Value");
            etFieldValue.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
            etFieldValue.setTextSize(14);
            etFieldValue.setTypeface(android.graphics.Typeface.MONOSPACE);
            etFieldValue.setTextColor(0xFF0F172A);
            etFieldValue.setHintTextColor(0xFFCBD5E1);
            etFieldValue.setBackground(null);
            etFieldValue.setPadding((int)(4*dp), (int)(6*dp), (int)(4*dp), (int)(6*dp));
            android.widget.LinearLayout.LayoutParams valLp = new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            etFieldValue.setLayoutParams(valLp);
            if (fieldVal != null) etFieldValue.setText(fieldVal);

            // Remove button (styled)
            android.widget.ImageButton btnRemove = new android.widget.ImageButton(this);
            btnRemove.setImageResource(R.drawable.ic_close);
            btnRemove.setColorFilter(0xFFEF4444);
            btnRemove.setBackgroundResource(R.drawable.bg_btn_remove_field);
            android.widget.LinearLayout.LayoutParams remLp = new android.widget.LinearLayout.LayoutParams(
                    (int)(32*dp), (int)(32*dp));
            remLp.setMarginStart((int)(4*dp));
            btnRemove.setLayoutParams(remLp);
            btnRemove.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
            btnRemove.setPadding((int)(6*dp),(int)(6*dp),(int)(6*dp),(int)(6*dp));
            btnRemove.setOnClickListener(rv -> {
                containerCustomFields.removeView(row);
                updateFieldCount.run();
            });

            row.addView(tvDe);
            row.addView(etFieldNum);
            row.addView(sep);
            row.addView(etFieldValue);
            row.addView(btnRemove);
            containerCustomFields.addView(row);
            updateFieldCount.run();
        };

        btnAddField.setOnClickListener(af -> addFieldRowWithData.accept(null, null));

        // ─── Pre-fill: Custom Fields ───
        if (isEdit) {
            for (java.util.Map.Entry<Integer, String> entry : existing.getAllFields().entrySet()) {
                if (!reservedFields.contains(entry.getKey())) {
                    addFieldRowWithData.accept(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }

        // ─── Pre-fill: Basic Fields ───
        if (isBuiltIn) {
            etName.setText(existing.getDescription());
            etName.setEnabled(false);
            etName.setAlpha(0.6f);
            btnSave.setText("Apply Config");
            btnSave.setIconResource(R.drawable.ic_check);
        }

        if (isEdit) {
            if (!isBuiltIn) {
                etName.setText(existing.getDescription());
            }
            String existDe22 = existing.getField(22);
            if (existDe22 != null) {
                etDe22.setText(existDe22);
            }
            etAmount.setText(existing.getField(4));
            etPan.setText(existing.getField(2));
            etExpiry.setText(existing.getField(14));
            etTrack2.setText(existing.getField(35));
        } else {
            etDe22.setText("051");
            etAmount.setText("100000");
        }
        updateEntryModeUI.run();

        // ─── Save Action ───
        btnSave.setOnClickListener(v -> {
            boolean valid = true;

            // Name
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) { tilName.setError("Required"); valid = false; }
            else { tilName.setError(null); }

            // DE 22
            String de22 = etDe22.getText().toString().trim();
            NapasFieldSpecConfig.FieldSpec de22Spec = NapasFieldSpecConfig.get(22);
            if (de22Spec != null && de22Spec.pattern != null && !de22Spec.pattern.matcher(de22).matches()) {
                Toast.makeText(this, "Invalid entry mode: " + de22, Toast.LENGTH_SHORT).show();
                valid = false;
            }

            // Amount
            String amount = etAmount.getText().toString().trim();
            if (!amount.isEmpty()) {
                try {
                    long amountVal = Long.parseLong(amount);
                    if (amountVal < 10000 || amountVal > 500000000) {
                        etAmount.setError("10,000 → 500,000,000"); valid = false;
                    } else { etAmount.setError(null); }
                } catch (NumberFormatException e) {
                    etAmount.setError("Must be numeric"); valid = false;
                }
            }

            // PAN + Expiry (manual mode)
            if (layoutManual.getVisibility() == View.VISIBLE) {
                String pan = etPan.getText().toString().trim();
                if (pan.isEmpty()) { tilPan.setError("Required"); valid = false; }
                else if (pan.length() < 13 || pan.length() > 19) { tilPan.setError("13-19 digits"); valid = false; }
                else if (!checkLuhn(pan)) { tilPan.setError("Luhn failed"); valid = false; }
                else { tilPan.setError(null); }

                String exp = etExpiry.getText().toString().trim();
                if (exp.isEmpty()) { tilExpiry.setError("Required"); valid = false; }
                else if (exp.length() != 4) { tilExpiry.setError("YYMM"); valid = false; }
                else {
                    try {
                        int yy = Integer.parseInt(exp.substring(0, 2));
                        int mm = Integer.parseInt(exp.substring(2, 4));
                        java.util.Calendar now = java.util.Calendar.getInstance();
                        int curY = now.get(java.util.Calendar.YEAR) % 100;
                        int curM = now.get(java.util.Calendar.MONTH) + 1;
                        if (mm < 1 || mm > 12) { tilExpiry.setError("Invalid month"); valid = false; }
                        else if (yy < curY || (yy == curY && mm < curM)) { tilExpiry.setError("Expired"); valid = false; }
                        else { tilExpiry.setError(null); }
                    } catch (NumberFormatException e) { tilExpiry.setError("YYMM"); valid = false; }
                }
            }

            // Track 2
            if (layoutTrack2.getVisibility() == View.VISIBLE) {
                String t2 = etTrack2.getText().toString().trim();
                if (t2.isEmpty()) { tilTrack2.setError("Required"); valid = false; }
                else { tilTrack2.setError(null); }
            }

            if (!valid) return;

            String panFinal = etPan.getText().toString().trim();
            String expiryFinal = etExpiry.getText().toString().trim();
            String track2Final = etTrack2.getText().toString().trim();

            if (isBuiltIn) {
                existing.setField(22, de22);
                existing.setField(4, amount);
                if (layoutManual.getVisibility() == View.VISIBLE) {
                    existing.setField(2, panFinal);
                    existing.setField(14, expiryFinal);
                    existing.setField(35, null);
                }
                if (layoutTrack2.getVisibility() == View.VISIBLE) {
                    existing.setField(35, track2Final);
                    if (track2Final.contains("=")) {
                        String[] parts = track2Final.split("=");
                        existing.setField(2, parts[0]);
                        if (parts.length > 1 && parts[1].length() >= 4)
                            existing.setField(14, parts[1].substring(0, 4));
                    }
                }
                applyCustomFieldsToScenario(existing, containerCustomFields, reservedFields);
                adapter.notifyDataSetChanged();
                dialog.dismiss();
                Toast.makeText(this, "✓ Configuration applied", Toast.LENGTH_SHORT).show();
                return;
            }

            // Custom: save to DB
            executor.execute(() -> {
                com.example.mysoftpos.data.local.entity.TestCaseEntity entity = new com.example.mysoftpos.data.local.entity.TestCaseEntity();
                if (isEdit) entity.id = existing.getId();
                entity.suiteId = -1;
                entity.name = name;
                entity.transactionType = txnType;
                entity.scheme = scheme;
                entity.timestamp = System.currentTimeMillis();
                entity.status = "NEW";
                entity.de22 = de22;
                entity.amount = amount;
                if (layoutManual.getVisibility() == View.VISIBLE) {
                    entity.pan = panFinal;
                    entity.expiry = expiryFinal;
                }
                if (layoutTrack2.getVisibility() == View.VISIBLE) {
                    entity.track2 = track2Final;
                }
                entity.fieldConfigJson = collectCustomFieldsJson(containerCustomFields, reservedFields);
                if (isEdit) repository.update(entity);
                else repository.insert(entity);
                runOnUiThread(dialog::dismiss);
            });
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private boolean checkLuhn(String cardNo) {
        int nDigits = cardNo.length();
        int nSum = 0;
        boolean isSecond = false;
        for (int i = nDigits - 1; i >= 0; i--) {
            int d = cardNo.charAt(i) - '0';
            if (isSecond)
                d = d * 2;
            nSum += d / 10;
            nSum += d % 10;
            isSecond = !isSecond;
        }
        return (nSum % 10 == 0);
    }

    /**
     * Collects custom field rows from the container and builds a JSON string.
     * Format: {"3":"000000","18":"5999",...}
     */
    private String collectCustomFieldsJson(android.widget.LinearLayout container, java.util.Set<Integer> reserved) {
        org.json.JSONObject json = new org.json.JSONObject();
        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            if (row instanceof android.widget.LinearLayout) {
                android.widget.LinearLayout rowLayout = (android.widget.LinearLayout) row;
                // Row: [0]=tvDe, [1]=etFieldNum, [2]=sep, [3]=etFieldValue, [4]=btnRemove
                if (rowLayout.getChildCount() >= 4) {
                    android.widget.EditText etNum = (android.widget.EditText) rowLayout.getChildAt(1);
                    android.widget.EditText etVal = (android.widget.EditText) rowLayout.getChildAt(3);
                    String numStr = etNum.getText().toString().trim();
                    String valStr = etVal.getText().toString().trim();
                    if (!numStr.isEmpty() && !valStr.isEmpty()) {
                        try {
                            int fieldNum = Integer.parseInt(numStr);
                            if (!reserved.contains(fieldNum)) {
                                json.put(String.valueOf(fieldNum), valStr);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        return json.length() > 0 ? json.toString() : null;
    }

    /**
     * Applies custom field overrides from the advanced section to a TestScenario.
     */
    private void applyCustomFieldsToScenario(TestScenario scenario,
            android.widget.LinearLayout container, java.util.Set<Integer> reserved) {
        // First, remove any existing non-reserved custom fields
        java.util.Set<Integer> toRemove = new java.util.HashSet<>();
        for (Integer key : scenario.getAllFields().keySet()) {
            if (!reserved.contains(key)) {
                toRemove.add(key);
            }
        }
        for (Integer key : toRemove) {
            scenario.getAllFields().remove(key);
        }

        // Then add from the UI rows
        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            if (row instanceof android.widget.LinearLayout) {
                android.widget.LinearLayout rowLayout = (android.widget.LinearLayout) row;
                // Row: [0]=tvDe, [1]=etFieldNum, [2]=sep, [3]=etFieldValue, [4]=btnRemove
                if (rowLayout.getChildCount() >= 4) {
                    android.widget.EditText etNum = (android.widget.EditText) rowLayout.getChildAt(1);
                    android.widget.EditText etVal = (android.widget.EditText) rowLayout.getChildAt(3);
                    String numStr = etNum.getText().toString().trim();
                    String valStr = etVal.getText().toString().trim();
                    if (!numStr.isEmpty() && !valStr.isEmpty()) {
                        try {
                            int fieldNum = Integer.parseInt(numStr);
                            if (!reserved.contains(fieldNum)) {
                                scenario.setField(fieldNum, valStr);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    // ========================
    // SINGLE-THREAD FLOW (existing)
    // ========================

    private void openRunnerSingle(TestScenario scenario) {
        // Skip Amount Input for Balance Inquiry
        if ("BALANCE".equals(txnType)) {
            proceedWithRunner(scenario);
            return;
        }

        // If Custom scenario has a valid amount, use it directly (Skip Dialog)
        if (scenario.isCustom()) {
            String currentAmount = scenario.getField(4);
            if (currentAmount != null && !currentAmount.isEmpty()) {
                proceedWithRunner(scenario);
                return;
            }
        }

        showAmountInput(scenario, () -> proceedWithRunner(scenario));
    }

    private void proceedWithRunner(TestScenario scenario) {
        // Custom test cases with saved card data skip the card selection dialog
        if (scenario.isCustom() && hasCardData(scenario)) {
            checkPinAndLaunch(scenario);
            return;
        }

        String de22 = scenario.getField(22);
        if ("011".equals(de22) || "012".equals(de22)) {
            showPanSelectionDialog(scenario, () -> checkPinAndLaunch(scenario));
        } else {
            showCardSelectionDialog(scenario, () -> checkPinAndLaunch(scenario));
        }
    }

    private void showAmountInput(TestScenario scenario, Runnable onConfirmed) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_amount_input, null);
        builder.setView(view);
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        android.widget.TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        tvTitle.setText("Amount (" + scenario.getDescription() + ")");

        com.google.android.material.textfield.TextInputEditText etAmount = view.findViewById(R.id.etAmount);
        com.google.android.material.textfield.TextInputLayout tilAmount = view.findViewById(R.id.tilAmount);

        android.widget.RadioGroup rgCurrency = view.findViewById(R.id.rgCurrency);
        android.widget.RadioButton rbVnd = view.findViewById(R.id.rbVnd);
        android.widget.RadioButton rbUsd = view.findViewById(R.id.rbUsd);

        String current = scenario.getField(4);
        if (current != null) {
            etAmount.setText(current);
        } else {
            etAmount.setText("");
        }

        // Pre-select Currency
        String currentCurr = scenario.getField(49);
        if ("840".equals(currentCurr)) {
            rbUsd.setChecked(true);
            tilAmount.setSuffixText("USD");
        } else {
            rbVnd.setChecked(true);
            tilAmount.setSuffixText("VND");
        }

        // Listener to update Suffix
        rgCurrency.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbUsd) {
                tilAmount.setSuffixText("USD");
            } else {
                tilAmount.setSuffixText("VND");
            }
        });

        etAmount.requestFocus();
        // Show keyboard?

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> {
            configuringInProgress = false;
            dialog.dismiss();
        });
        view.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            String val = etAmount.getText().toString().trim();
            if (val.isEmpty()) {
                etAmount.setError("Required");
                return;
            }
            try {
                long l = Long.parseLong(val);
                if (l <= 0) {
                    etAmount.setError("Must be > 0");
                    return;
                }
            } catch (NumberFormatException e) {
                etAmount.setError("Invalid number");
                return;
            }

            // Save Amount (DE 4)
            scenario.setField(4, val);

            // Save Currency (DE 49) and Country Code (DE 19)
            if (rbUsd.isChecked()) {
                scenario.setField(49, "840"); // USD
                scenario.setField(19, "840"); // USD Country Code
            } else {
                scenario.setField(49, "704"); // VND
                scenario.setField(19, "704"); // VND Country Code
            }

            dialog.dismiss();
            onConfirmed.run();
        });

        dialog.show();
    }

    private boolean hasCardData(TestScenario scenario) {
        String de22 = scenario.getField(22);
        if (de22 == null)
            return false;

        // Manual entry (01x): needs PAN
        if (de22.startsWith("01")) {
            return scenario.getField(2) != null && !scenario.getField(2).isEmpty();
        }
        // Mag/Chip (02x, 05x, 07x, 91x): needs Track2
        return scenario.getField(35) != null && !scenario.getField(35).isEmpty();
    }

    private void applyPanData(TestScenario scenario, String pan) {
        scenario.setField(2, pan);
        scenario.setField(14, "3101");
        scenario.setField(35, null);
    }

    private void showPanSelectionDialog(TestScenario scenario, Runnable onDone) {
        final java.util.List<String> panOptions = java.util.Arrays.asList(
                "9704166606226219923",
                "9704306669144645257",
                "9704189991010867647");

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_card_selection, null);
        builder.setView(view);
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        tvTitle.setText("Select Card (PAN)");

        androidx.recyclerview.widget.RecyclerView rv = view.findViewById(R.id.recyclerViewCards);
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));

        com.example.mysoftpos.testsuite.adapter.CardOptionAdapter adapter = new com.example.mysoftpos.testsuite.adapter.CardOptionAdapter(
                panOptions, selected -> {
                    applyPanData(scenario, selected);
                    dialog.dismiss();
                    onDone.run();
                });
        rv.setAdapter(adapter);

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> {
            configuringInProgress = false;
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showCardSelectionDialog(TestScenario scenario, Runnable onDone) {
        final java.util.List<String> cardOptions = java.util.Arrays.asList(
                "9704166606226219923=31016010000000123",
                "9704306669144645257=31016010000000123",
                "9704189991010867647=31016010000000123");

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_card_selection, null);
        builder.setView(view);
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        tvTitle.setText("Select Card");

        androidx.recyclerview.widget.RecyclerView rv = view.findViewById(R.id.recyclerViewCards);
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));

        com.example.mysoftpos.testsuite.adapter.CardOptionAdapter adapter = new com.example.mysoftpos.testsuite.adapter.CardOptionAdapter(
                cardOptions, selected -> {
                    applyCardData(scenario, selected);
                    dialog.dismiss();
                    onDone.run();
                });
        rv.setAdapter(adapter);

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> {
            configuringInProgress = false;
            dialog.dismiss();
        });
        dialog.show();
    }

    private void applyCardData(TestScenario scenario, String track2Raw) {
        if (track2Raw.contains("=")) {
            String[] parts = track2Raw.split("=");
            String pan = parts[0];
            String expiry = "";
            if (parts.length > 1 && parts[1].length() >= 4) {
                expiry = parts[1].substring(0, 4);
            }
            scenario.setField(2, pan);
            scenario.setField(35, track2Raw);
            scenario.setField(14, expiry);
        }
    }

    private void checkPinAndLaunch(TestScenario scenario) {
        String de22 = scenario.getField(22);
        if ("011".equals(de22) || "021".equals(de22)) {
            showPinDialog(scenario, pin -> {
                scenario.setUserPin(pin);
                launchRunner(scenario);
            });
        } else {
            launchRunner(scenario);
        }
    }

    private interface PinCallback {
        void onPin(String pin);
    }

    private void showPinDialog(TestScenario scenario, PinCallback callback) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pin_entry, null);
        builder.setView(dialogView);
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        android.widget.ImageView[] dots = new android.widget.ImageView[] {
                dialogView.findViewById(R.id.dot1),
                dialogView.findViewById(R.id.dot2),
                dialogView.findViewById(R.id.dot3),
                dialogView.findViewById(R.id.dot4),
                dialogView.findViewById(R.id.dot5),
                dialogView.findViewById(R.id.dot6)
        };
        StringBuilder pinBuilder = new StringBuilder();

        View.OnClickListener numListener = v -> {
            if (pinBuilder.length() < 6) {
                pinBuilder.append(((TextView) v).getText());
                updateDots(dots, pinBuilder.length());
            }
        };

        int[] btnIds = {
                R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        };
        for (int id : btnIds) {
            dialogView.findViewById(id).setOnClickListener(numListener);
        }

        dialogView.findViewById(R.id.btnBackspace).setOnClickListener(v -> {
            if (pinBuilder.length() > 0) {
                pinBuilder.deleteCharAt(pinBuilder.length() - 1);
                updateDots(dots, pinBuilder.length());
            }
        });

        dialogView.findViewById(R.id.btnOk).setOnClickListener(v -> {
            dialog.dismiss();
            callback.onPin(pinBuilder.toString());
        });

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> {
            configuringInProgress = false;
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateDots(android.widget.ImageView[] dots, int length) {
        for (int i = 0; i < dots.length; i++) {
            if (i < length) {
                dots[i].setImageResource(R.drawable.bg_pin_dot_filled);
            } else {
                dots[i].setImageResource(R.drawable.bg_pin_dot_empty);
            }
        }
    }

    private void launchRunner(TestScenario scenario) {
        String pinBlock = null;
        if ("PIN_BLOCK_PRESENT".equals(scenario.getField(52))) {
            // Check if we have a specific user entered PIN
            if (scenario.getUserPin() != null) {
                pinBlock = scenario.getUserPin(); // This is raw PIN, RunnerViewModel generates block
            } else {
                pinBlock = "123456"; // Default
            }
        }

        android.content.Intent i = new android.content.Intent(this, RunnerActivity.class);
        i.putExtra(com.example.mysoftpos.utils.IntentKeys.DESC, scenario.getDescription());
        i.putExtra(com.example.mysoftpos.utils.IntentKeys.DE22, scenario.getField(22));
        i.putExtra(com.example.mysoftpos.utils.IntentKeys.PAN, scenario.getField(2));
        i.putExtra(com.example.mysoftpos.utils.IntentKeys.EXPIRY, scenario.getField(14));
        i.putExtra(com.example.mysoftpos.utils.IntentKeys.TRACK2, scenario.getField(35));
        i.putExtra(com.example.mysoftpos.utils.IntentKeys.PIN_BLOCK, pinBlock);
        i.putExtra(com.example.mysoftpos.utils.IntentKeys.TXN_TYPE,
                scenario.getTxnType() != null ? scenario.getTxnType() : "PURCHASE");

        // Pass the amount (DE 4)
        i.putExtra(com.example.mysoftpos.utils.IntentKeys.AMOUNT, scenario.getField(4));

        // Pass scheme for per-scheme connection config
        i.putExtra(com.example.mysoftpos.utils.IntentKeys.SCHEME, scheme);

        // Pass custom field overrides as JSON
        java.util.Set<Integer> reserved = new java.util.HashSet<>(java.util.Arrays.asList(2, 4, 14, 22, 35, 52));
        org.json.JSONObject fieldJson = new org.json.JSONObject();
        try {
            for (java.util.Map.Entry<Integer, String> entry : scenario.getAllFields().entrySet()) {
                if (!reserved.contains(entry.getKey()) && entry.getValue() != null) {
                    fieldJson.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        } catch (Exception ignored) {}
        if (fieldJson.length() > 0) {
            i.putExtra(com.example.mysoftpos.utils.IntentKeys.FIELD_CONFIG_JSON, fieldJson.toString());
        }

        startActivity(i);
    }

    // ========================
    // MULTI-THREAD FLOW
    // ========================

    private void configureForMultiMode(TestScenario scenario) {
        if (scenario.isSelected()) {
            scenario.setSelected(false);
            scenario.setUserPin(null);
            adapter.notifyDataSetChanged();
            updateSelectAllCheckbox();
            return;
        }

        String de22 = scenario.getField(22);

        Runnable afterCard = () -> {
            if ("011".equals(de22) || "021".equals(de22)) {
                showPinDialog(scenario, pin -> {
                    scenario.setUserPin(pin);
                    scenario.setSelected(true);
                    adapter.notifyDataSetChanged();
                    updateSelectAllCheckbox();

                    if (selectAllIndex >= 0) {
                        selectAllIndex++;
                        configureNextForSelectAll();
                    }
                });
            } else {
                scenario.setSelected(true);
                adapter.notifyDataSetChanged();
                updateSelectAllCheckbox();

                if (selectAllIndex >= 0) {
                    selectAllIndex++;
                    configureNextForSelectAll();
                }
            }
        };

        if ("011".equals(de22) || "012".equals(de22)) {
            showPanSelectionDialog(scenario, afterCard);
        } else {
            showCardSelectionDialog(scenario, afterCard);
        }
    }

    private void configureNextForSelectAll() {
        if (selectAllIndex >= displayedScenarios.size()) {
            selectAllIndex = -1;
            return;
        }
        TestScenario next = displayedScenarios.get(selectAllIndex);
        if (next.isSelected()) {
            selectAllIndex++;
            configureNextForSelectAll();
            return;
        }
        configureForMultiMode(next);
    }

    private void updateSelectAllCheckbox() {
        boolean all = true;
        for (TestScenario s : displayedScenarios) {
            if (!s.isSelected()) {
                all = false;
                break;
            }
        }
        cbSelectAll.setOnCheckedChangeListener(null);
        cbSelectAll.setChecked(all);
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectAllIndex = 0;
                configureNextForSelectAll();
            } else {
                for (TestScenario s : displayedScenarios) {
                    s.setSelected(false);
                    s.setUserPin(null);
                }
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void returnSelectedScenarios() {
        ArrayList<TestScenario> selected = new ArrayList<>();
        for (TestScenario s : displayedScenarios) {
            if (s.isSelected())
                selected.add(s);
        }

        Intent data = new Intent();
        data.putExtra(com.example.mysoftpos.utils.IntentKeys.SELECTED_SCENARIOS, selected);
        data.putExtra(com.example.mysoftpos.utils.IntentKeys.TXN_TYPE, txnType);
        setResult(RESULT_OK, data);
        finish();
    }
}
