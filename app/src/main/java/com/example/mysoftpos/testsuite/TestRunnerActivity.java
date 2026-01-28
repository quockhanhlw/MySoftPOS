package com.example.mysoftpos.testsuite;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.remote.IsoNetworkClient;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.Iso8583Builder;
import com.example.mysoftpos.iso8583.IsoMessage;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.testsuite.data.TestResultDao;
import com.example.mysoftpos.testsuite.model.CardProfile;
import com.example.mysoftpos.testsuite.model.TestCase;
import com.example.mysoftpos.testsuite.model.TestResult;
import com.example.mysoftpos.utils.ConfigManager;
import com.example.mysoftpos.utils.StandardIsoPacker;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Screen 3: Test Runner - Execute a specific test case.
 * 
 * PRODUCTION-GRADE FLOW:
 * ═══════════════════════════════════════════════════════════════════════════
 * 1. Validate Input (Card, Amount)
 * 2. Generate STAN & RRN
 * 3. Build ISO Message
 * 4. INSERT PENDING record to DB ← BEFORE network call
 * 5. Send via Socket (Background Thread)
 * 6. UPDATE DB status (SUCCESS/FAIL/TIMEOUT/ERROR)
 * 7. Show Result UI
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class TestRunnerActivity extends AppCompatActivity {

    // ═══════════════════════════════════════════════════════════════════
    //                         FIELDS
    // ═══════════════════════════════════════════════════════════════════
    
    private String testCaseId;
    private String scheme;
    private TestCase currentTestCase;
    private List<CardProfile> testCards;
    
    // UI Components
    private Spinner spinnerCard;
    private TextInputLayout tilAmount;
    private TextInputEditText etAmount;
    private TextView tvMti, tvProcCode, tvTerminalId;
    private TextView tvResultStatus, tvResponseCode, tvRrn, tvExecTime;
    private MaterialCardView cardResult;
    private FrameLayout layoutLoading;
    private Button btnRunTest;
    
    // Data
    private TestResultDao testResultDao;
    private ExecutorService executor;
    private ConfigManager config;

    // ═══════════════════════════════════════════════════════════════════
    //                         LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_runner);

        // Get Intent data
        testCaseId = getIntent().getStringExtra("TEST_CASE_ID");
        scheme = getIntent().getStringExtra("SCHEME");
        
        // Initialize dependencies
        executor = Executors.newSingleThreadExecutor();
        testResultDao = AppDatabase.getInstance(this).testResultDao();
        config = ConfigManager.getInstance(this);
        
        // Find the test case
        currentTestCase = findTestCase(testCaseId);
        if (currentTestCase == null) {
            Toast.makeText(this, R.string.error_test_case_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        initViews();
        setupToolbar();
        loadConfiguration();
        loadTestCards();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //                         INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════

    private void initViews() {
        spinnerCard = findViewById(R.id.spinnerCard);
        tilAmount = findViewById(R.id.tilAmount);
        etAmount = findViewById(R.id.etAmount);
        tvMti = findViewById(R.id.tvMti);
        tvProcCode = findViewById(R.id.tvProcCode);
        tvTerminalId = findViewById(R.id.tvTerminalId);
        tvResultStatus = findViewById(R.id.tvResultStatus);
        tvResponseCode = findViewById(R.id.tvResponseCode);
        tvRrn = findViewById(R.id.tvRrn);
        tvExecTime = findViewById(R.id.tvExecTime);
        cardResult = findViewById(R.id.cardResult);
        layoutLoading = findViewById(R.id.layoutLoading);
        btnRunTest = findViewById(R.id.btnRunTest);
        
        btnRunTest.setOnClickListener(v -> runTest());
        
        // Hide amount if not required for this test case
        View tvAmountLabel = findViewById(R.id.tvAmountLabel);
        if (!currentTestCase.isRequiresAmount()) {
            tilAmount.setVisibility(View.GONE);
            if (tvAmountLabel != null) tvAmountLabel.setVisibility(View.GONE);
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(currentTestCase.getDisplayName());
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadConfiguration() {
        tvMti.setText(currentTestCase.getMti());
        tvProcCode.setText(currentTestCase.getProcessingCode());
        tvTerminalId.setText(config.getTerminalId());
    }

    private void loadTestCards() {
        testCards = TestDataProvider.getTestCards();
        ArrayAdapter<CardProfile> adapter = new ArrayAdapter<>(
            this, 
            android.R.layout.simple_spinner_item, 
            testCards
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCard.setAdapter(adapter);
    }

    private TestCase findTestCase(String id) {
        if (id == null) return null;
        
        for (TestCase tc : TestDataProvider.getPosTestCases()) {
            if (tc.getId().equals(id)) return tc;
        }
        for (TestCase tc : TestDataProvider.getAtmTestCases()) {
            if (tc.getId().equals(id)) return tc;
        }
        for (TestCase tc : TestDataProvider.getQrcTestCases()) {
            if (tc.getId().equals(id)) return tc;
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //                         RUN TEST (Main Logic)
    // ═══════════════════════════════════════════════════════════════════

    private void runTest() {
        // ─────────────────────────────────────────────────────────────────
        // STEP 1: Input Validation
        // ─────────────────────────────────────────────────────────────────
        
        // Validate card selection
        CardProfile selectedCard = (CardProfile) spinnerCard.getSelectedItem();
        if (selectedCard == null) {
            Toast.makeText(this, R.string.error_select_card, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Validate amount (if required)
        String amountStr = "50000"; // Default for non-amount test cases
        if (currentTestCase.isRequiresAmount()) {
            String input = etAmount.getText() != null ? etAmount.getText().toString().trim() : "";
            if (input.isEmpty()) {
                tilAmount.setError(getString(R.string.error_enter_amount));
                return;
            }
            
            try {
                long amt = Long.parseLong(input);
                if (amt <= 0) {
                    tilAmount.setError(getString(R.string.error_invalid_amount));
                    return;
                }
                amountStr = input;
                tilAmount.setError(null); // Clear error
            } catch (NumberFormatException e) {
                tilAmount.setError(getString(R.string.error_invalid_amount));
                return;
            }
        }
        
        // ─────────────────────────────────────────────────────────────────
        // STEP 2: Generate STAN & RRN
        // ─────────────────────────────────────────────────────────────────
        
        final String finalAmount = amountStr;
        final String stan = config.getAndIncrementTrace();
        final String rrn = TransactionContext.generateRrn(stan);
        final long startTime = System.currentTimeMillis();
        
        // ─────────────────────────────────────────────────────────────────
        // UI: Show Loading
        // ─────────────────────────────────────────────────────────────────
        
        showLoading(true);
        cardResult.setVisibility(View.GONE);
        
        // ─────────────────────────────────────────────────────────────────
        // Background Execution
        // ─────────────────────────────────────────────────────────────────
        
        executor.execute(() -> {
            TestResult result = new TestResult();
            result.testCaseId = currentTestCase.getId();
            result.testCaseName = currentTestCase.getName();
            result.cardProfileId = selectedCard.getId();
            result.cardName = selectedCard.getName();
            result.amount = finalAmount;
            result.stan = stan;
            result.posEntryMode = currentTestCase.getPosEntryMode();
            result.requestMti = currentTestCase.getMti();
            result.status = TestResult.Status.PENDING.name();
            
            try {
                // ─────────────────────────────────────────────────────────
                // STEP 3: Build ISO Message
                // ─────────────────────────────────────────────────────────
                
                // Mock EMV Tags for Chip/NFC cases to satisfy DE 55 requirement
                java.util.Map<String, String> mockEmvTags = null;
                String mode = currentTestCase.getPosEntryMode();
                if (mode != null && (mode.startsWith("05") || mode.startsWith("07"))) {
                    mockEmvTags = new java.util.HashMap<>();
                    // Basic Napas/Standard Tags (9F26, 9F27, 9F10, 9F37, 9F36, 95, 9A, 9C, 9F02, 5F2A, 82, 9F1A, 9F03, 5F34)
                    mockEmvTags.put("9F26", "E293520E69D8C61D"); // Cryptogram
                    mockEmvTags.put("9F27", "80");             // Cryptogram Info Data
                    mockEmvTags.put("9F10", "06010A03A00000"); // Issuer App Data
                    mockEmvTags.put("9F37", "89352614");       // Unpredictable Number
                    mockEmvTags.put("9F36", "0209");           // ATC
                    mockEmvTags.put("95", "0000008000");       // TVR
                    mockEmvTags.put("9A", "240128");           // Txn Date
                    mockEmvTags.put("9C", "00");               // Txn Type
                    mockEmvTags.put("9F02", "000000000000");   // Amount Auth
                    mockEmvTags.put("5F2A", "0704");           // Currency Code
                    mockEmvTags.put("82", "1800");             // AIP
                    mockEmvTags.put("9F1A", "0704");           // Country Code
                    mockEmvTags.put("9F03", "000000000000");   // Amount Other
                    mockEmvTags.put("5F34", "01");             // PAN Seq
                }

                CardInputData cardData = new CardInputData(
                    selectedCard.getPan(),
                    selectedCard.getExpiryDate(),
                    selectedCard.getTrack2(),
                    currentTestCase.getPosEntryMode(), // DE 22 from test case
                    null,  // pinBlock (String is 5th arg)
                    mockEmvTags // emvTags (Map is 6th arg)
                );
                
                TransactionContext ctx = buildTransactionContext(stan, rrn, finalAmount);
                IsoMessage msg = buildIsoMessage(ctx, cardData);
                byte[] packed = StandardIsoPacker.pack(msg);
                result.requestHex = StandardIsoPacker.bytesToHex(packed);
                
                // ─────────────────────────────────────────────────────────
                // STEP 4: INSERT PENDING Record (BEFORE Socket Send)
                // ─────────────────────────────────────────────────────────
                
                long resultId = testResultDao.insert(result);
                result.id = resultId;
                
                // ─────────────────────────────────────────────────────────
                // STEP 5: Send via Socket
                // ─────────────────────────────────────────────────────────
                
                IsoNetworkClient client = new IsoNetworkClient(
                    config.getServerIp(), 
                    config.getServerPort()
                );
                
                byte[] response = client.sendAndReceive(packed);
                result.responseHex = StandardIsoPacker.bytesToHex(response);
                
                // ─────────────────────────────────────────────────────────
                // STEP 6: Parse Response & Determine Status
                // ─────────────────────────────────────────────────────────
                
                IsoMessage respMsg = new StandardIsoPacker().unpack(response);
                result.responseMti = respMsg.getMti();
                result.responseCode = respMsg.getField(39);
                result.rrn = respMsg.getField(37);
                result.authCode = respMsg.getField(38);
                
                // SUCCESS if Response Code = "00"
                if ("00".equals(result.responseCode)) {
                    result.status = TestResult.Status.SUCCESS.name();
                } else {
                    result.status = TestResult.Status.FAIL.name();
                }
                
                result.executionTimeMs = System.currentTimeMillis() - startTime;
                
            } catch (SocketTimeoutException e) {
                // TIMEOUT: 30s exceeded
                result.status = TestResult.Status.TIMEOUT.name();
                result.errorMessage = "Timeout (30s)";
                result.executionTimeMs = System.currentTimeMillis() - startTime;
                
            } catch (ConnectException e) {
                // CONNECTION REFUSED
                result.status = TestResult.Status.ERROR.name();
                result.errorMessage = "Connection Refused: " + config.getServerIp() + ":" + config.getServerPort();
                result.executionTimeMs = System.currentTimeMillis() - startTime;
                
            } catch (Exception e) {
                // OTHER ERRORS
                result.status = TestResult.Status.ERROR.name();
                result.errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
                result.executionTimeMs = System.currentTimeMillis() - startTime;
            }
            
            // ─────────────────────────────────────────────────────────────
            // STEP 7: UPDATE DB (Final Status)
            // ─────────────────────────────────────────────────────────────
            
            if (result.id > 0) {
                testResultDao.update(result);
            } else {
                // Fallback: insert if update failed
                testResultDao.insert(result);
            }
            
            // ─────────────────────────────────────────────────────────────
            // Update UI
            // ─────────────────────────────────────────────────────────────
            
            final TestResult finalResult = result;
            runOnUiThread(() -> {
                showLoading(false);
                showResult(finalResult);
            });
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    //                         HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════



    private TransactionContext buildTransactionContext(String stan, String rrn, String amount) {
        TransactionContext ctx = new TransactionContext();
        
        // Use shared logic to match Mock Test exactly (DE 7, 12, 13, 15)
        ctx.generateDateTime();
        
        ctx.stan11 = stan;
        ctx.rrn37 = rrn;
        ctx.terminalId41 = config.getTerminalId();
        ctx.merchantId42 = config.getMerchantId();
        ctx.merchantNameLocation43 = config.getMerchantName();
        ctx.mcc18 = config.getMcc18();
        ctx.acquirerId32 = config.getAcquirerId32();
        ctx.currency49 = config.getCurrencyCode49();
        ctx.ip = config.getServerIp();
        ctx.port = config.getServerPort();
        
        // Amount: Use shared logic (handles *100 padding)
        String amt = (amount == null || amount.isEmpty()) ? "0" : amount;
        ctx.amount4 = TransactionContext.formatAmount12(amt);
        
        return ctx;
    }

    private IsoMessage buildIsoMessage(TransactionContext ctx, CardInputData cardData) {
        // Build based on test case type
        switch (currentTestCase.getId()) {
            case "atm_balance":
                return Iso8583Builder.buildBalanceMsg(ctx, cardData);
                
            case "pos_reversal":
                ctx.txnType = com.example.mysoftpos.iso8583.TxnType.PURCHASE;
                return Iso8583Builder.buildReversalAdvice(ctx, cardData, ctx.stan11);
                
            default:
                // All other cases: Purchase
                return Iso8583Builder.buildPurchaseMsg(ctx, cardData);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //                         UI UPDATES
    // ═══════════════════════════════════════════════════════════════════

    private void showResult(TestResult result) {
        cardResult.setVisibility(View.VISIBLE);
        
        TestResult.Status status = result.getStatusEnum();
        tvResultStatus.setText(result.getStatusText());
        
        // Color based on status
        int bgColor;
        switch (status) {
            case SUCCESS:
                bgColor = ContextCompat.getColor(this, R.color.status_success);
                break;
            case FAIL:
                bgColor = ContextCompat.getColor(this, R.color.status_error);
                break;
            case TIMEOUT:
                bgColor = ContextCompat.getColor(this, R.color.status_warning);
                break;
            case ERROR:
            default:
                bgColor = ContextCompat.getColor(this, R.color.status_error);
                break;
        }
        
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(16f);
        tvResultStatus.setBackground(bg);
        
        // Details
        if (result.errorMessage != null && !result.errorMessage.isEmpty()) {
            tvResponseCode.setText("Error: " + result.errorMessage);
        } else {
            tvResponseCode.setText("RC: " + (result.responseCode != null ? result.responseCode : "N/A"));
        }
        
        tvRrn.setText("RRN: " + (result.rrn != null ? result.rrn : "N/A"));
        tvExecTime.setText("Time: " + result.executionTimeMs + "ms");
    }

    private void showLoading(boolean show) {
        layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        btnRunTest.setEnabled(!show);
    }
}
