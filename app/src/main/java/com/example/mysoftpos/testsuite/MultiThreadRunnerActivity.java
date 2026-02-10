package com.example.mysoftpos.testsuite;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mysoftpos.R;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.iso8583.builder.Iso8583Builder;
import com.example.mysoftpos.iso8583.message.IsoMessage;
import com.example.mysoftpos.iso8583.util.StandardIsoPacker;
import com.example.mysoftpos.testsuite.model.TestScenario;
import com.example.mysoftpos.utils.config.ConfigManager;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs multiple test scenarios concurrently using a thread pool.
 * Each scenario runs in its own thread. Results are logged to the UI.
 */
public class MultiThreadRunnerActivity extends AppCompatActivity {

    private TextView tvLog;
    private TextView tvStatus;
    private ScrollView scrollLog;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_thread_runner);

        tvLog = findViewById(R.id.tvLog);
        tvStatus = findViewById(R.id.tvStatus);
        scrollLog = findViewById(R.id.scrollLog);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        @SuppressWarnings("unchecked")
        ArrayList<TestScenario> scenarios = (ArrayList<TestScenario>) getIntent().getSerializableExtra("SCENARIOS");
        String txnType = getIntent().getStringExtra("TXN_TYPE");

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
                String tag = "[T" + idx + " " + scenario.getField(22) + "]";
                appendLog(tag + " Starting...\n");

                try {
                    String result = runSingleTransaction(scenario, txnType, tag);
                    if (result.contains("PASS")) {
                        passed.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                    appendLog(tag + " ERROR: " + e.getMessage() + "\n");
                }

                int done = completed.incrementAndGet();
                mainHandler.post(() -> {
                    tvStatus.setText("Completed " + done + "/" + threadCount
                            + " (Pass: " + passed.get() + " / Fail: " + failed.get() + ")");
                });

                if (done == threadCount) {
                    appendLog("\n=== ALL TESTS COMPLETE ===\n");
                    appendLog("Pass: " + passed.get() + " / Fail: " + failed.get() + "\n");
                    pool.shutdown();
                }
            });
        }
    }

    private String runSingleTransaction(TestScenario scenario, String txnType, String tag) throws Exception {
        ConfigManager config = ConfigManager.getInstance(getApplicationContext());

        // Build Context
        TransactionContext ctx = new TransactionContext();
        ctx.amount4 = TransactionContext.formatAmount12("12345");
        ctx.stan11 = config.getAndIncrementTrace();
        ctx.generateDateTime();
        ctx.rrn37 = TransactionContext.calculateRrn(config.getServerId(), ctx.stan11);

        if ("BALANCE".equals(txnType)) {
            ctx.processingCode3 = "300000";
        } else {
            ctx.processingCode3 = "000000";
        }
        ctx.posCondition25 = config.getPosConditionCode();
        ctx.mcc18 = config.getMcc18();
        ctx.acquirerId32 = config.getAcquirerId32();
        ctx.terminalId41 = config.getTerminalId();
        ctx.merchantId42 = config.getMerchantId();
        ctx.merchantNameLocation43 = config.getMerchantName();
        ctx.currency49 = config.getCurrencyCode49();
        ctx.ip = config.getServerIp();
        ctx.port = config.getServerPort();

        // Card Data
        String de22 = scenario.getField(22);
        String pan = scenario.getField(2);
        String expiry = scenario.getField(14);
        String track2 = scenario.getField(35);
        String pinData = scenario.getUserPin();

        if ((pan == null || pan.isEmpty()) && track2 != null) {
            String[] parts = track2.split("[=D]");
            if (parts.length > 0)
                pan = parts[0];
            if (parts.length > 1 && parts[1].length() >= 4) {
                expiry = parts[1].substring(0, 4);
            }
        }
        if (pan == null)
            pan = config.getMockPan();

        CardInputData card = new CardInputData(pan, expiry, de22, track2);

        // PIN Block
        if (pinData != null && !pinData.isEmpty() && pan != null) {
            try {
                String clearBlock = com.example.mysoftpos.iso8583.util.PinBlockGenerator
                        .calculateClearBlock(pinData, pan);
                ctx.pinBlock52 = clearBlock;
                ctx.encryptPin = true;
                card.setPinBlock(clearBlock);
                appendLog(tag + " PIN Block: " + clearBlock + "\n");
            } catch (Exception e) {
                appendLog(tag + " PIN Error: " + e.getMessage() + "\n");
            }
        }

        // Build Message
        IsoMessage msg;
        if ("BALANCE".equals(txnType)) {
            ctx.amount4 = "000000000000";
            msg = Iso8583Builder.buildBalanceMsg(ctx, card);
        } else {
            msg = Iso8583Builder.buildPurchaseMsg(ctx, card);
        }

        appendLog(tag + " Built " + msg.getMti() + " | STAN=" + ctx.stan11 + "\n");

        // Pack & Send
        byte[] packed = StandardIsoPacker.pack(msg);
        com.example.mysoftpos.utils.logging.FileLogger.logTestSuitePacket(getApplicationContext(), "SEND " + tag,
                packed);

        appendLog(tag + " Sending to " + ctx.ip + ":" + ctx.port + "...\n");

        com.example.mysoftpos.data.remote.IsoNetworkClient client = new com.example.mysoftpos.data.remote.IsoNetworkClient(
                ctx.ip, ctx.port);
        byte[] responseBytes = client.sendAndReceive(packed);

        com.example.mysoftpos.utils.logging.FileLogger.logTestSuitePacket(getApplicationContext(), "RECV " + tag,
                responseBytes);

        // Unpack & check RC
        IsoMessage respMsg = new StandardIsoPacker().unpack(responseBytes);
        String rc = respMsg.getField(39);

        String result;
        if ("00".equals(rc)) {
            result = "PASS";
            appendLog(tag + " ✅ PASS (RC: 00 - Approved)\n");
        } else {
            result = "FAIL";
            appendLog(tag + " ❌ FAIL (RC: " + rc + ")\n");
        }

        // Save to DB
        try {
            String maskedPan = pan != null && pan.length() >= 10
                    ? pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4)
                    : pan;
            String bin = (pan != null && pan.length() >= 6) ? pan.substring(0, 6) : "";
            String last4 = (pan != null && pan.length() >= 4) ? pan.substring(pan.length() - 4) : "";

            new com.example.mysoftpos.data.local.DatabaseManager(getApplicationContext())
                    .saveTransaction(
                            ctx.stan11,
                            ctx.amount4,
                            "00".equals(rc) ? "APPROVED" : "DECLINED",
                            StandardIsoPacker.bytesToHex(packed),
                            StandardIsoPacker.bytesToHex(responseBytes),
                            System.currentTimeMillis(),
                            ctx.merchantId42,
                            ctx.merchantNameLocation43,
                            ctx.terminalId41,
                            maskedPan, bin, last4,
                            "Napas",
                            "TEST_MULTI");
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
