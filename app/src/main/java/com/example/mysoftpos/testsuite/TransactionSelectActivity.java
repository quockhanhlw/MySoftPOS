package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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

public class TransactionSelectActivity extends AppCompatActivity {

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
        setContentView(R.layout.activity_transaction_select);

        scheme = getIntent().getStringExtra(IntentKeys.SCHEME);
        channel = getIntent().getStringExtra(IntentKeys.CHANNEL);

        btnRunSelected = findViewById(R.id.btnRunSelected);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnPurchase).setOnClickListener(v -> {
            Intent i = new Intent(this, TestSuiteActivity.class);
            i.putExtra(IntentKeys.SCHEME, scheme);
            i.putExtra(IntentKeys.CHANNEL, channel);
            i.putExtra(IntentKeys.TXN_TYPE, "PURCHASE");
            if (!purchaseSelected.isEmpty()) {
                i.putExtra(IntentKeys.SELECTED_SCENARIOS, purchaseSelected);
            }
            purchaseLauncher.launch(i);
        });

        findViewById(R.id.btnBalance).setOnClickListener(v -> {
            Intent i = new Intent(this, TestSuiteActivity.class);
            i.putExtra(IntentKeys.SCHEME, scheme);
            i.putExtra(IntentKeys.CHANNEL, channel);
            i.putExtra(IntentKeys.TXN_TYPE, "BALANCE");
            if (!balanceSelected.isEmpty()) {
                i.putExtra(IntentKeys.SELECTED_SCENARIOS, balanceSelected);
            }
            balanceLauncher.launch(i);
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
        if (total > 0) {
            btnRunSelected.setVisibility(View.VISIBLE);
            StringBuilder label = new StringBuilder("Run Selected (");
            if (!purchaseSelected.isEmpty()) {
                label.append(purchaseSelected.size()).append(" Purchase");
            }
            if (!purchaseSelected.isEmpty() && !balanceSelected.isEmpty()) {
                label.append(" + ");
            }
            if (!balanceSelected.isEmpty()) {
                label.append(balanceSelected.size()).append(" Balance");
            }
            label.append(")");
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
            Toast.makeText(this, "No test cases selected", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent i = new Intent(this, BatchRunnerActivity.class);
        i.putExtra(IntentKeys.SELECTED_SCENARIOS, all);
        i.putExtra(IntentKeys.SCHEME, scheme);
        startActivity(i);
    }
}
