package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.entity.TestCaseEntity;
import com.example.mysoftpos.testsuite.adapter.TestCaseAdapter;
import com.example.mysoftpos.testsuite.viewmodel.DynamicTestCaseViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class DynamicTestCaseActivity extends AppCompatActivity {

    private DynamicTestCaseViewModel viewModel;
    private TestCaseAdapter adapter;
    private long suiteId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dynamic_test_case);

        String suiteName = getIntent().getStringExtra("SUITE_NAME");
        suiteId = getIntent().getLongExtra("SUITE_ID", -1);

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(suiteName);

        RecyclerView recyclerView = findViewById(R.id.recyclerViewCases);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new TestCaseAdapter(this::openCaseDetails);
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(DynamicTestCaseViewModel.class);
        viewModel.setSuiteId(suiteId);
        viewModel.getCasesForSuite().observe(this, adapter::setCases);

        FloatingActionButton fab = findViewById(R.id.fabAddCase);
        fab.setOnClickListener(v -> showAddCaseDialog());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void openCaseDetails(TestCaseEntity testCase) {
        // TODO: Implement Case Details (Future Step)
        // For now, maybe just rerun?
    }

    private void showAddCaseDialog() {
        // TODO: Implement selecting a Template to create a Case (Future Step)
        // Need to query TemplateRepository first
    }
}





