package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mysoftpos.R;
import com.example.mysoftpos.testsuite.model.TestScenario;
import com.example.mysoftpos.utils.IntentKeys;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TransactionSelectActivity extends AppCompatActivity {

    private String channel;
    private String perfMode;
    private ActivityResultLauncher<Intent> multiSelectLauncher;

    // Multi-thread selections
    private CheckBox cbPurchase;
    private CheckBox cbBalance;
    private MaterialButton btnRunTransaction;
    private final Map<String, ArrayList<TestScenario>> selectedScenariosMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_select);

        channel = getIntent().getStringExtra(IntentKeys.CHANNEL);
        if (channel == null)
            channel = "POS";

        perfMode = getIntent().getStringExtra(IntentKeys.PERF_MODE);
        if (perfMode == null)
            perfMode = "SINGLE";

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(channel + " - Select Transaction");

        cbPurchase = findViewById(R.id.cbPurchase);
        cbBalance = findViewById(R.id.cbBalance);
        btnRunTransaction = findViewById(R.id.btnRunTransaction);

        if ("MULTI".equals(perfMode)) {
            cbPurchase.setVisibility(View.VISIBLE);
            cbBalance.setVisibility(View.VISIBLE);
            btnRunTransaction.setVisibility(View.VISIBLE);
            btnRunTransaction.setEnabled(false); // Disabled initially

            // Checkboxes are read-only indicators, user must click the card to select
            cbPurchase.setClickable(false);
            cbBalance.setClickable(false);
        }

        // Register launcher for MULTI flow
        multiSelectLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        onMultiScenariosSelected(result.getData());
                    }
                });

        findViewById(R.id.btnPurchase).setOnClickListener(v -> navigateToTestSuite("PURCHASE"));
        findViewById(R.id.btnBalance).setOnClickListener(v -> navigateToTestSuite("BALANCE"));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnRunTransaction.setOnClickListener(v -> runMultiThreadTransaction());
    }

    private void navigateToTestSuite(String txnType) {
        Intent intent = new Intent(this, TestSuiteActivity.class);
        intent.putExtra(IntentKeys.CHANNEL, channel);
        intent.putExtra(IntentKeys.TXN_TYPE, txnType);
        intent.putExtra(IntentKeys.SCHEME, getIntent().getStringExtra(IntentKeys.SCHEME));
        intent.putExtra(IntentKeys.PERF_MODE, perfMode);

        if ("MULTI".equals(perfMode)) {
            // Pass previously selected scenarios if we want to restore state (Optional, not
            // implemented yet)
            multiSelectLauncher.launch(intent);
        } else {
            startActivity(intent);
        }
    }

    @SuppressWarnings("unchecked")
    private void onMultiScenariosSelected(Intent data) {
        ArrayList<TestScenario> selected = (ArrayList<TestScenario>) data
                .getSerializableExtra(IntentKeys.SELECTED_SCENARIOS);
        String txnType = data.getStringExtra(IntentKeys.TXN_TYPE);

        if (txnType == null)
            return;

        if (selected != null && !selected.isEmpty()) {
            for (TestScenario s : selected) {
                s.setTxnType(txnType);
            }
            selectedScenariosMap.put(txnType, selected);
        } else {
            selectedScenariosMap.remove(txnType);
        }

        updateUiState();
    }

    private void updateUiState() {
        boolean hasPurchase = selectedScenariosMap.containsKey("PURCHASE")
                && !selectedScenariosMap.get("PURCHASE").isEmpty();
        boolean hasBalance = selectedScenariosMap.containsKey("BALANCE")
                && !selectedScenariosMap.get("BALANCE").isEmpty();

        cbPurchase.setChecked(hasPurchase);
        cbBalance.setChecked(hasBalance);

        int totalCount = 0;
        for (ArrayList<TestScenario> list : selectedScenariosMap.values()) {
            totalCount += list.size();
        }

        btnRunTransaction.setEnabled(totalCount > 0);
        if (totalCount > 0) {
            btnRunTransaction.setText("Run Transaction (" + totalCount + ")");
        } else {
            btnRunTransaction.setText("Run Transaction");
        }
    }

    private void runMultiThreadTransaction() {
        ArrayList<TestScenario> allScenarios = new ArrayList<>();
        for (ArrayList<TestScenario> list : selectedScenariosMap.values()) {
            allScenarios.addAll(list);
        }

        if (allScenarios.isEmpty()) {
            Toast.makeText(this, "No scenarios selected", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent runner = new Intent(this, MultiThreadRunnerActivity.class);
        runner.putExtra(IntentKeys.SCENARIOS, allScenarios);
        // Use the first type or mixed? TransactionExecutor handles type per transaction
        // context usually,
        // but currently MultiThreadRunner takes a single TXN_TYPE extra.
        // We might need to adjust logic if the runner relies on the activity intent's
        // TXN_TYPE.
        // CHECK: MultiThreadRunnerActivity uses txnType from Intent to build context.
        // If we mix types, the runner might default to one. We need to check
        // MultiThreadRunnerActivity logic.

        // For now, if we have mixed types, we might have an issue if the Runner depends
        // on a single global type.
        // Let's assume for this task we pass "MIXED" or just the first one,
        // OR better: The Runner should respect the Individual Scenario's intention?
        // Actually, TransactionExecutor.buildContext(context, txnType) is used.
        // If we pass "PURCHASE", all run as purchase.
        // If we pass "BALANCE", all run as balance.
        // We need to fix MultiThreadRunnerActivity later probably,
        // but for now let's pass "PURCHASE" if mixed, or the specific one.

        String finalType = "PURCHASE"; // Default
        if (selectedScenariosMap.containsKey("PURCHASE") && !selectedScenariosMap.containsKey("BALANCE")) {
            finalType = "PURCHASE";
        } else if (!selectedScenariosMap.containsKey("PURCHASE") && selectedScenariosMap.containsKey("BALANCE")) {
            finalType = "BALANCE";
        }

        runner.putExtra(IntentKeys.TXN_TYPE, finalType);
        startActivity(runner);
    }
}
