package com.example.mysoftpos.testsuite;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.domain.service.TransactionExecutor;
import com.example.mysoftpos.domain.service.TransactionResult;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.builder.Iso8583Builder;
import com.example.mysoftpos.iso8583.message.IsoMessage;
import com.example.mysoftpos.iso8583.util.StandardIsoPacker;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.testsuite.model.TestScenario;
import com.example.mysoftpos.testsuite.storage.SchemeRepository;
import com.example.mysoftpos.utils.IntentKeys;
import com.example.mysoftpos.utils.logging.ResponseCodeHelper;
import com.example.mysoftpos.di.ServiceLocator;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BatchRunnerActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private TransactionExecutor transactionExecutor;

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvProgress;
    private MaterialButton btnRunAll;
    private ResultAdapter adapter;

    private final List<CaseResult> results = new ArrayList<>();
    private String scheme;
    private boolean isRunning = false;
    private final java.util.concurrent.atomic.AtomicInteger completedCount = new java.util.concurrent.atomic.AtomicInteger(
            0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_batch_runner);

        transactionExecutor = ServiceLocator.getInstance(getApplication()).getTransactionExecutor();

        recyclerView = findViewById(R.id.recyclerViewResults);
        progressBar = findViewById(R.id.progressBar);
        tvProgress = findViewById(R.id.tvProgress);
        btnRunAll = findViewById(R.id.btnRunAll);
        scheme = getIntent().getStringExtra(IntentKeys.SCHEME);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Get selected scenarios
        ArrayList<TestScenario> scenarios = (ArrayList<TestScenario>) getIntent()
                .getSerializableExtra(IntentKeys.SELECTED_SCENARIOS);

        if (scenarios == null || scenarios.isEmpty()) {
            Toast.makeText(this, "No test cases provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Build results list
        for (TestScenario s : scenarios) {
            results.add(new CaseResult(s));
        }

        adapter = new ResultAdapter(results);
        recyclerView.setAdapter(adapter);

        tvProgress.setText("0/" + results.size());
        progressBar.setMax(results.size());
        progressBar.setProgress(0);

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Batch Runner (" + results.size() + " cases)");

        btnRunAll.setText("Run All (" + results.size() + ")");
        btnRunAll.setOnClickListener(v -> runAll());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void runAll() {
        if (isRunning)
            return;
        isRunning = true;
        btnRunAll.setEnabled(false);
        btnRunAll.setText("Running...");
        completedCount.set(0);

        // Reset all to running
        for (CaseResult r : results) {
            r.status = CaseStatus.RUNNING;
            r.rc = null;
            r.detail = null;
        }
        adapter.notifyDataSetChanged();

        int total = results.size();

        // Launch ALL cases in parallel
        for (int i = 0; i < total; i++) {
            final int index = i;
            final CaseResult cr = results.get(index);

            executor.execute(() -> {
                executeCase(cr);

                int done = completedCount.incrementAndGet();

                runOnUiThread(() -> {
                    adapter.notifyItemChanged(index);
                    progressBar.setProgress(done);
                    tvProgress.setText(done + "/" + total);

                    // All done?
                    if (done == total) {
                        isRunning = false;
                        btnRunAll.setEnabled(true);

                        long passed = results.stream().filter(r -> r.status == CaseStatus.PASS).count();
                        long failed = results.stream().filter(r -> r.status == CaseStatus.FAIL).count();

                        btnRunAll.setText("Done: " + passed + " Pass, " + failed + " Fail — Run Again?");
                    }
                });
            });
        }
    }

    private void executeCase(CaseResult cr) {
        TestScenario scenario = cr.scenario;
        try {
            StringBuilder sb = new StringBuilder();
            TransactionExecutor.LogCallback logger = msg -> sb.append(msg).append("\n");

            String txnType = scenario.getTxnType() != null ? scenario.getTxnType() : "PURCHASE";
            String de22 = scenario.getField(22);
            String pan = scenario.getField(2);
            String expiry = scenario.getField(14);
            String track2 = scenario.getField(35);
            String amount = scenario.getField(4);
            String pinBlock = null;
            if ("PIN_BLOCK_PRESENT".equals(scenario.getField(52))) {
                pinBlock = scenario.getUserPin() != null ? scenario.getUserPin() : "123456";
            } else if (scenario.getUserPin() != null) {
                pinBlock = scenario.getUserPin();
            }

            TransactionContext ctx = TransactionExecutor.buildContext(
                    getApplication(), txnType, amount, null, null);
            applySchemeConnection(ctx, scheme);

            CardInputData card = TransactionExecutor.prepareCard(
                    getApplication(), de22, pan, expiry, track2, pinBlock, ctx, logger);

            TransactionResult result = transactionExecutor.execute(
                    getApplication(), ctx, card, txnType, logger, "");

            cr.rc = result.rc;
            String reason = ResponseCodeHelper.getMessage(result.rc);
            if (result.approved) {
                cr.status = CaseStatus.PASS;
                cr.detail = "RC: " + result.rc + " (" + reason + ")\n" + sb;
            } else {
                cr.status = CaseStatus.FAIL;
                cr.detail = "RC: " + result.rc + " - " + reason + "\n" + sb;
            }

        } catch (java.net.SocketTimeoutException e) {
            cr.status = CaseStatus.FAIL;
            cr.rc = "TIMEOUT";
            cr.detail = "Error: Timeout waiting for response.";
        } catch (Exception e) {
            cr.status = CaseStatus.FAIL;
            cr.rc = "ERROR";
            cr.detail = "Error: " + e.getMessage();
        }
    }

    private void applySchemeConnection(TransactionContext ctx, String schemeName) {
        if (schemeName == null)
            return;
        SchemeRepository repo = new SchemeRepository(this);
        com.example.mysoftpos.testsuite.model.Scheme s = repo.getByName(schemeName);
        if (s != null && s.getServerIp() != null && !s.getServerIp().isEmpty()) {
            ctx.ip = s.getServerIp();
            ctx.port = s.getServerPort();
        }
    }

    // --- Data Models ---

    enum CaseStatus {
        PENDING, RUNNING, PASS, FAIL
    }

    static class CaseResult {
        final TestScenario scenario;
        CaseStatus status = CaseStatus.PENDING;
        String rc;
        String detail;
        boolean expanded = false;

        CaseResult(TestScenario scenario) {
            this.scenario = scenario;
        }
    }

    // --- Adapter ---

    static class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.VH> {
        private final List<CaseResult> items;

        ResultAdapter(List<CaseResult> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_batch_result, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            CaseResult cr = items.get(position);
            h.tvCaseName.setText(cr.scenario.getDescription());

            String type = cr.scenario.getTxnType() != null ? cr.scenario.getTxnType() : "PURCHASE";
            h.tvCaseType.setText(
                    type + " | DE22: " + (cr.scenario.getField(22) != null ? cr.scenario.getField(22) : "---"));

            switch (cr.status) {
                case PENDING:
                    h.ivStatus.setImageResource(R.drawable.ic_pending);
                    h.tvRcBadge.setText("PENDING");
                    h.tvRcBadge.setTextColor(0xFF64748B);
                    h.tvRcBadge.setBackgroundResource(R.drawable.bg_badge_default);
                    break;
                case RUNNING:
                    h.ivStatus.setImageResource(R.drawable.ic_pending);
                    h.tvRcBadge.setText("RUNNING...");
                    h.tvRcBadge.setTextColor(0xFF0369A1);
                    h.tvRcBadge.setBackgroundResource(R.drawable.bg_badge_default);
                    break;
                case PASS:
                    h.ivStatus.setImageResource(R.drawable.ic_check_circle);
                    h.tvRcBadge.setText("RC: " + cr.rc);
                    h.tvRcBadge.setTextColor(0xFF16A34A);
                    h.tvRcBadge.setBackgroundResource(R.drawable.bg_badge_custom);
                    break;
                case FAIL:
                    h.ivStatus.setImageResource(R.drawable.ic_error);
                    h.tvRcBadge.setText(cr.rc != null ? "RC: " + cr.rc : "FAIL");
                    h.tvRcBadge.setTextColor(0xFFDC2626);
                    h.tvRcBadge.setBackgroundResource(R.drawable.bg_badge_default);
                    break;
            }

            if (cr.detail != null && !cr.detail.isEmpty()) {
                h.tvDetail.setText(cr.detail);
                h.tvDetail.setVisibility(cr.expanded ? View.VISIBLE : View.GONE);
            } else {
                h.tvDetail.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> {
                if (cr.detail != null && !cr.detail.isEmpty()) {
                    cr.expanded = !cr.expanded;
                    h.tvDetail.setVisibility(cr.expanded ? View.VISIBLE : View.GONE);
                    if (cr.expanded)
                        h.tvDetail.setMaxLines(Integer.MAX_VALUE);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ImageView ivStatus;
            final TextView tvCaseName, tvCaseType, tvRcBadge, tvDetail;

            VH(View v) {
                super(v);
                ivStatus = v.findViewById(R.id.ivStatus);
                tvCaseName = v.findViewById(R.id.tvCaseName);
                tvCaseType = v.findViewById(R.id.tvCaseType);
                tvRcBadge = v.findViewById(R.id.tvRcBadge);
                tvDetail = v.findViewById(R.id.tvDetail);
            }
        }
    }
}
