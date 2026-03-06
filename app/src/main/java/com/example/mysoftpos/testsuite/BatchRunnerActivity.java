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
import com.example.mysoftpos.ui.BaseActivity;
import com.example.mysoftpos.utils.IntentKeys;
import com.example.mysoftpos.utils.logging.ResponseCodeHelper;
import com.example.mysoftpos.di.ServiceLocator;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BatchRunnerActivity extends BaseActivity {

    // CachedThreadPool: creates new threads as needed, ideal for parallel I/O
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private TransactionExecutor transactionExecutor;
    private com.example.mysoftpos.data.repository.TransactionRepository transactionRepository;

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
        transactionRepository = ServiceLocator.getInstance(getApplication()).getTransactionRepository();

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
        btnRunAll.setText("Preparing...");
        completedCount.set(0);

        // Reset all to PENDING
        for (CaseResult r : results) {
            r.status = CaseStatus.PENDING;
            r.rc = null;
            r.detail = null;
        }
        adapter.notifyDataSetChanged();

        int total = results.size();

        // Phase 1: Pre-build ALL contexts & cards on a single thread
        // This avoids SharedPreferences lock contention from ConfigManager.getAndIncrementTrace()
        executor.execute(() -> {
            // Pre-build arrays
            TransactionContext[] contexts = new TransactionContext[total];
            CardInputData[] cards = new CardInputData[total];
            String[] txnTypes = new String[total];

            for (int i = 0; i < total; i++) {
                CaseResult cr = results.get(i);
                TestScenario scenario = cr.scenario;
                try {
                    txnTypes[i] = scenario.getTxnType() != null ? scenario.getTxnType() : "PURCHASE";
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

                    contexts[i] = TransactionExecutor.buildContext(
                            getApplication(), txnTypes[i], amount, null, null);
                    applySchemeConnection(contexts[i], scheme);

                    cards[i] = TransactionExecutor.prepareCard(
                            getApplication(), de22, pan, expiry, track2, pinBlock,
                            contexts[i], msg -> {});
                } catch (Exception e) {
                    cr.status = CaseStatus.FAIL;
                    cr.rc = "BUILD_ERR";
                    cr.detail = "Build error: " + e.getMessage();
                }
            }

            // Phase 2: Fire ALL network calls simultaneously
            runOnUiThread(() -> {
                btnRunAll.setText("Sending " + total + " transactions...");
                for (CaseResult r : results) {
                    if (r.status != CaseStatus.FAIL) {
                        r.status = CaseStatus.RUNNING;
                    }
                }
                adapter.notifyDataSetChanged();
                progressBar.setProgress(0);
            });

            // Use a CountDownLatch so we know when all finish
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(total);

            for (int i = 0; i < total; i++) {
                final int index = i;
                final CaseResult cr = results.get(index);

                if (cr.status == CaseStatus.FAIL) {
                    // Already failed during build
                    int done = completedCount.incrementAndGet();
                    runOnUiThread(() -> {
                        adapter.notifyItemChanged(index);
                        progressBar.setProgress(done);
                        tvProgress.setText(done + "/" + total);
                    });
                    latch.countDown();
                    continue;
                }

                final TransactionContext ctx = contexts[index];
                final CardInputData card = cards[index];
                final String txnType = txnTypes[index];

                executor.execute(() -> {
                    try {
                        StringBuilder sb = new StringBuilder();
                        TransactionExecutor.LogCallback logger = msg -> sb.append(msg).append("\n");

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

                        // Save to DB for history
                        saveTransactionToDb(ctx, card, result);
                    } catch (java.net.SocketTimeoutException e) {
                        cr.status = CaseStatus.FAIL;
                        cr.rc = "TIMEOUT";
                        cr.detail = "Error: Timeout waiting for response.";
                    } catch (Exception e) {
                        cr.status = CaseStatus.FAIL;
                        cr.rc = "ERROR";
                        cr.detail = "Error: " + e.getMessage();
                    }

                    int done = completedCount.incrementAndGet();
                    runOnUiThread(() -> {
                        adapter.notifyItemChanged(index);
                        progressBar.setProgress(done);
                        tvProgress.setText(done + "/" + total);

                        if (done == total) {
                            isRunning = false;
                            btnRunAll.setEnabled(true);
                            long passed = results.stream().filter(r -> r.status == CaseStatus.PASS).count();
                            long failed = results.stream().filter(r -> r.status == CaseStatus.FAIL).count();
                            btnRunAll.setText("Done: " + passed + " Pass, " + failed + " Fail — Run Again?");
                        }
                    });
                    latch.countDown();
                });
            }
        });
    }


    private void applySchemeConnection(TransactionContext ctx, String schemeName) {
        if (schemeName == null)
            return;
        SchemeRepository repo = new SchemeRepository(this);
        com.example.mysoftpos.testsuite.model.Scheme s = repo.getByName(schemeName);
        if (s == null) return;
        if (s.getServerIp() != null && !s.getServerIp().isEmpty()) {
            ctx.ip = s.getServerIp();
            ctx.port = s.getServerPort();
        }
        // Terminal / Merchant overrides
        String tid = s.getTerminalId();
        if (tid != null && !tid.isEmpty()) ctx.terminalId41 = tid;
        String mid = s.getMerchantId();
        if (mid != null && !mid.isEmpty()) ctx.merchantId42 = mid;
        String mcc = s.getMcc();
        if (mcc != null && !mcc.isEmpty()) ctx.mcc18 = mcc;
        String acq = s.getAcquirerId();
        if (acq != null && !acq.isEmpty()) ctx.acquirerId32 = acq;
        String currency = s.getCurrencyCode();
        if (currency != null && !currency.isEmpty()) ctx.currency49 = currency;
        String country = s.getCountryCode();
        if (country != null && !country.isEmpty()) ctx.country19 = country;
        String posCond = s.getPosConditionCode();
        if (posCond != null && !posCond.isEmpty()) ctx.posCondition25 = posCond;
        String de43 = s.buildMerchantNameLocation();
        if (!de43.isEmpty()) ctx.merchantNameLocation43 = de43;
    }

    private void saveTransactionToDb(TransactionContext ctx, CardInputData card,
                                      TransactionResult result) {
        try {
            String pan = card.getPan();
            com.example.mysoftpos.domain.model.TransactionRecord record =
                    new com.example.mysoftpos.domain.model.TransactionRecord.Builder()
                            .setTraceNumber(ctx.stan11)
                            .setAmount(ctx.amount4)
                            .setStatus(result.status)
                            .setRequestHex(result.reqHex)
                            .setResponseHex(result.respHex)
                            .setTimestamp(System.currentTimeMillis())
                            .setMerchantCode(ctx.merchantId42)
                            .setMerchantName(ctx.merchantNameLocation43)
                            .setTerminalCode(ctx.terminalId41)
                            .setPanMasked(com.example.mysoftpos.utils.PanUtils.mask(pan))
                            .setBin(com.example.mysoftpos.utils.PanUtils.getBin(pan))
                            .setLast4(com.example.mysoftpos.utils.PanUtils.getLast4(pan))
                            .setScheme(com.example.mysoftpos.utils.PanUtils.detectScheme(pan))
                            .setUsername("TEST_SUITE_BATCH")
                            .setProcessingCode(ctx.processingCode3)
                            .setCurrencyCode(ctx.currency49)
                            .build();
            transactionRepository.saveTransaction(record);
            // Sync to backend via WorkManager
            com.example.mysoftpos.data.remote.SyncWorker.enqueueOneTime(this);
        } catch (Exception e) {
            android.util.Log.e("BatchRunner", "Save to DB failed", e);
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
