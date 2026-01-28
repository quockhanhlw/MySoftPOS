package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.testsuite.data.TestResultDao;
import com.example.mysoftpos.testsuite.model.TestCase;
import com.example.mysoftpos.testsuite.model.TestResult;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;

import java.util.List;
import java.util.concurrent.Executors;

/**
 * Screen 2: Test Suite Dashboard with tabs for channels and test case list.
 */
public class TestSuiteActivity extends AppCompatActivity {

    private String scheme;
    private String channel;
    private String txnType;
    private RecyclerView rvTestCases;
    // private TabLayout tabLayout; // Removed tabs
    private TestCaseAdapter adapter;
    private TestResultDao testResultDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_suite);

        scheme = getIntent().getStringExtra("SCHEME");
        channel = getIntent().getStringExtra("CHANNEL");
        txnType = getIntent().getStringExtra("TXN_TYPE");
        
        if (scheme == null) scheme = "NAPAS";
        if (channel == null) channel = "POS";

        testResultDao = AppDatabase.getInstance(this).testResultDao();

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        String title = scheme + " > " + channel;
        if (txnType != null) {
            title += " > " + (txnType.equals("PURCHASE") ? "Purchase" : "Balance");
        }
        toolbar.setTitle(title);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Tabs - HIDE/REMOVE since we filtered by Intent
        View tabLayout = findViewById(R.id.tabLayout);
        if (tabLayout != null) tabLayout.setVisibility(View.GONE);

        // RecyclerView
        rvTestCases = findViewById(R.id.rvTestCases);
        rvTestCases.setLayoutManager(new LinearLayoutManager(this));

        // Load filtered test cases
        loadFilteredTestCases();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh status indicators
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void loadFilteredTestCases() {
        List<TestCase> allCases;
        
        // 1. Filter by Channel
        if ("ATM".equals(channel)) {
            allCases = TestDataProvider.getAtmTestCases();
        } else if ("QRC".equals(channel)) {
            allCases = TestDataProvider.getQrcTestCases();
        } else {
            allCases = TestDataProvider.getPosTestCases();
        }
        
        // 2. Filter by Transaction Type (if specified)
        java.util.List<TestCase> filteredCases = new java.util.ArrayList<>();
        if (txnType != null) {
            for (TestCase tc : allCases) {
                boolean isPurchase = tc.getProcessingCode().startsWith("00");
                boolean isBalance = tc.getProcessingCode().startsWith("30");
                
                if ("PURCHASE".equals(txnType) && isPurchase) {
                    filteredCases.add(tc);
                } else if ("BALANCE".equals(txnType) && isBalance) {
                    filteredCases.add(tc);
                }
            }
        } else {
            filteredCases = allCases;
        }

        adapter = new TestCaseAdapter(filteredCases);
        rvTestCases.setAdapter(adapter);
    }

    // ═══════════════════════════════════════════════════════════════════
    //                         TEST CASE ADAPTER
    // ═══════════════════════════════════════════════════════════════════

    private class TestCaseAdapter extends RecyclerView.Adapter<TestCaseAdapter.ViewHolder> {
        private final List<TestCase> testCases;

        TestCaseAdapter(List<TestCase> testCases) {
            this.testCases = testCases;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_test_case, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TestCase tc = testCases.get(position);
            
            holder.tvTestName.setText(tc.getName());
            holder.tvDescription.setText(tc.getDescription());
            holder.tvMti.setText("MTI: " + tc.getMti());
            holder.tvDe22.setText("DE22: " + tc.getPosEntryMode());
            holder.tvProcCode.setText("PC: " + tc.getProcessingCode());
            
            // Load last result status
            Executors.newSingleThreadExecutor().execute(() -> {
                TestResult lastResult = testResultDao.getLastResultForTestCase(tc.getId());
                runOnUiThread(() -> {
                    if (lastResult != null) {
                        holder.tvLastStatus.setVisibility(View.VISIBLE);
                        holder.tvLastStatus.setText(lastResult.getStatusText());
                        
                        int bgColor = lastResult.isSuccess() ? 
                            ContextCompat.getColor(TestSuiteActivity.this, R.color.status_success) :
                            ContextCompat.getColor(TestSuiteActivity.this, R.color.status_error);
                        
                        GradientDrawable bg = new GradientDrawable();
                        bg.setColor(bgColor);
                        bg.setCornerRadius(16f);
                        holder.tvLastStatus.setBackground(bg);
                        
                        // Status dot
                        GradientDrawable dot = new GradientDrawable();
                        dot.setShape(GradientDrawable.OVAL);
                        dot.setColor(lastResult.isSuccess() ? bgColor : ContextCompat.getColor(TestSuiteActivity.this, R.color.status_error));
                        holder.viewStatus.setBackground(dot);
                    } else {
                        holder.tvLastStatus.setVisibility(View.GONE);
                        // Gray dot for not run
                        GradientDrawable dot = new GradientDrawable();
                        dot.setShape(GradientDrawable.OVAL);
                        dot.setColor(ContextCompat.getColor(TestSuiteActivity.this, R.color.text_muted));
                        holder.viewStatus.setBackground(dot);
                    }
                });
            });
            
            holder.cardTestCase.setOnClickListener(v -> {
                Intent intent = new Intent(TestSuiteActivity.this, TestRunnerActivity.class);
                intent.putExtra("TEST_CASE_ID", tc.getId());
                intent.putExtra("SCHEME", scheme);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return testCases.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView cardTestCase;
            View viewStatus;
            TextView tvTestName, tvDescription, tvMti, tvDe22, tvProcCode, tvLastStatus;

            ViewHolder(View itemView) {
                super(itemView);
                cardTestCase = itemView.findViewById(R.id.cardTestCase);
                viewStatus = itemView.findViewById(R.id.viewStatus);
                tvTestName = itemView.findViewById(R.id.tvTestName);
                tvDescription = itemView.findViewById(R.id.tvDescription);
                tvMti = itemView.findViewById(R.id.tvMti);
                tvDe22 = itemView.findViewById(R.id.tvDe22);
                tvProcCode = itemView.findViewById(R.id.tvProcCode);
                tvLastStatus = itemView.findViewById(R.id.tvLastStatus);
            }
        }
    }
}
