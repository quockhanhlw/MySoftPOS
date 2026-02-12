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

        if (scenarios == null || scenarios.isEmpty()) {
            appendLog("No scenarios selected.");
            tvStatus.setText("Error: No scenarios");
            return;
        }

        int threadCount = scenarios.size();
        tvStatus.setText("Running " + threadCount + " tests concurrently...");
        appendLog("=== Multi-thread Runner ===\n");
        appendLog("Total tests: " + threadCount + "\n");
        appendLog("Mode: Concurrent (Thread Pool)\n\n");

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger passed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (int i = 0; i < scenarios.size(); i++) {
            TestScenario scenario = scenarios.get(i);
            int idx = i + 1;

            pool.execute(() -> {
                String typeToRun = scenario.getTxnType() != null ? scenario.getTxnType() : txnType;
                String tag = "[T" + idx + " " + typeToRun + " " + scenario.getField(22) + "]";
                appendLog(tag + " Starting...\n");

                try {
                    TransactionResult result = runSingleTransaction(scenario, typeToRun, tag);

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
    }

    private TransactionResult runSingleTransaction(TestScenario scenario, String txnType, String tag)
            throws Exception {
        TransactionExecutor.LogCallback logger = msg -> appendLog(tag + " " + msg + "\n");

        // 1. Build Context
        TransactionContext ctx = TransactionExecutor.buildContext(getApplicationContext(), txnType, null);

        // 2. Prepare Card
        String de22 = scenario.getField(22);
        CardInputData card = TransactionExecutor.prepareCard(
                getApplicationContext(), de22,
                scenario.getField(2), scenario.getField(14),
                scenario.getField(35), scenario.getUserPin(),
                ctx, logger);

        // 3. Execute
        TransactionResult result = transactionExecutor.execute(
                getApplicationContext(), ctx, card, txnType, logger, tag);

        // 4. Save to DB
        try {
            String pan = card.getPan();
            com.example.mysoftpos.domain.model.TransactionRecord record = new com.example.mysoftpos.domain.model.TransactionRecord.Builder()
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
                    .setUsername("TEST_MULTI")
                    .build();

            com.example.mysoftpos.di.ServiceLocator.getInstance(getApplicationContext())
                    .getTransactionRepository()
                    .saveTransaction(record);
        } catch (Exception e) {
            appendLog(tag + " DB Error: " + e.getMessage() + "\n");
        }

        return result;
    }

    private void appendLog(String text) {
        mainHandler.post(() -> {
            tvLog.append(text);
            scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }
}
