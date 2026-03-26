package com.example.mysoftpos.testsuite;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.widget.TextView;
import com.example.mysoftpos.R;
import com.example.mysoftpos.domain.service.TransactionExecutor;
import com.example.mysoftpos.domain.service.TransactionResult;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.testsuite.model.TestScenario;
import com.example.mysoftpos.ui.BaseActivity;
import com.example.mysoftpos.utils.PanUtils;
import com.example.mysoftpos.utils.logging.ResponseCodeHelper;
import com.example.mysoftpos.testsuite.model.Scheme;
import com.example.mysoftpos.testsuite.storage.SchemeRepository;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs multiple test scenarios concurrently using a thread pool.
 * Each scenario runs in its own thread via TransactionExecutor.
 */
public class MultiThreadRunnerActivity extends BaseActivity {

    private static final String ADMIN_TEST_SUITE_MID = "MYSOFTPOSSHOP01";

    private TextView tvLog;
    private TextView tvStatus;
    private ScrollView scrollLog;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TransactionExecutor transactionExecutor;
    private com.example.mysoftpos.data.repository.TransactionRepository transactionRepository;
    private String schemeName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_thread_runner);

        tvLog = findViewById(R.id.tvLog);
        tvStatus = findViewById(R.id.tvStatus);
        scrollLog = findViewById(R.id.scrollLog);

        transactionExecutor = com.example.mysoftpos.di.ServiceLocator.getInstance(getApplicationContext())
                .getTransactionExecutor();
        transactionRepository = com.example.mysoftpos.di.ServiceLocator.getInstance(getApplicationContext())
                .getTransactionRepository();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        @SuppressWarnings("unchecked")
        ArrayList<TestScenario> scenarios = (ArrayList<TestScenario>) getIntent()
                .getSerializableExtra(com.example.mysoftpos.utils.IntentKeys.SCENARIOS);
        String txnType = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.TXN_TYPE);
        schemeName = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.SCHEME);

        if (scenarios == null || scenarios.isEmpty()) {
            appendLog(getString(R.string.multi_runner_no_scenarios_selected));
            tvStatus.setText(R.string.multi_runner_status_no_scenarios);
            return;
        }

        int threadCount = scenarios.size();
        tvStatus.setText(getString(R.string.multi_runner_status_preparing, threadCount));
        appendLog(getString(R.string.multi_runner_log_header));
        appendLog(getString(R.string.multi_runner_log_total_tests, threadCount));
        appendLog(getString(R.string.multi_runner_log_mode_concurrent));

        ExecutorService pool = Executors.newCachedThreadPool();
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger passed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        // Phase 1: Pre-build ALL contexts on a single background thread
        // to avoid ConfigManager.getAndIncrementTrace() synchronized lock
        new Thread(() -> {
            TransactionContext[] contexts = new TransactionContext[threadCount];
            CardInputData[] cards = new CardInputData[threadCount];
            String[] types = new String[threadCount];
            String[] tags = new String[threadCount];
            String[] fieldOverrides = new String[threadCount];

            for (int i = 0; i < threadCount; i++) {
                TestScenario scenario = scenarios.get(i);
                types[i] = scenario.getTxnType() != null ? scenario.getTxnType() : txnType;
                tags[i] = "[T" + (i + 1) + " " + types[i] + " " + scenario.getField(22) + "]";

                try {
                    TransactionExecutor.LogCallback noop = msg -> {};
                    String amount = scenario.getField(4);
                    contexts[i] = TransactionExecutor.buildContext(getApplicationContext(), types[i], amount, null, null);
                    contexts[i].merchantId42 = ADMIN_TEST_SUITE_MID;

                    if (schemeName != null && !schemeName.isEmpty()) {
                        try {
                            SchemeRepository repo = new SchemeRepository(getApplicationContext());
                            Scheme scheme = repo.getByName(schemeName);
                            if (scheme != null) {
                                if (scheme.hasConnectionConfig()) {
                                    contexts[i].ip = scheme.getServerIp();
                                    contexts[i].port = scheme.getServerPort();
                                }
                                applySchemeToContext(contexts[i], scheme);
                            }
                        } catch (Exception ignored) {}
                    }

                    String de22 = scenario.getField(22);
                    cards[i] = TransactionExecutor.prepareCard(
                            getApplicationContext(), de22,
                            scenario.getField(2), scenario.getField(14),
                            scenario.getField(35), scenario.getUserPin(),
                            contexts[i], noop);
                    fieldOverrides[i] = buildFieldOverridesJson(scenario);
                } catch (Exception e) {
                    appendLog(getString(R.string.multi_runner_log_build_error, tags[i], e.getMessage()));
                }
            }

            // Phase 2: Fire ALL network calls simultaneously
            mainHandler.post(() -> tvStatus.setText(getString(R.string.multi_runner_status_sending, threadCount)));

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                final String tag = tags[i];
                final TransactionContext ctx = contexts[i];
                final CardInputData card = cards[i];
                final String typeToRun = types[i];
                final String fieldConfigJson = fieldOverrides[i];

                if (ctx == null || card == null) {
                    failed.incrementAndGet();
                    int done = completed.incrementAndGet();
                    appendLog(getString(R.string.multi_runner_log_skipped, tag));
                    if (done == threadCount) {
                        appendLog(getString(R.string.multi_runner_log_all_complete));
                        appendLog(getString(R.string.multi_runner_log_pass_fail, passed.get(), failed.get()));
                        pool.shutdown();
                    }
                    mainHandler.post(() -> tvStatus.setText(
                            getString(R.string.multi_runner_status_completed_simple, done, threadCount)));
                    continue;
                }

                pool.execute(() -> {
                    appendLog(getString(R.string.multi_runner_log_starting, tag));
                    try {
                        TransactionExecutor.LogCallback logger = msg -> appendLog(tag + " " + msg + "\n");

                        TransactionResult result = transactionExecutor.execute(
                                getApplicationContext(), ctx, card, typeToRun, logger, tag, fieldConfigJson);

                        appendLog(tag + " Packed Hex (" + result.reqHex.length() / 2 + " bytes):\n" + result.reqHex + "\n");
                        appendLog(tag + " Response Hex:\n" + result.respHex + "\n");

                        String reason = ResponseCodeHelper.getMessage(result.rc);
                        if (result.approved) {
                            passed.incrementAndGet();
                            appendLog(getString(R.string.multi_runner_log_status_pass, tag));
                            appendLog(getString(R.string.multi_runner_log_rc_reason, tag, result.rc, reason));
                        } else {
                            failed.incrementAndGet();
                            appendLog(getString(R.string.multi_runner_log_status_fail, tag));
                            appendLog(getString(R.string.multi_runner_log_rc_fail_reason, tag, result.rc, reason));
                        }

                        // Save to DB for history
                        saveTransactionToDb(ctx, card, result);
                    } catch (java.net.SocketTimeoutException e) {
                        failed.incrementAndGet();
                        appendLog(getString(R.string.multi_runner_log_status_fail, tag));
                        appendLog(getString(R.string.multi_runner_log_timeout, tag));
                    } catch (Exception e) {
                        failed.incrementAndGet();
                        appendLog(getString(R.string.multi_runner_log_status_fail, tag));
                        appendLog(getString(R.string.multi_runner_log_error, tag, e.getMessage()));
                    }

                    int done = completed.incrementAndGet();
                    mainHandler.post(() -> tvStatus.setText(getString(
                            R.string.multi_runner_status_completed_with_result,
                            done, threadCount, passed.get(), failed.get())));

                    if (done == threadCount) {
                        appendLog(getString(R.string.multi_runner_log_all_complete));
                        appendLog(getString(R.string.multi_runner_log_pass_fail, passed.get(), failed.get()));
                        pool.shutdown();
                    }
                });
            }
        }).start();
    }

    private void appendLog(String text) {
        mainHandler.post(() -> {
            tvLog.append(text);
            scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    private String buildFieldOverridesJson(TestScenario scenario) {
        if (scenario == null || scenario.getAllFields() == null || scenario.getAllFields().isEmpty()) {
            return null;
        }

        org.json.JSONObject json = new org.json.JSONObject();
        java.util.Set<Integer> reserved = new java.util.HashSet<>(
                java.util.Arrays.asList(2, 4, 14, 22, 35, 42, 52));

        for (java.util.Map.Entry<Integer, String> entry : scenario.getAllFields().entrySet()) {
            Integer field = entry.getKey();
            String value = entry.getValue();
            if (field == null || reserved.contains(field) || value == null || value.trim().isEmpty()) {
                continue;
            }
            try {
                json.put(String.valueOf(field), value);
            } catch (Exception ignored) {
            }
        }

        return json.length() > 0 ? json.toString() : null;
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
                            .setPanMasked(PanUtils.mask(pan))
                            .setBin(PanUtils.getBin(pan))
                            .setLast4(PanUtils.getLast4(pan))
                            .setScheme(PanUtils.detectScheme(pan))
                            .setUsername("TEST_SUITE_MULTI")
                            .setProcessingCode(ctx.processingCode3)
                            .setCurrencyCode(ctx.currency49)
                            .build();
            transactionRepository.saveTransaction(record);
            // Sync to backend via WorkManager
            com.example.mysoftpos.data.remote.SyncWorker.enqueueOneTime(this);
        } catch (Exception e) {
            android.util.Log.e("MultiThreadRunner", "Save to DB failed", e);
        }
    }

    private void applySchemeToContext(com.example.mysoftpos.iso8583.TransactionContext ctx, Scheme scheme) {
        ctx.merchantId42 = ADMIN_TEST_SUITE_MID;
        String tid = scheme.getTerminalId();
        if (tid != null && !tid.isEmpty()) ctx.terminalId41 = tid;
        String mcc = scheme.getMcc();
        if (mcc != null && !mcc.isEmpty()) ctx.mcc18 = mcc;
        String acq = scheme.getAcquirerId();
        if (acq != null && !acq.isEmpty()) ctx.acquirerId32 = acq;
        String currency = scheme.getCurrencyCode();
        if (currency != null && !currency.isEmpty()) ctx.currency49 = currency;
        String country = scheme.getCountryCode();
        if (country != null && !country.isEmpty()) ctx.country19 = country;
        String posCond = scheme.getPosConditionCode();
        if (posCond != null && !posCond.isEmpty()) ctx.posCondition25 = posCond;
        String de43 = scheme.buildMerchantNameLocation();
        if (!de43.isEmpty()) ctx.merchantNameLocation43 = de43;
    }
}
