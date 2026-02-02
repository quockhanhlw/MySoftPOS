package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mysoftpos.R;
import com.example.mysoftpos.testsuite.model.TestScenario;
import java.util.List;
import java.util.ArrayList;

public class TestSuiteActivity extends AppCompatActivity {

    private String channel;
    private String txnType;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<TestScenario> allScenarios;
    private List<TestScenario> displayedScenarios;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_suite); // Need to create this layout

        channel = getIntent().getStringExtra("CHANNEL");
        txnType = getIntent().getStringExtra("TXN_TYPE");

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(String.format("%s - %s Tests", channel, txnType));

        listView = findViewById(R.id.listViewCases);

        // Load Data
        allScenarios = TestDataProvider.generateAllScenarios();

        // Filter Data (For now, show all, or implement filtering logic if needed)
        // Since user request specific DE22 list for "Purchase", and "TransactionSelect"
        // passed "PURCHASE" or "BALANCE"
        // We can filter here.
        displayedScenarios = filterScenarios(allScenarios, channel, txnType);

        // Adapter
        List<String> titles = new ArrayList<>();
        for (TestScenario s : displayedScenarios) {
            titles.add(s.getDescription());
        }

        adapter = new ArrayAdapter<String>(this, R.layout.item_test_scenario, R.id.tvScenarioTitle, titles);
        listView.setAdapter(adapter);

        // Click Listener
        listView.setOnItemClickListener((parent, view, position, id) -> {
            TestScenario selected = displayedScenarios.get(position);
            openRunner(selected);
        });

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
        Intent intent = new Intent(this, RunnerActivity.class);

        // Pass strictly generated logic fields
        intent.putExtra("DE_22", scenario.getField(22));
        intent.putExtra("DESC", scenario.getDescription());
        intent.putExtra("CHANNEL", channel);
        intent.putExtra("TXN_TYPE", txnType);

        // Critical Data
        intent.putExtra("TRACK2", scenario.getField(35));
        intent.putExtra("DE55", scenario.getField(55));
        intent.putExtra("PAN", scenario.getField(2));
        intent.putExtra("EXPIRY", scenario.getField(14));
        intent.putExtra("PIN_BLOCK", scenario.getField(52)); // Pass PIN marker

        startActivity(intent);
    }
}
