package com.example.mysoftpos.testsuite;

import com.example.mysoftpos.iso8583.spec.NapasFieldSpecConfig;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mysoftpos.R;
import com.example.mysoftpos.testsuite.model.TestScenario;
import com.google.android.material.button.MaterialButton;
import java.util.List;
import java.util.ArrayList;

public class TestSuiteActivity extends AppCompatActivity {

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

        // Custom Cases Observer
        repository.getCustomCasesBySchemeAndType(scheme, txnType).observe(this, entities -> {
            customScenarios.clear();
            if (entities != null) {
                for (com.example.mysoftpos.data.local.entity.TestCaseEntity entity : entities) {
                    TestScenario s = new TestScenario("0200", entity.name);
                    s.setDescription(entity.name);
                    s.setTxnType(entity.transactionType);
                    s.setId(entity.id);
                    s.setCustom(true);

                    if (entity.de22 != null)
                        s.setField(22, entity.de22);
                    if (entity.amount != null)
                        s.setField(4, entity.amount);
                    if (entity.pan != null)
                        s.setField(2, entity.pan);
                    if (entity.expiry != null)
                        s.setField(14, entity.expiry);
                    if (entity.track2 != null)
                        s.setField(35, entity.track2);

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
            // ... existing delete logic ...
            if (scenario.isCustom()) {
                scenario.setSelected(!scenario.isSelected());
                adapter.notifyDataSetChanged();
            } else {
                Toast.makeText(this, "Cannot delete built-in scenarios", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if ("MULTI".equals(perfMode)) {
            if (selectionMode) {
                configureScenario(scenario);
            } else {
                Toast.makeText(this, "Long press to select test cases.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Single mode — tap to configure & select, or deselect
        if (scenario.isSelected()) {
            scenario.setSelected(false);
            adapter.notifyDataSetChanged();
            updateRunAllButton();
        } else if (!configuringInProgress) {
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

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_edit_test_case, null);
        builder.setView(view);
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        // styling

        // Bind Views
        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        tvTitle.setText(isEdit ? "Edit Test Case" : "New Test Case");

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

        // Pre-fill data
        if (isEdit) {
            etName.setText(existing.getDescription());
            etDe22.setText(existing.getField(22));
            etAmount.setText(existing.getField(4));
            etPan.setText(existing.getField(2));
            etExpiry.setText(existing.getField(14));
            etTrack2.setText(existing.getField(35));
        } else {
            etDe22.setText("051"); // Default to Chip/Mag
            etAmount.setText("100000");
        }

        // DE22 Watcher for Visibility
        Runnable updateVis = () -> {
            String code = etDe22.getText().toString();
            boolean isManual = code.startsWith("01");
            boolean isMag = code.startsWith("02") || code.startsWith("05") || code.startsWith("07")
                    || code.startsWith("91");

            layoutManual.setVisibility(isManual ? View.VISIBLE : View.GONE);
            layoutTrack2.setVisibility(isMag ? View.VISIBLE : View.GONE);
        };
        etDe22.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateVis.run();
            }

            public void afterTextChanged(android.text.Editable s) {
            }
        });
        updateVis.run(); // Init

        // Save Action
        btnSave.setOnClickListener(v -> {
            boolean valid = true;

            // --- Name (Required) ---
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                tilName.setError("Required");
                valid = false;
            } else {
                tilName.setError(null);
            }

            // --- DE 22 (NapasFieldSpecConfig: 3 numeric digits) ---
            String de22 = etDe22.getText().toString().trim();
            NapasFieldSpecConfig.FieldSpec de22Spec = NapasFieldSpecConfig.get(22);
            if (de22Spec != null && de22Spec.pattern != null && !de22Spec.pattern.matcher(de22).matches()) {
                etDe22.setError(de22Spec.description);
                valid = false;
            } else {
                etDe22.setError(null);
            }

            // --- Amount (10,000 → 500,000,000 VND) ---
            String amount = etAmount.getText().toString().trim();
            if (!amount.isEmpty()) {
                try {
                    long amountVal = Long.parseLong(amount);
                    if (amountVal < 10000 || amountVal > 500000000) {
                        etAmount.setError("Amount: 10,000 → 500,000,000 VND");
                        valid = false;
                    } else {
                        etAmount.setError(null);
                    }
                } catch (NumberFormatException e) {
                    etAmount.setError("Must be numeric");
                    valid = false;
                }
            }

            // --- PAN (Luhn + NapasFieldSpecConfig DE 2) ---
            if (layoutManual.getVisibility() == View.VISIBLE) {
                String pan = etPan.getText().toString().trim();
                NapasFieldSpecConfig.FieldSpec panSpec = NapasFieldSpecConfig.get(2);
                if (pan.isEmpty()) {
                    tilPan.setError("Required");
                    valid = false;
                } else if (pan.length() < 13 || pan.length() > 19) {
                    tilPan.setError("PAN length: 13-19 digits");
                    valid = false;
                } else if (panSpec != null && panSpec.pattern != null && !panSpec.pattern.matcher(pan).matches()) {
                    tilPan.setError(panSpec.description);
                    valid = false;
                } else if (!checkLuhn(pan)) {
                    tilPan.setError("Luhn checksum failed");
                    valid = false;
                } else {
                    tilPan.setError(null);
                }

                // --- Expiry (YYMM, must not be expired) ---
                String exp = etExpiry.getText().toString().trim();
                NapasFieldSpecConfig.FieldSpec expirySpec = NapasFieldSpecConfig.get(14);
                if (exp.isEmpty()) {
                    tilExpiry.setError("Required");
                    valid = false;
                } else if (expirySpec != null && expirySpec.pattern != null
                        && !expirySpec.pattern.matcher(exp).matches()) {
                    tilExpiry.setError(expirySpec.description);
                    valid = false;
                } else {
                    // Check expiry against current date (YYMM format)
                    try {
                        int yy = Integer.parseInt(exp.substring(0, 2));
                        int mm = Integer.parseInt(exp.substring(2, 4));
                        if (mm < 1 || mm > 12) {
                            tilExpiry.setError("Invalid month (01-12)");
                            valid = false;
                        } else {
                            java.util.Calendar now = java.util.Calendar.getInstance();
                            int currentYear = now.get(java.util.Calendar.YEAR) % 100; // 2-digit
                            int currentMonth = now.get(java.util.Calendar.MONTH) + 1; // 1-based
                            if (yy < currentYear || (yy == currentYear && mm < currentMonth)) {
                                tilExpiry.setError("Card expired");
                                valid = false;
                            } else {
                                tilExpiry.setError(null);
                            }
                        }
                    } catch (NumberFormatException e) {
                        tilExpiry.setError("Invalid format (YYMM)");
                        valid = false;
                    }
                }
            }

            // --- Track 2 (NapasFieldSpecConfig DE 35) ---
            if (layoutTrack2.getVisibility() == View.VISIBLE) {
                String t2 = etTrack2.getText().toString().trim();
                NapasFieldSpecConfig.FieldSpec t2Spec = NapasFieldSpecConfig.get(35);
                if (t2.isEmpty()) {
                    tilTrack2.setError("Required");
                    valid = false;
                } else if (t2Spec != null && t2Spec.pattern != null && !t2Spec.pattern.matcher(t2).matches()) {
                    tilTrack2.setError(t2Spec.description);
                    valid = false;
                } else {
                    tilTrack2.setError(null);
                }
            }

            if (!valid)
                return;

            // Save
            executor.execute(() -> {
                com.example.mysoftpos.data.local.entity.TestCaseEntity entity = new com.example.mysoftpos.data.local.entity.TestCaseEntity();
                if (isEdit)
                    entity.id = existing.getId();
                entity.suiteId = -1;
                entity.name = name;
                entity.transactionType = txnType;
                entity.scheme = scheme;
                entity.timestamp = System.currentTimeMillis();
                entity.status = "NEW";

                entity.de22 = de22;
                entity.amount = amount;

                if (layoutManual.getVisibility() == View.VISIBLE) {
                    entity.pan = etPan.getText().toString();
                    entity.expiry = etExpiry.getText().toString();
                }
                if (layoutTrack2.getVisibility() == View.VISIBLE) {
                    entity.track2 = etTrack2.getText().toString();
                }

                if (isEdit)
                    repository.update(entity);
                else
                    repository.insert(entity);

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
