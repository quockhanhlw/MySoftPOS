package com.example.mysoftpos;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mysoftpos.data.IsoDepTransceiver;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.TransactionEntity;
import com.example.mysoftpos.data.remote.IsoNetworkClient;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.domain.usecase.ReadCardDataUseCase;
import com.example.mysoftpos.iso8583.Iso8583Builder;
import com.example.mysoftpos.iso8583.IsoField;
import com.example.mysoftpos.iso8583.IsoMessage;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.utils.BinResolver;
import com.example.mysoftpos.utils.ConfigManager;
import com.example.mysoftpos.utils.StandardIsoPacker;
import com.example.mysoftpos.utils.TransactionValidator;
import android.widget.EditText;

import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PurchaseCardActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    private static final String TAG = "PurchaseCard";
    private NfcAdapter nfcAdapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ConfigManager configManager;
    private AppDatabase appDatabase;

    private TxnType txnType = TxnType.PURCHASE;
    private String amount = "0";

    // UI
    private TextView tvTitle, tvAmountDisplay;
    private View layoutSelection, layoutNfc, layoutManual;
    private EditText etPan, etExp;
    
    // State
    private boolean isNfcActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_card);

        // Core Init
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        configManager = ConfigManager.getInstance(this);
        appDatabase = AppDatabase.getInstance(this);

        // Intent Data
        String typeStr = getIntent().getStringExtra("TXN_TYPE");
        amount = getIntent().getStringExtra("AMOUNT");
        if (amount == null) amount = "0";
        txnType = "BALANCE_INQUIRY".equals(typeStr) ? TxnType.BALANCE_INQUIRY : TxnType.PURCHASE;

        // UI Binding
        tvTitle = findViewById(R.id.tvTitle); // Can be null if layout changed
        tvAmountDisplay = findViewById(R.id.tvAmountDisplay);
        layoutSelection = findViewById(R.id.layoutMethodSelection);
        layoutNfc = findViewById(R.id.layoutNfcMode);
        layoutManual = findViewById(R.id.layoutManualMode);
        
        etPan = findViewById(R.id.etCardNumber);
        etExp = findViewById(R.id.etExpiry);

        // Header Logic
        findViewById(R.id.btnBack).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        if (txnType == TxnType.BALANCE_INQUIRY) {
            tvAmountDisplay.setVisibility(View.GONE);
        } else {
            tvAmountDisplay.setVisibility(View.VISIBLE);
            tvAmountDisplay.setText(amount + " VND");
        }

        // --- SELECTION LOGIC ---
        findViewById(R.id.cardOptionNfc).setOnClickListener(v -> showNfcMode());
        findViewById(R.id.cardOptionManual).setOnClickListener(v -> showManualMode());

        // --- BACK NAVIGATION LOGIC ---
        // --- BACK NAVIGATION LOGIC ---
        // Handled by generic back button (OnBackPressedCallback)

        // --- MANUAL PROCESSING LOGIC ---
        findViewById(R.id.btnProcess).setOnClickListener(v -> {
            String pan = etPan.getText().toString().replace(" ", "").trim();
            String rawExp = etExp.getText().toString().trim();
            String exp = formatExpiryDate(rawExp);
            
            CardInputData manual = new CardInputData(pan, exp, null, "011", null, null);
            processTransaction(manual);
        });

        // Bin Resolver
        TextView tvBank = findViewById(R.id.tvBankName);
        etPan.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) {
                String pan = s.toString().replace(" ", "");
                if (pan.length() >= 6) {
                     String bank = BinResolver.getBankName(pan);
                     tvBank.setText(bank);
                     tvBank.setVisibility(View.VISIBLE);
                } else {
                     tvBank.setVisibility(View.GONE);
                }
            }
        });
        
        // Initial State
        showSelectionMode();
        
        // --- MOCK TAP TRIGGER (Hidden Feature) ---
        // Clicking the NFC Illustration triggers the Mock Transaction immediately
        findViewById(R.id.ivNfcIcon).setOnClickListener(v -> {
            // Track 2: 9704189991010867647=31016010000000123 (37 chars)
            CardInputData mockData = new CardInputData(
                "9704189991010867647",
                "3101",
                "9704189991010867647=31016010000000123",
                "072",
                null, null
            );
            Toast.makeText(this, "Mock NFC Triggered...", Toast.LENGTH_SHORT).show();
            processTransaction(mockData);
        });
        
        // Modern Back Navigation (API 33+)
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
             @Override
             public void handleOnBackPressed() {
                 if (layoutSelection.getVisibility() != View.VISIBLE) {
                     showSelectionMode();
                 } else {
                     setEnabled(false);
                     getOnBackPressedDispatcher().onBackPressed();
                 }
             }
        });
    }
    
    private void showSelectionMode() {
        isNfcActive = false;
        layoutSelection.setVisibility(View.VISIBLE);
        layoutNfc.setVisibility(View.GONE);
        layoutManual.setVisibility(View.GONE);
        if (nfcAdapter != null) nfcAdapter.disableReaderMode(this);
    }
    
    private void showNfcMode() {
        isNfcActive = true;
        layoutSelection.setVisibility(View.GONE);
        layoutNfc.setVisibility(View.VISIBLE);
        layoutManual.setVisibility(View.GONE);
        enableNfcReader();
    }
    
    private void showManualMode() {
        isNfcActive = false;
        layoutSelection.setVisibility(View.GONE);
        layoutNfc.setVisibility(View.GONE);
        layoutManual.setVisibility(View.VISIBLE);
        if (nfcAdapter != null) nfcAdapter.disableReaderMode(this);

        // Animate Card Entry
        View cardContainer = findViewById(R.id.cardInputContainer);
        android.view.animation.Animation slideUp = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom);
        cardContainer.startAnimation(slideUp);
    }
    
    private void enableNfcReader() {
        if (nfcAdapter != null) {
            nfcAdapter.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isNfcActive) {
            enableNfcReader();
        }
    }
    
    // Back Navigation Logic handled in onCreate via OnBackPressedCallback

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) nfcAdapter.disableReaderMode(this);
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) return;

        executor.execute(() -> {
            try {
                IsoDepTransceiver transceiver = new IsoDepTransceiver(isoDep);
                transceiver.connect();
                ReadCardDataUseCase useCase = new ReadCardDataUseCase(transceiver);
                CardInputData data = useCase.execute();
                transceiver.close();

                // --- MOCK TEST FORCE (User Request) ---
                // Track 2: Shortened to 37 chars (max per server)
                // Orig: 9704189991010867647=3101601000000001230 (39)
                // New:  9704189991010867647=31016010000000123   (37)
                String mockTrk2 = "9704189991010867647=31016010000000123";
                String mockPan = "9704189991010867647";
                String mockExp = "3101"; // YYMM from Track 2 (3101)
                
                // Override with Mock Data
                CardInputData mockOverrideData = new CardInputData(
                    mockPan, 
                    mockExp, 
                    mockTrk2, 
                    "072", // NFC Mode
                    null, // pinBlock
                    data != null ? data.getEmvTags() : null // Keep EMV tags
                );

                runOnUiThread(() -> {
                    Toast.makeText(this, "NFC Read Success (MOCK 37)", Toast.LENGTH_SHORT).show();
                    processTransaction(mockOverrideData);
                });
            } catch (Exception e) {
                // FALLBACK MOCK (Shortened to 37)
                // Track 2: 9704189991010867647=31016010000000123
                CardInputData mockData = new CardInputData(
                    "9704189991010867647",
                    "3101",
                    "9704189991010867647=31016010000000123",
                    "072",
                    null, null
                );
                runOnUiThread(() -> {
                     Toast.makeText(this, "Using Mock Data (Read Error bypassed)", Toast.LENGTH_SHORT).show();
                     processTransaction(mockData);
                });
            }
        });
    }

    private void processTransaction(CardInputData card) {
        runOnUiThread(() -> showLoading(true));
        executor.execute(() -> {
            TransactionContext ctx = new TransactionContext();
            TransactionEntity entity = new TransactionEntity();

            try {
                // Validation (Strict Rules)
                boolean isPurchase = (txnType == TxnType.PURCHASE);
                String amtF4 = isPurchase ? TransactionContext.formatAmount12(amount) : "000000000000";
                
                TransactionValidator.ValidationResult v = TransactionValidator.validate(card, amount, isPurchase);
                if (v != TransactionValidator.ValidationResult.VALID) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        showResult(false, "Validation Failed: " + v, null, null);
                    });
                    return;
                }

                // Context
                ctx.txnType = txnType;
                ctx.amount4 = amtF4;
                ctx.stan11 = configManager.getAndIncrementTrace();
                ctx.generateDateTime();
                ctx.rrn37 = TransactionContext.calculateRrn(configManager.getServerId(), ctx.stan11);
                
                // Configurable Fields from JSON/Prefs
                ctx.mcc18 = configManager.getMcc18();
                ctx.acquirerId32 = configManager.getAcquirerId32();
                ctx.fwdInst33 = configManager.getForwardingInst33();
                ctx.currency49 = configManager.getCurrencyCode49();
                
                ctx.terminalId41 = configManager.getTerminalId();
                ctx.merchantId42 = configManager.getMerchantId();
                ctx.merchantNameLocation43 = configManager.getMerchantName();
                
                ctx.ip = configManager.getServerIp();
                ctx.port = configManager.getServerPort();

                // Build Request (Builder enforces strict rules)
                IsoMessage req = (txnType == TxnType.BALANCE_INQUIRY)
                        ? Iso8583Builder.buildBalanceMsg(ctx, card)
                        : Iso8583Builder.buildPurchaseMsg(ctx, card);
                
                // Pack (StandardIsoPacker handling 128 fields)
                byte[] packed = StandardIsoPacker.pack(req);
                
                // LOG REQUEST (File)
                com.example.mysoftpos.utils.FileLogger.logPacket(this, "SEND 0200", packed);

                // DB Log
                entity.traceNumber = ctx.stan11;
                entity.amount = amount;
                entity.pan = card.getPan();
                entity.status = "PENDING";
                entity.requestHex = StandardIsoPacker.bytesToHex(packed);
                entity.timestamp = System.currentTimeMillis();
                appDatabase.transactionDao().insert(entity);

                // Network Send (Double Safety with Auto-Reversal)
                IsoNetworkClient client = new IsoNetworkClient(ctx.ip, ctx.port);
                byte[] resp;
                try {
                    resp = client.sendAndReceive(packed);
                    // LOG RESPONSE (File)
                    com.example.mysoftpos.utils.FileLogger.logPacket(this, "RECV 0210", resp);
                    
                } catch (SocketTimeoutException e) {
                    com.example.mysoftpos.utils.FileLogger.logString(this, "ERROR", "Timeout waiting for response");
                    handleAutoReversal(ctx, card, entity);
                    return;
                } catch (Exception e) {
                    com.example.mysoftpos.utils.FileLogger.logString(this, "ERROR", "Network Error: " + e.getMessage());
                    throw e; // rethrow to outer catch
                }

                // Unpack & Check Response
                IsoMessage respMsg = new StandardIsoPacker().unpack(resp);
                String rc = respMsg.getField(IsoField.RESPONSE_CODE_39);
                entity.responseHex = StandardIsoPacker.bytesToHex(resp);
                entity.status = "00".equals(rc) ? "APPROVED" : "DECLINED " + rc;
                appDatabase.transactionDao().update(entity);
                
                runOnUiThread(() -> {
                    showLoading(false);
                    // Map RC to Human Readable Message
                    String msg = com.example.mysoftpos.utils.ResponseCodeHelper.getMessage(rc);
                    showResult("00".equals(rc), msg, StandardIsoPacker.bytesToHex(resp), StandardIsoPacker.bytesToHex(packed));
                });

            } catch (Exception e) {
                Log.e(TAG, "Error", e);
                runOnUiThread(() -> {
                    showLoading(false);
                    showResult(false, "Error: " + e.getMessage(), null, null);
                });
            }
        });
    }

    private void showLoading(boolean loading) {
        View v = findViewById(R.id.layoutLoading);
        if (v != null) v.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void handleAutoReversal(TransactionContext ctx, CardInputData card, TransactionEntity entity) {
        try {
            Log.d(TAG, "Starting Auto-Reversal due to Timeout...");
            
            // 1. Build Reversal Advice (0420)
            String newTrace = configManager.getAndIncrementTrace();
            IsoMessage rev = Iso8583Builder.buildReversalAdvice(ctx, card, newTrace);
            byte[] packedRev = StandardIsoPacker.pack(rev);
            
            // LOG REVERSAL REQUEST
            com.example.mysoftpos.utils.FileLogger.logPacket(this, "SEND 0420 (Reversal)", packedRev);
            
            entity.status = "TIMEOUT_REVERSAL_INIT";
            appDatabase.transactionDao().update(entity);

            // 2. Send 0420 & Wait for 0430
            IsoNetworkClient revClient = new IsoNetworkClient(ctx.ip, ctx.port);
            byte[] revResp;
            
            try {
                revResp = revClient.sendAndReceive(packedRev);
                
                // 3. Received 0430 (Success or Failure doesn't matter, we received IT)
                com.example.mysoftpos.utils.FileLogger.logPacket(this, "RECV 0430", revResp);
                
                entity.status = "TIMEOUT_REVERSED";
                appDatabase.transactionDao().update(entity);
                
                runOnUiThread(() -> {
                     showLoading(false);
                     showResult(false, "Giao dịch lỗi Time Out (Đã gửi hủy)", null, null);
                });
                
            } catch (SocketTimeoutException e) {
                // 4. Timeout waiting for 0430 (Double Timeout)
                Log.e(TAG, "Timeout waiting for Reversal Response (0430)");
                com.example.mysoftpos.utils.FileLogger.logString(this, "ERROR", "Timeout waiting for 0430");
                
                entity.status = "TIMEOUT_REVERSAL_NO_RSP";
                appDatabase.transactionDao().update(entity);
                
                runOnUiThread(() -> {
                    showLoading(false);
                    // Specific User Request: "hết timeout sau mà vẫn không nhận được 0430 thì hiện giao dịch lỗi time out"
                    showResult(false, "Giao dịch lỗi Time Out", null, null);
                });
            }

        } catch (Exception e) {
             // General Error during Reversal Build/Send
             Log.e(TAG, "Reversal Failed", e);
             com.example.mysoftpos.utils.FileLogger.logString(this, "ERROR", "Reversal Critical Error: " + e.getMessage());
             
             entity.status = "TIMEOUT_REVERSAL_FAILED";
             appDatabase.transactionDao().update(entity);
             
             runOnUiThread(() -> {
                 showLoading(false);
                 showResult(false, "Giao dịch lỗi Time Out", null, null);
             });
        }
    }

    private void showResult(boolean success, String msg, String isoResp, String isoReq) {
        Intent i = new Intent(this, TransactionResultActivity.class);
        if (success) {
            i.putExtra(TransactionResultActivity.EXTRA_RESULT_TYPE, TransactionResultActivity.ResultType.SUCCESS);
        } else {
            i.putExtra(TransactionResultActivity.EXTRA_RESULT_TYPE, TransactionResultActivity.ResultType.TRANSACTION_FAILED);
        }
        i.putExtra(TransactionResultActivity.EXTRA_MESSAGE, msg);
        i.putExtra("TXN_TYPE", txnType); // Crucial for Balance Inquiry
        i.putExtra(TransactionResultActivity.EXTRA_ISO_RESPONSE, isoResp);
        if (isoReq != null) {
            i.putExtra(TransactionResultActivity.EXTRA_ISO_REQUEST, isoReq);
        }
        startActivity(i);
        finish();
    }

    private String formatExpiryDate(String raw) {
        if (raw == null) return "0000";
        // Remove non-digits
        return raw.replaceAll("[^0-9]", "");
    }
}
