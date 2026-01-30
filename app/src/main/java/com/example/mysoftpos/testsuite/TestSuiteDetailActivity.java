package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.testsuite.adapter.TestCaseAdapter;
import com.example.mysoftpos.testsuite.data.TestSuiteDao;
import com.example.mysoftpos.testsuite.model.TestCase;
import com.example.mysoftpos.testsuite.model.TestScenario;

import com.example.mysoftpos.utils.AppExecutors;
import java.util.List;

public class TestSuiteDetailActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TestCaseAdapter adapter;
    private TestSuiteDao dao;
    private long suiteId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_suite_detail);

        suiteId = getIntent().getLongExtra("SUITE_ID", -1);
        dao = AppDatabase.getInstance(this).testSuiteDao();

        initViews();
        if (suiteId != -1)
            loadData();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.fabAddCase).setOnClickListener(v -> {
            Intent intent = new Intent(this, TestCaseEditorActivity.class);
            intent.putExtra("SUITE_ID", suiteId);
            startActivity(intent);
        });

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TestCaseAdapter();
        recyclerView.setAdapter(adapter);
    }

    private void loadData() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<TestCase> cases = dao.getTestCasesForSuite(suiteId);
            java.util.List<TestScenario> scenarios = new java.util.ArrayList<>();
            for (TestCase tc : cases) {
                TestScenario s = new TestScenario(tc.getMti(), tc.getName());
                s.setId(tc.getId());
                s.setField(3, tc.getProcessingCode());
                s.setField(22, tc.getPosEntryMode());
                scenarios.add(s);
            }
            runOnUiThread(() -> adapter.setData(scenarios));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (suiteId != -1)
            loadData();
    }
}
