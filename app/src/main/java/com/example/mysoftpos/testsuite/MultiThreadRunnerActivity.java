package com.example.mysoftpos.testsuite;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mysoftpos.R;
import com.example.mysoftpos.domain.service.TransactionExecutor;
import com.example.mysoftpos.domain.service.TransactionResult;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.testsuite.model.TestScenario;
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
public class MultiThreadRunnerActivity extends AppCompatActivity {

    private TextView tvLog;
    private TextView tvStatus;
    private ScrollView scrollLog;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TransactionExecutor transactionExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_thread_runner);

        tvLog = findViewById(R.id.tvLog);
        tvStatus = findViewById(R.id.tvStatus);
        scrollLog = findViewById(R.id.scrollLog);

        transactionExecutor = com.example.mysoftpos.di.ServiceLocator.getInstance(getApplicationContext())
                .getTransactionExecutor();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        @SuppressWarnings("unchecked")
        ArrayList<TestScenario> scenarios = (ArrayList<TestScenario>) getIntent()
                .getSerializableExtra(com.example.mysoftpos.utils.IntentKeys.SCENARIOS);
        String txnType = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.TXN_TYPE);
        String schemeName = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.SCHEME);

        if (scenarios == null || scenarios.isEmpty()) {
            appendLog("No scenarios selected.");
            tvStatus.setText("Error: No scenarios");
            return;
        }

        int threadCount = scenarios.size();
        tvStatus.setText("Preparing " + threadCount + " tests...");
        appendLog("=== Multi-thread Runner ===\n");
        appendLog("Total tests: " + threadCount + "\n");
        appendLog("Mode: Concurrent (Thread Pool)\n\n");

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

            for (int i = 0; i < threadCount; i++) {
                TestScenario scenario = scenarios.get(i);
                types[i] = scenario.getTxnType() != null ? scenario.getTxnType() : txnType;
                tags[i] = "[T" + (i + 1) + " " + types[i] + " " + scenario.getField(22) + "]";

                try {
                    TransactionExecutor.LogCallback noop = msg -> {};
                    String amount = scenario.getField(4);
                    contexts[i] = TransactionExecutor.buildContext(getApplicationContext(), types[i], amount, null, null);

                    if (schemeName != null && !schemeName.isEmpty()) {
                        try {
                            SchemeRepository repo = new SchemeRepository(getApplicationContext());
                            Scheme scheme = repo.getByName(schemeName);
                            if (scheme != null && scheme.hasConnectionConfig()) {
                                contexts[i].ip = scheme.getServerIp();
                                contexts[i].port = scheme.getServerPort();
                            }
                        } catch (Exception ignored) {}
                    }

                    String de22 = scenario.getField(22);
                    cards[i] = TransactionExecutor.prepareCard(
                            getApplicationContext(), de22,
                            scenario.getField(2), scenario.getField(14),
                            scenario.getField(35), scenario.getUserPin(),
                            contexts[i], noop);
                } catch (Exception e) {
                    appendLog(tags[i] + " Build error: " + e.getMessage() + "\n");
                }
            }

            // Phase 2: Fire ALL network calls simultaneously
            mainHandler.post(() -> tvStatus.setText("Sending " + threadCount + " transactions..."));

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                final String tag = tags[i];
                final TransactionContext ctx = contexts[i];
                final CardInputData card = cards[i];
                final String typeToRun = types[i];

                if (ctx == null || card == null) {
                    failed.incrementAndGet();
                    int done = completed.incrementAndGet();
                    appendLog(tag + " *** SKIPPED (build error) ***\n");
                    if (done == threadCount) {
                        appendLog("\n=== ALL TESTS COMPLETE ===\n");
                        appendLog("Pass: " + passed.get() + " / Fail: " + failed.get() + "\n");
                        pool.shutdown();
                    }
                    mainHandler.post(() -> tvStatus.setText("Completed " + done + "/" + threadCount));
                    continue;
                }

                pool.execute(() -> {
                    appendLog(tag + " Starting...\n");
                    try {
                        TransactionExecutor.LogCallback logger = msg -> appendLog(tag + " " + msg + "\n");

                        TransactionResult result = transactionExecutor.execute(
                                getApplicationContext(), ctx, card, typeToRun, logger, tag);

                        appendLog(tag + " Packed Hex (" + result.reqHex.length() / 2 + " bytes):\n" + result.reqHex + "\n");
                        appendLog(tag + " Response Hex:\n" + result.respHex + "\n");

                        String reason = ResponseCodeHelper.getMessage(result.rc);
                        if (result.approved) {
                            passed.incrementAndGet();
                            appendLog(tag + " *** STATUS: PASS ***\n");
                            appendLog(tag + " RC: " + result.rc + " (" + reason + ")\n");
                        } else {
                            failed.incrementAndGet();
                            appendLog(tag + " *** STATUS: FAIL ***\n");
                            appendLog(tag + " RC: " + result.rc + " - Reason: " + reason + "\n");
                        }
                    } catch (java.net.SocketTimeoutException e) {
                        failed.incrementAndGet();
                        appendLog(tag + " *** STATUS: FAIL ***\n");
                        appendLog(tag + " Error: Timeout waiting for response.\n");
                    } catch (Exception e) {
                        failed.incrementAndGet();
                        appendLog(tag + " *** STATUS: FAIL ***\n");
                        appendLog(tag + " Error: " + e.getMessage() + "\n");
                    }

                    int done = completed.incrementAndGet();
                    mainHandler.post(() -> tvStatus.setText("Completed " + done + "/" + threadCount
                            + " (Pass: " + passed.get() + " / Fail: " + failed.get() + ")"));

                    if (done == threadCount) {
                        appendLog("\n=== ALL TESTS COMPLETE ===\n");
                        appendLog("Pass: " + passed.get() + " / Fail: " + failed.get() + "\n");
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
}
