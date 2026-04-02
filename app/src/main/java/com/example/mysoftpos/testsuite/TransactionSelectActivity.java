package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.example.mysoftpos.R;
import com.example.mysoftpos.testsuite.model.TestScenario;
import com.example.mysoftpos.ui.BaseActivity;
import com.example.mysoftpos.utils.IntentKeys;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

public class TransactionSelectActivity extends BaseActivity {

    private String scheme;
    private String channel;

    private final ArrayList<TestScenario> purchaseSelected = new ArrayList<>();
    private final ArrayList<TestScenario> balanceSelected = new ArrayList<>();

    private MaterialButton btnRunSelected;
    private TextView tvPurchaseSubtitle;
    private TextView tvBalanceSubtitle;

    private final ActivityResultLauncher<Intent> purchaseLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<TestScenario> selected = (ArrayList<TestScenario>) result.getData()
                            .getSerializableExtra(IntentKeys.SELECTED_SCENARIOS);
                    if (selected != null) {
                        purchaseSelected.clear();
                        purchaseSelected.addAll(selected);
                    }
                }
                updateUI();
            });

    private final ActivityResultLauncher<Intent> balanceLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<TestScenario> selected = (ArrayList<TestScenario>) result.getData()
                            .getSerializableExtra(IntentKeys.SELECTED_SCENARIOS);
                    if (selected != null) {
                        balanceSelected.clear();
                        balanceSelected.addAll(selected);
                    }
                }
                updateUI();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_transaction_select);
        } catch (Exception ex) {
            Toast.makeText(this,
                    getString(R.string.common_error_with_reason, ex.getMessage()),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        scheme = getIntent().getStringExtra(IntentKeys.SCHEME);
        channel = getIntent().getStringExtra(IntentKeys.CHANNEL);
        if (scheme == null || scheme.trim().isEmpty()) {
            scheme = getString(R.string.txn_select_default_scheme);
        }
        if (channel == null || channel.trim().isEmpty()) {
            channel = getString(R.string.txn_select_default_channel);
        }

        btnRunSelected = findViewById(R.id.btnRunSelected);
        tvPurchaseSubtitle = findViewById(R.id.tvPurchaseSubtitle);
        tvBalanceSubtitle = findViewById(R.id.tvBalanceSubtitle);
        View btnBack = findViewById(R.id.btnBack);
        View btnPurchase = findViewById(R.id.btnPurchase);
        View btnBalance = findViewById(R.id.btnBalance);

        if (btnRunSelected == null || btnBack == null || btnPurchase == null || btnBalance == null) {
            Toast.makeText(this,
                    getString(R.string.common_error_with_reason, "Invalid transaction select layout"),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Breadcrumbs
        TextView tvBreadcrumbScheme = findViewById(R.id.tvBreadcrumbScheme);
        TextView tvBreadcrumbChannel = findViewById(R.id.tvBreadcrumbChannel);
        if (tvBreadcrumbScheme != null && scheme != null)
            tvBreadcrumbScheme.setText(scheme);
        if (tvBreadcrumbChannel != null && channel != null)
            tvBreadcrumbChannel.setText(channel);

        // Swipe back
        com.example.mysoftpos.testsuite.util.SwipeBackHelper.attach(this);
        com.example.mysoftpos.testsuite.util.StepDotsHelper.setActiveStep(this, 3);

        btnBack.setOnClickListener(v -> finish());

        btnPurchase.setOnClickListener(v -> {
            try {
                Intent i = new Intent(this, TestSuiteActivity.class);
                i.putExtra(IntentKeys.SCHEME, scheme);
                i.putExtra(IntentKeys.CHANNEL, channel);
                i.putExtra(IntentKeys.TXN_TYPE, "PURCHASE");
                if (!purchaseSelected.isEmpty()) {
                    i.putExtra(IntentKeys.SELECTED_SCENARIOS, purchaseSelected);
                }
                purchaseLauncher.launch(i);
            } catch (Exception ex) {
                android.util.Log.e("TransactionSelect", "Failed to launch TestSuiteActivity: " + ex.getMessage(), ex);
                Toast.makeText(this,
                        getString(R.string.common_error_with_reason, "Launch error: " + ex.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
        });

        btnBalance.setOnClickListener(v -> {
            try {
                Intent i = new Intent(this, TestSuiteActivity.class);
                i.putExtra(IntentKeys.SCHEME, scheme);
                i.putExtra(IntentKeys.CHANNEL, channel);
                i.putExtra(IntentKeys.TXN_TYPE, "BALANCE");
                if (!balanceSelected.isEmpty()) {
                    i.putExtra(IntentKeys.SELECTED_SCENARIOS, balanceSelected);
                }
                balanceLauncher.launch(i);
            } catch (Exception ex) {
                android.util.Log.e("TransactionSelect", "Failed to launch TestSuiteActivity: " + ex.getMessage(), ex);
                Toast.makeText(this,
                        getString(R.string.common_error_with_reason, "Launch error: " + ex.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
        });

        btnRunSelected.setOnClickListener(v -> runAllSelected());

        // Find subtitle TextViews for badge display
        View purchaseCard = findViewById(R.id.btnPurchase);
        View balanceCard = findViewById(R.id.btnBalance);
        // The subtitle is the second TextView inside the card's inner LinearLayout
        // We'll find them by tag or just update text on the card itself
        // For simplicity, we update the card subtitle dynamically

        updateUI();
    }

    private void updateUI() {
        int total = purchaseSelected.size() + balanceSelected.size();

        // Update subtitles
        if (tvPurchaseSubtitle != null) {
            tvPurchaseSubtitle.setText(purchaseSelected.isEmpty()
                    ? getString(R.string.txn_select_purchase_subtitle)
                    : getString(R.string.txn_select_cases_selected_format, purchaseSelected.size()));
        }
        if (tvBalanceSubtitle != null) {
            tvBalanceSubtitle.setText(balanceSelected.isEmpty()
                    ? getString(R.string.txn_select_balance_subtitle)
                    : getString(R.string.txn_select_cases_selected_format, balanceSelected.size()));
        }

        // Update count badges
        TextView tvPurchaseCount = findViewById(R.id.tvPurchaseCount);
        TextView tvBalanceCount = findViewById(R.id.tvBalanceCount);
        if (tvPurchaseCount != null) {
            if (!purchaseSelected.isEmpty()) {
                tvPurchaseCount.setVisibility(View.VISIBLE);
                tvPurchaseCount.setText(purchaseSelected.size() + "");
            } else {
                tvPurchaseCount.setVisibility(View.GONE);
            }
        }
        if (tvBalanceCount != null) {
            if (!balanceSelected.isEmpty()) {
                tvBalanceCount.setVisibility(View.VISIBLE);
                tvBalanceCount.setText(balanceSelected.size() + "");
            } else {
                tvBalanceCount.setVisibility(View.GONE);
            }
        }

        if (total > 0) {
            btnRunSelected.setVisibility(View.VISIBLE);
            StringBuilder label = new StringBuilder(getString(R.string.txn_select_run_selected_prefix));
            if (!purchaseSelected.isEmpty()) {
                label.append(getString(R.string.txn_select_run_selected_purchase_part, purchaseSelected.size()));
            }
            if (!purchaseSelected.isEmpty() && !balanceSelected.isEmpty()) {
                label.append(getString(R.string.txn_select_run_selected_separator));
            }
            if (!balanceSelected.isEmpty()) {
                label.append(getString(R.string.txn_select_run_selected_balance_part, balanceSelected.size()));
            }
            label.append(getString(R.string.txn_select_run_selected_suffix));
            btnRunSelected.setText(label.toString());
        } else {
            btnRunSelected.setVisibility(View.GONE);
        }
    }

    private void runAllSelected() {
        ArrayList<TestScenario> all = new ArrayList<>();
        all.addAll(purchaseSelected);
        all.addAll(balanceSelected);

        if (all.isEmpty()) {
            Toast.makeText(this, R.string.testsuite_no_case_selected, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent i = new Intent(this, BatchRunnerActivity.class);
        i.putExtra(IntentKeys.SELECTED_SCENARIOS, all);
        i.putExtra(IntentKeys.SCHEME, scheme);
        startActivity(i);
    }
}
