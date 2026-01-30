package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.testsuite.adapter.TestSuiteAdapter;
import com.example.mysoftpos.testsuite.adapter.TestCaseAdapter;
import com.example.mysoftpos.testsuite.data.TestSuiteDao;
import com.example.mysoftpos.testsuite.model.TestSuite;
import com.example.mysoftpos.testsuite.model.TestCase;
import com.example.mysoftpos.testsuite.model.TestScenario;
import com.example.mysoftpos.testsuite.viewmodel.TestSuiteViewModel;
import com.example.mysoftpos.utils.AppExecutors;

import java.util.List;
import java.util.ArrayList;

public class TestSuiteActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TestSuiteDao dao;
    private TestSuiteViewModel viewModel;

    // Filters
    private String scheme, channel, txnType;
    private boolean isFilteredMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_suite_list);

        dao = AppDatabase.getInstance(this).testSuiteDao();
        viewModel = new ViewModelProvider(this).get(TestSuiteViewModel.class);

        scheme = getIntent().getStringExtra("SCHEME");
        channel = getIntent().getStringExtra("CHANNEL");
        txnType = getIntent().getStringExtra("TXN_TYPE");
        isFilteredMode = (channel != null);

        initViews();
        observeViewModel();

        viewModel.initData(channel, "0200", resolveProcCode());
    }

    private void observeViewModel() {
        viewModel.getTestScenarios().observe(this,
                (List<com.example.mysoftpos.testsuite.model.TestScenario> scenarios) -> {
                    RecyclerView.Adapter adapter = recyclerView.getAdapter();
                    if (adapter instanceof TestCaseAdapter) {
                        ((TestCaseAdapter) adapter).setData(scenarios);
                    }
                });
    }

    private String resolveProcCode() {
        if ("ATM".equals(channel)) {
            if ("PURCHASE".equals(txnType))
                return "010000";
            else if ("BALANCE".equals(txnType))
                return "310000";
        } else {
            if ("BALANCE".equals(txnType))
                return "310000";
        }
        return "000000";
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        TextView tvTitle = findViewById(R.id.tvTitle);
        if (isFilteredMode && tvTitle != null) {
            tvTitle.setText(channel + " " + txnType + " - Cases");
        }

        findViewById(R.id.fabAdd).setOnClickListener(v -> {
            if (isFilteredMode) {
                Intent intent = new Intent(this, TestCaseEditorActivity.class);
                startActivity(intent);
            } else {
                createDefaultSuite();
            }
        });

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        if (isFilteredMode) {
            TestCaseAdapter adapter = new TestCaseAdapter();
            adapter.setOnItemClickListener(testCase -> {
                Intent intent = new Intent(this, RunnerActivity.class);
                startActivity(intent);
            });
            recyclerView.setAdapter(adapter);
        } else {
            TestSuiteAdapter adapter = new TestSuiteAdapter();
            adapter.setOnItemClickListener(suite -> {
                Intent intent = new Intent(this, TestSuiteDetailActivity.class);
                intent.putExtra("SUITE_ID", suite.id);
                startActivity(intent);
            });
            recyclerView.setAdapter(adapter);
            loadTestSuiteData();
        }
    }

    private void loadTestSuiteData() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<TestSuite> list = dao.getAllTestSuites();
            runOnUiThread(() -> {
                RecyclerView.Adapter adapter = recyclerView.getAdapter();
                if (adapter instanceof TestSuiteAdapter) {
                    ((TestSuiteAdapter) adapter).setData(list);
                }
            });
        });
    }

    private void createDefaultSuite() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            TestSuite suite = new TestSuite("New Suite " + System.currentTimeMillis() % 1000, "Created manually",
                    System.currentTimeMillis());
            dao.insertTestSuite(suite);
            loadTestSuiteData();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFilteredMode) {
            viewModel.initData(channel, "0200", resolveProcCode());
        } else {
            loadTestSuiteData();
        }
    }
}
