package com.example.mysoftpos.testsuite;

import com.example.mysoftpos.utils.config.ConfigManager;
import com.example.mysoftpos.iso8583.TxnType;

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
    private List<TestScenario> allScenarios;
    private List<TestScenario> displayedScenarios;
    private com.example.mysoftpos.testsuite.adapter.TestScenarioAdapter adapter;

    // Multi-thread UI
    private View layoutSelectAll;
    private CheckBox cbSelectAll;
    private MaterialButton btnRunAll;

    // Queue for sequential card/PIN config in "Select All"
    private int selectAllIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_suite);

        channel = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.CHANNEL);
        txnType = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.TXN_TYPE);
        perfMode = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.PERF_MODE);
        if (perfMode == null)
            perfMode = "SINGLE";

        boolean isMulti = "MULTI".equals(perfMode);

        TextView tvTitle = findViewById(R.id.tvTitle);

        // Bind Summary Card
        TextView tvSummaryScheme = findViewById(R.id.tvSummaryScheme);
        TextView tvSummaryChannel = findViewById(R.id.tvSummaryChannel);
        TextView tvSummaryType = findViewById(R.id.tvSummaryType);

        String scheme = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.SCHEME);
        if (scheme == null)
            scheme = "Napas";

        tvSummaryScheme.setText(scheme);
        tvSummaryChannel.setText(channel);
        tvSummaryType.setText(txnType);

        androidx.recyclerview.widget.RecyclerView recyclerView = findViewById(R.id.recyclerViewCases);

        // Load Data
        allScenarios = TestDataProvider.generateAllScenarios(this);
        displayedScenarios = filterScenarios(allScenarios, channel, txnType);

        adapter = new com.example.mysoftpos.testsuite.adapter.TestScenarioAdapter(
                this::onScenarioClicked);

        adapter.setScenarios(displayedScenarios);
        adapter.setMultiMode(isMulti);

        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Multi-thread controls
        layoutSelectAll = findViewById(R.id.layoutSelectAll);
        cbSelectAll = findViewById(R.id.cbSelectAll);
        btnRunAll = findViewById(R.id.btnRunAll);

        if (isMulti) {
            layoutSelectAll.setVisibility(View.VISIBLE);
            btnRunAll.setVisibility(View.VISIBLE);
            btnRunAll.setText("Done");

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

            btnRunAll.setOnClickListener(v -> returnSelectedScenarios());
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private List<TestScenario> filterScenarios(List<TestScenario> src, String channel, String type) {
        return src;
    }

    private void onScenarioClicked(TestScenario scenario) {
        if ("MULTI".equals(perfMode)) {
            configureForMultiMode(scenario);
        } else {
            openRunnerSingle(scenario);
        }
    }

    // ========================
    // SINGLE-THREAD FLOW (existing)
    // ========================

    private void openRunnerSingle(TestScenario scenario) {
        String de22 = scenario.getField(22);
        if ("011".equals(de22) || "012".equals(de22)) {
            showPanSelectionDialog(scenario, () -> checkPinAndLaunch(scenario));
        } else {
            showCardSelectionDialog(scenario, () -> checkPinAndLaunch(scenario));
        }
    }

    private void applyPanData(TestScenario scenario, String pan) {
        scenario.setField(2, pan);
        scenario.setField(14, "3101");
        scenario.setField(35, null);
    }

    private void showPanSelectionDialog(TestScenario scenario, Runnable onDone) {
        final String[] panOptions = {
                "9704166606226219923",
                "9704306669144645257",
                "9704189991010867647"
        };

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Select Card (PAN)");

        builder.setSingleChoiceItems(panOptions, -1, (dialog, which) -> {
            String selected = panOptions[which];
            applyPanData(scenario, selected);
            dialog.dismiss();
            onDone.run();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void showCardSelectionDialog(TestScenario scenario, Runnable onDone) {
        final String[] cardOptions = {
                "9704166606226219923=31016010000000123",
                "9704306669144645257=31016010000000123",
                "9704189991010867647=31016010000000123"
        };

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Select Card");

        builder.setSingleChoiceItems(cardOptions, -1, (dialog, which) -> {
            String selected = cardOptions[which];
            applyCardData(scenario, selected);
            dialog.dismiss();
            onDone.run();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
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
            showPinDialog(scenario, pin -> launchRunner(scenario, pin));
        } else {
            launchRunner(scenario, null);
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
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

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
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.DE22, scenario.getField(22));
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.DESC, scenario.getDescription());
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.CHANNEL, channel);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.TXN_TYPE, txnType);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.TRACK2, scenario.getField(35));
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.PAN, scenario.getField(2));
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.EXPIRY, scenario.getField(14));

        if (userPin != null && !userPin.isEmpty()) {
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.PIN_BLOCK, userPin);
        } else {
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.PIN_BLOCK, scenario.getField(52));
        }

        startActivity(intent);
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
