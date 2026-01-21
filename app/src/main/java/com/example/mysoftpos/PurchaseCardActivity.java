package com.example.mysoftpos;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mysoftpos.data.IsoDepTransceiver;
import com.example.mysoftpos.data.NfcHardwareManager;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.TransactionEntity;
import com.example.mysoftpos.data.remote.IsoNetworkClient;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.domain.usecase.ReadCardDataUseCase;
import com.example.mysoftpos.iso8583.IsoMessage;
import com.example.mysoftpos.iso8583.IsoRequestBuilder;
import com.example.mysoftpos.iso8583.PurchaseFlowData;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.utils.CardValidator;
import com.google.android.material.button.MaterialButton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Step 2: User enters Payment Info (NFC or Manual).
 * Handles the logic for Amount -> Validation -> Network -> Result.
 */
public class PurchaseCardActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    private static final String TAG = "PurchaseCardActivity";
    private NfcHardwareManager nfcHardwareManager;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    private String transactionAmountDigits; // Raw digits e.g. "50000"
    private String transactionAmountF4;     // Padded 12 chars e.g. "000000050000"
    private boolean isNfcMode = false;
    private boolean isWaitingForNfc = false;
    
    private View vGlowRing;
    private androidx.appcompat.app.AlertDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_card);

        // 1. Get Amount and Mode from previous step
        transactionAmountDigits = getIntent().getStringExtra(PurchaseFlowData.EXTRA_AMOUNT_DIGITS);
        transactionAmountF4 = getIntent().getStringExtra(PurchaseFlowData.EXTRA_AMOUNT_F4);
        isNfcMode = getIntent().getBooleanExtra("EXTRA_MODE_NFC", true);

        if (transactionAmountF4 == null) {
            Toast.makeText(this, "Lỗi: Không có số tiền", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 2. Initialize NFC (Only if NFC Mode)
        if (isNfcMode) {
            nfcHardwareManager = new NfcHardwareManager(this);
            checkNfcState();
        }

        // 3. UI References
        vGlowRing = findViewById(R.id.vGlowRing);
        View layoutNfc = findViewById(R.id.layoutNfcMode);
        View layoutManual = findViewById(R.id.layoutManualMode);
        
        EditText etPan = findViewById(R.id.etPan);
        EditText etExpiry = findViewById(R.id.etExpiry);
        MaterialButton btnPay = findViewById(R.id.btnPay);
        
        // 4. Set Mode UI
        if (isNfcMode) {
            if (layoutNfc != null) layoutNfc.setVisibility(View.VISIBLE);
            if (layoutManual != null) layoutManual.setVisibility(View.GONE);
            startPulsing();
        } else {
            if (layoutNfc != null) layoutNfc.setVisibility(View.GONE);
            if (layoutManual != null) layoutManual.setVisibility(View.VISIBLE);
        }

        // Manual Input Logic
        btnPay.setOnClickListener(v -> {
            String pan = etPan.getText().toString().replace(" ", "").trim();
            String expiry = etExpiry.getText().toString().replace("/", "").trim();
            
            // Basic Manual Card Map (No CVV)
            CardInputData manualData = new CardInputData(pan, expiry, "011", null);
            processCardTransaction(manualData);
        });
    }

    private void checkNfcState() {
        // --- MOCK NFC BYPASS (TEMPORARY FIX) ---
        // Consistent with MainDashboardActivity bypass
        String rawTrack2 = "9704189991010867647=3101601000000001230";
        String pan = "9704189991010867647";
        String expiry = "3101"; // YYMM
        
        // 071 = NFC
        CardInputData mockData = new CardInputData(pan, expiry, "071", rawTrack2);

        Toast.makeText(this, "MOCK NFC Triggered (Purchase)", Toast.LENGTH_SHORT).show();
        
        // Execute immediately (Bypass Preview for speed/testing)
        processCardTransaction(mockData);
        return;

        /*
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
        if (adapter == null) {
            Toast.makeText(this, "Thiết bị không hỗ trợ NFC", Toast.LENGTH_LONG).show();
            return;
        }

        if (!adapter.isEnabled()) {
            isWaitingForNfc = true;
            if (nfcEnableDialog != null && nfcEnableDialog.isShowing()) return; // Already showing
            
            nfcEnableDialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("NFC chưa bật")
                .setMessage("Vui lòng bật NFC để thực hiện thanh toán.")
                .setPositiveButton("Cài đặt", (d, w) -> {
                    startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
                })
                .setNegativeButton("Hủy", (d, w) -> finish())
                .setCancelable(false)
                .create();
            nfcEnableDialog.show();
        }
        */
    }

    private void startPulsing() {
        if (vGlowRing != null) {
            vGlowRing.setVisibility(View.VISIBLE);
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(vGlowRing, "scaleX", 1f, 1.2f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(vGlowRing, "scaleY", 1f, 1.2f);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(vGlowRing, "alpha", 0.5f, 0.2f);
            
            scaleX.setRepeatCount(ObjectAnimator.INFINITE);
            scaleX.setRepeatMode(ObjectAnimator.REVERSE);
            scaleY.setRepeatCount(ObjectAnimator.INFINITE);
            scaleY.setRepeatMode(ObjectAnimator.REVERSE);
            alpha.setRepeatCount(ObjectAnimator.INFINITE);
            alpha.setRepeatMode(ObjectAnimator.REVERSE);
            
            AnimatorSet set = new AnimatorSet();
            set.playTogether(scaleX, scaleY, alpha);
            set.setDuration(1500);
            set.start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Auto-Check if returning from Settings
        if (isNfcMode) {
             NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
             if (adapter != null && adapter.isEnabled()) {
                 isWaitingForNfc = false;
                 // Dismiss dialogs if possible (not holding ref here but user comes back to view)
                 // Start Reader
                 if (nfcHardwareManager != null) {
                     nfcHardwareManager.enableReaderMode(this, this);
                 }
             } else if (isWaitingForNfc) {
                 // User came back but didn't enable it?
                 // Prompt again or let them stay on screen
             }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isNfcMode && nfcHardwareManager != null) {
            nfcHardwareManager.disableReaderMode(this);
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if (!isNfcMode) return;
        
        Log.d(TAG, "NFC Tag Detected");
        
        // Haptic Feedback
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null) v.vibrate(100);

        executorService.execute(() -> {
            IsoDep isoDep = IsoDep.get(tag);
            if (isoDep == null) return;

            try {
                IsoDepTransceiver transceiver = new IsoDepTransceiver(isoDep);
                transceiver.connect();
                ReadCardDataUseCase useCase = new ReadCardDataUseCase(transceiver);
                CardInputData cardData = useCase.execute();
                transceiver.close();
                
                runOnUiThread(() -> showCardPreviewDialog(cardData));
            } catch (Exception e) {
                Log.e(TAG, "Read Error", e);
                final String failureReason = "Lỗi đọc thẻ: " + e.getMessage();
                Log.d(TAG, "NFC Read Failure Verbose: " + failureReason); // Explicit Log
                runOnUiThread(() -> Toast.makeText(this, failureReason, Toast.LENGTH_LONG).show());
            }
        });
    }

    // Member variables for Dialogs
    private com.google.android.material.bottomsheet.BottomSheetDialog previewDialog;
    private androidx.appcompat.app.AlertDialog nfcEnableDialog;

    private void showCardPreviewDialog(CardInputData data) {
        if (isFinishing()) return;
        
        // Dismiss existing if any
        if (previewDialog != null && previewDialog.isShowing()) {
            previewDialog.dismiss();
        }

        previewDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_card_preview, null);
        previewDialog.setContentView(view);
        previewDialog.setCancelable(false);

        android.widget.TextView tvBank = view.findViewById(R.id.tvBankName);
        android.widget.TextView tvPan = view.findViewById(R.id.tvCardNumber);
        android.widget.TextView tvExpiry = view.findViewById(R.id.tvExpiry);
        android.view.View btnConfirm = view.findViewById(R.id.btnConfirm);
        android.view.View btnRescan = view.findViewById(R.id.btnRescan);

        // Bind Data
        String bank = getBankName(data.getPan());
        tvBank.setText(bank);
        
        // Mask PAN: 9704 **** **** 1234
        String pan = data.getPan();
        if (pan != null && pan.length() > 10) {
            tvPan.setText(pan.substring(0, 4) + " **** **** " + pan.substring(pan.length() - 4));
        } else {
            tvPan.setText(pan);
        }

        // Expiry: YYMM -> MM/YY
        String exp = data.getExpiryDate();
        if (exp != null && exp.length() == 4) {
             tvExpiry.setText(exp.substring(2, 4) + "/" + exp.substring(0, 2));
        } else {
             tvExpiry.setText(exp);
        }

        // Actions
        btnConfirm.setOnClickListener(v -> {
            if (previewDialog != null) previewDialog.dismiss();
            processCardTransaction(data);
        });

        btnRescan.setOnClickListener(v -> {
            if (previewDialog != null) previewDialog.dismiss();
            isWaitingForNfc = false; 
            Toast.makeText(this, "Vui lòng chạm thẻ khác", Toast.LENGTH_SHORT).show();
            // Reader remains active due to onResume
        });

        previewDialog.show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nfcHardwareManager != null) nfcHardwareManager.disableReaderMode(this);
        if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
        if (previewDialog != null && previewDialog.isShowing()) previewDialog.dismiss();
        if (nfcEnableDialog != null && nfcEnableDialog.isShowing()) nfcEnableDialog.dismiss();
    }

    /**
     * Unified Transaction Logic (Sequence Diagram Implementation)
     * STRICT IMPLEMENTATION OF MASTER NARRATIVE
     */
    private void processCardTransaction(CardInputData data) {
        showLoading("Đang xử lý giao dịch...");

        // Check 1: Expiry
        if (CardValidator.isExpired(data.getExpiryDate())) {
             hideLoading();
             showResult(TransactionResultActivity.ResultType.CARD_EXPIRED, "Thẻ đã hết hạn", null, null);
             return;
        }

        // Check 2: Luhn
        if (!CardValidator.isValidLuhn(data.getPan())) {
             hideLoading();
             showResult(TransactionResultActivity.ResultType.INVALID_CARD, "Số thẻ không hợp lệ", null, null);
             return;
        }

        // Check 3: Card Type (Starts with 9704)
        if (!CardValidator.isValidNapasBin(data.getPan())) {
             hideLoading();
             showResult(TransactionResultActivity.ResultType.INVALID_CARD, "Thẻ không hợp lệ (Yêu cầu thẻ nội địa)", null, null);
             return;
        }

        // Check 4: Bank Support
        String bankName = getBankName(data.getPan());
        if ("Unknown Bank".equals(bankName)) {
             hideLoading();
             showResult(TransactionResultActivity.ResultType.INVALID_CARD, "Thẻ không được hỗ trợ", null, null);
             return;
        }

        // Update UI with Bank Name
        runOnUiThread(() -> {
            if (loadingDialog != null) loadingDialog.setMessage("Đang xử lý thẻ " + bankName + "...");
        });

        // 5. Async Network Flow
        executorService.execute(() -> {
            String stan = null;
            String requestHex = null;
            String responseHex = null;
            
            try {
                // Initialize ConfigManager Singleton
                com.example.mysoftpos.utils.ConfigManager config = com.example.mysoftpos.utils.ConfigManager.getInstance(this); // Singleton
                
                // Get Current Values (Atomically Increment)
                stan = config.getAndIncrementTrace();
                String tid = config.getTid();
                String mid = config.getMid();

                // Helper Time
                String transmissionTime = TransactionContext.buildTransmissionDateTime7Now();
                String localTime = TransactionContext.buildLocalTime12Now();
                String localDate = TransactionContext.buildLocalDate13Now();
                
                android.util.Log.d("PurchaseCardActivity", "Building Context: F7=" + transmissionTime + ", F12=" + localTime + ", F13=" + localDate);

                // Calculate RRN
                String serverId = config.getServerId();
                String rrn = TransactionContext.calculateRrn(serverId, stan);

                TransactionContext ctx = new TransactionContext.Builder(TxnType.PURCHASE)
                        .amount4(transactionAmountDigits)
                        .stan11(stan)
                        .transmissionDt7(transmissionTime)
                        .localTime12(localTime)
                        .localDate13(localDate)
                        .rrn37(rrn)
                        .mcc18("5411") 
                        .posEntryMode22(data.getPosEntryMode())
                        .posCondition25("00") 
                        .acquirerId32("970400") 
                        .terminalId41(tid) 
                        .merchantId42(mid)
                        .merchantNameLocation43("MySoftPOS Test       VN")
                        .currency49("704") 
                        .pan2(data.getPan()) 
                        .build();

                IsoMessage isoMsg = IsoRequestBuilder.buildPurchase(ctx, data);
                byte[] packed = com.example.mysoftpos.utils.StandardIsoPacker.pack(isoMsg);
                // Log as HEX for debugging
                requestHex = com.example.mysoftpos.utils.StandardIsoPacker.bytesToHex(packed);

                // Save Pending
                AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
                TransactionEntity entity = new TransactionEntity(stan, transactionAmountDigits, data.getPan(), "PENDING", requestHex, System.currentTimeMillis());
                db.transactionDao().insert(entity);

                // Network (Zero Config - No Context needed for prefs anymore)
                IsoNetworkClient client = new IsoNetworkClient();
                byte[] response = client.sendAndReceive(packed);
                // Log as HEX for debugging
                responseHex = com.example.mysoftpos.utils.StandardIsoPacker.bytesToHex(response);
                
                // SECURITY CHECK: Trace Number Comparison (DE 11)
                // Extract DE 11 from response
                String receivedStan = com.example.mysoftpos.utils.StandardIsoPacker.unpackField(response, 11);
                
                // If receivedStan is null or mismatch -> Security Alert
                if (receivedStan == null || !receivedStan.equals(stan)) {
                    throw new SecurityException("Lỗi hệ thống: Trace Mismatch (Req:" + stan + " != Res:" + receivedStan + ")");
                }

                // Validate Response Code (DE 39)
                String respCode = com.example.mysoftpos.utils.StandardIsoPacker.unpackResponseCode(response);
                boolean isApproved = "00".equals(respCode);
                String status = isApproved ? "APPROVED" : "FAILED";

                // Update DB logic
                db.transactionDao().updateResponse(stan, responseHex, status);

                hideLoading();
                
                if (isApproved) {
                    // Increment Counters (STAN handled atomically at start)
                    // com.example.mysoftpos.utils.ConfigManager.getInstance(getApplicationContext()).incrementTrace();
                    com.example.mysoftpos.utils.ConfigManager.getInstance(getApplicationContext()).incrementTidAndMid();

                    final String res = responseHex;
                    final String req = requestHex;
                    runOnUiThread(() -> showResult(TransactionResultActivity.ResultType.SUCCESS, "Giao dịch thành công", res, req));
                } else {
                    final String res = responseHex;
                    final String req = requestHex;
                    runOnUiThread(() -> showResult(TransactionResultActivity.ResultType.TRANSACTION_FAILED, "Giao dịch thất bại (Mã lỗi: " + respCode + ")", res, req));
                }

            } catch (Exception e) {
                Log.e(TAG, "Transaction Error", e);
                if (stan != null) {
                    try { AppDatabase.getDatabase(getApplicationContext()).transactionDao().updateStatus(stan, "FAILED"); } catch(Exception ex) {}
                }
                hideLoading();
                
                String errorMsg = e.getMessage();
                if (errorMsg == null) errorMsg = "Lỗi không xác định";
                
                final String finalReason = errorMsg;
                final String finalReq = requestHex;
                final String finalRes = responseHex;
                runOnUiThread(() -> showResult(TransactionResultActivity.ResultType.SYSTEM_ERROR, finalReason, finalRes, finalReq));
            }
        });
    }

    private String getBankName(String pan) {
        if (pan == null || pan.length() < 6) return "Unknown Bank";
        String bin = pan.substring(0, 6);
        
        // BIDV
        if (bin.equals("970418")) return "BIDV";
        if (bin.equals("970488")) return "BIDV";
        
        // Others
        if (bin.equals("970403")) return "Sacombank";
        if (bin.equals("970415")) return "VietinBank";
        if (bin.equals("970436")) return "Vietcombank";
        if (bin.equals("970405")) return "Agribank";
        if (bin.equals("970423")) return "TPBank";
        if (bin.equals("970448")) return "OCB";
        if (bin.equals("970419")) return "NCB";
        
        // Generic Napas
        if (bin.startsWith("9704")) return "NAPAS Bank"; 
        
        return "Unknown Bank";
    }

    private void showLoading(String msg) {
        runOnUiThread(() -> {
            if (loadingDialog == null) {
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
                builder.setCancelable(false);
                builder.setMessage(msg);
                loadingDialog = builder.create();
            } else {
                loadingDialog.setMessage(msg);
            }
            loadingDialog.show();
        });
    }

    private void hideLoading() {
        runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
        });
    }

    private void showResult(TransactionResultActivity.ResultType type, String message, String isoResponse, String isoRequest) {
        Intent intent = new Intent(this, TransactionResultActivity.class);
        intent.putExtra(TransactionResultActivity.EXTRA_RESULT_TYPE, type);
        intent.putExtra(TransactionResultActivity.EXTRA_MESSAGE, message);
        intent.putExtra(TransactionResultActivity.EXTRA_ISO_RESPONSE, isoResponse);
        intent.putExtra(TransactionResultActivity.EXTRA_ISO_REQUEST, isoRequest);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
