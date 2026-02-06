package com.example.mysoftpos.testsuite;

import com.example.mysoftpos.utils.config.ConfigManager;
import com.example.mysoftpos.iso8583.TxnType;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mysoftpos.R;
import com.example.mysoftpos.testsuite.model.TestScenario;
import java.util.List;
import java.util.ArrayList;

public class TestSuiteActivity extends AppCompatActivity {

    private String channel;
    private String txnType;
    // private ListView listView; // Removed
    // private ArrayAdapter<String> adapter; // Removed
    private List<TestScenario> allScenarios;
    private List<TestScenario> displayedScenarios;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_suite); // Need to create this layout

        channel = getIntent().getStringExtra("CHANNEL");
        txnType = getIntent().getStringExtra("TXN_TYPE");

        TextView tvTitle = findViewById(R.id.tvTitle);
        // title kept as "Test Scenarios" or generic

        // Bind Summary Card
        TextView tvSummaryScheme = findViewById(R.id.tvSummaryScheme);
        TextView tvSummaryChannel = findViewById(R.id.tvSummaryChannel);
        TextView tvSummaryType = findViewById(R.id.tvSummaryType);

        String scheme = getIntent().getStringExtra("SCHEME");
        if (scheme == null)
            scheme = "Napas"; // Fallback

        tvSummaryScheme.setText(scheme);
        tvSummaryChannel.setText(channel);
        tvSummaryType.setText(txnType);

        androidx.recyclerview.widget.RecyclerView recyclerView = findViewById(R.id.recyclerViewCases);

        // Load Data (Pass Activity Context for ConfigManager)
        allScenarios = TestDataProvider.generateAllScenarios(this);
        displayedScenarios = filterScenarios(allScenarios, channel, txnType);

        com.example.mysoftpos.testsuite.adapter.TestScenarioAdapter adapter = new com.example.mysoftpos.testsuite.adapter.TestScenarioAdapter(
                this::openRunner);

        adapter.setScenarios(displayedScenarios);

        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private List<TestScenario> filterScenarios(List<TestScenario> src, String channel, String type) {
        // Implement filtering if logic differs for ATM/POS or Purchase/Balance
        // For now, return all for Purchase, maybe empty/different for Balance
        if ("BALANCE".equals(type)) {
            // Maybe return just one generic case or allow all?
            // User instruction said: "Chọn Purchase thì hiện ra 1 danh sách các Testcase...
            // Các testcase còn lại tương ứng với 1 DE 22 riêng"
            return src;
        }
        return src;
    }

    private void openRunner(TestScenario scenario) {
        String de22 = scenario.getField(22);
        if ("011".equals(de22) || "021".equals(de22)) {
            showPinDialog(scenario);
        } else {
            launchRunner(scenario, null);
        }
    }

    private void showPinDialog(TestScenario scenario) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pin_entry, null);
        builder.setView(dialogView);
        android.app.AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // UI References
        android.widget.ImageView[] dots = new android.widget.ImageView[] {
                dialogView.findViewById(R.id.dot1),
                dialogView.findViewById(R.id.dot2),
                dialogView.findViewById(R.id.dot3),
                dialogView.findViewById(R.id.dot4),
                dialogView.findViewById(R.id.dot5),
                dialogView.findViewById(R.id.dot6)
        };
        StringBuilder pinBuilder = new StringBuilder();

        // Keypad Listener
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

        // Backspace
        dialogView.findViewById(R.id.btnBackspace).setOnClickListener(v -> {
            if (pinBuilder.length() > 0) {
                pinBuilder.deleteCharAt(pinBuilder.length() - 1);
                updateDots(dots, pinBuilder.length());
            }
        });

        // OK
        dialogView.findViewById(R.id.btnOk).setOnClickListener(v -> {
            dialog.dismiss();
            launchRunner(scenario, pinBuilder.toString());
        });

        // Cancel
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

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

    private void launchRunner(TestScenario scenario, String userPin) {
        Intent intent = new Intent(this, RunnerActivity.class);

        // Pass strictly generated logic fields
        intent.putExtra("DE_22", scenario.getField(22));
        intent.putExtra("DESC", scenario.getDescription());
        intent.putExtra("CHANNEL", channel);
        intent.putExtra("TXN_TYPE", txnType);

        // Critical Data
        intent.putExtra("TRACK2", scenario.getField(35));
        intent.putExtra("PAN", scenario.getField(2));
        intent.putExtra("EXPIRY", scenario.getField(14));

        // If user entered a PIN, pass it. Otherwise use default marker if present.
        if (userPin != null && !userPin.isEmpty()) {
            intent.putExtra("PIN_BLOCK", userPin);
        } else {
            intent.putExtra("PIN_BLOCK", scenario.getField(52));
        }

        startActivity(intent);
    }
}
