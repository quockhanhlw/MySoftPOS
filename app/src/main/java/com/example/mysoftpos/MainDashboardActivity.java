package com.example.mysoftpos;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.util.Log;
import android.nfc.tech.IsoDep;
import android.widget.Toast;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.domain.usecase.ReadCardDataUseCase;
import com.example.mysoftpos.data.IsoDepTransceiver;
import com.example.mysoftpos.data.NfcHardwareManager;

import com.example.mysoftpos.utils.CardValidator;
import com.example.mysoftpos.utils.ConfigManager;
import com.example.mysoftpos.iso8583.IsoRequestBuilder;
import com.example.mysoftpos.iso8583.IsoMessage;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.TransactionEntity;
import com.example.mysoftpos.data.remote.IsoNetworkClient;
import com.example.mysoftpos.utils.StandardIsoPacker;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainDashboardActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    private NfcHardwareManager nfcHardwareManager;

    private static final String TAG = "MainDashboardActivity";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private String transactionAmount = "0"; // Default
    
    // UX Components
    private com.google.android.material.bottomsheet.BottomSheetDialog nfcScanDialog;
    private androidx.appcompat.app.AlertDialog loadingDialog;

    private TxnType currentTxnType = TxnType.PURCHASE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_dashboard);

        nfcHardwareManager = new NfcHardwareManager(this);

        // Initialize views
        CardView cardPurchase = findViewById(R.id.cardPurchase);
        CardView cardBalance = findViewById(R.id.cardBalance); 
        CardView cardHistory = findViewById(R.id.cardHistory);
        CardView cardLogon = findViewById(R.id.cardLogon);
        CardView cardSettings = findViewById(R.id.cardSettings);

        // Purchase Flow
        cardPurchase.setOnClickListener(v -> {
            currentTxnType = TxnType.PURCHASE;
            Intent intent = new Intent(MainDashboardActivity.this, PurchaseAmountActivity.class);
            startActivity(intent);
        });

        // Balance Inquiry Flow (Bypass Amount)
        cardBalance.setOnClickListener(v -> {
             currentTxnType = TxnType.BALANCE_INQUIRY;
             transactionAmount = "0"; 
             showPaymentMethodDialog();
        });

        cardHistory.setOnClickListener(v -> {
            Toast.makeText(this, "Lịch sử - Coming soon", Toast.LENGTH_SHORT).show();
        });

        cardLogon.setOnClickListener(v -> {
            Toast.makeText(this, "Logon - Coming soon", Toast.LENGTH_SHORT).show();
        });

        cardSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainDashboardActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // Retrieve Amount if returning from Amount Activity
        if (getIntent() != null && getIntent().hasExtra("AMOUNT")) {
            transactionAmount = getIntent().getStringExtra("AMOUNT");
            Log.d(TAG, "Amount received: " + transactionAmount);
            currentTxnType = TxnType.PURCHASE; 
        }
    }

    private void showPaymentMethodDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_payment_method, null);
        dialog.setContentView(view);
        
        view.findViewById(R.id.cardNfc).setOnClickListener(v -> {
            dialog.dismiss();
            checkNfcAndShowDialog();
        });

        view.findViewById(R.id.cardManual).setOnClickListener(v -> {
            dialog.dismiss();
            showManualEntryDialog();
        });

        dialog.show();
    }
    
    private void checkNfcAndShowDialog() {
        // --- MOCK NFC BYPASS (TEMPORARY FIX) ---
        // Requirement: "Immediately execute logic with Hardcoded Track 2"
        // Raw: 9704189991010867647=3101601000000001230
        
        String rawTrack2 = "9704189991010867647=3101601000000001230";
        String pan = "9704189991010867647";
        String expiry = "3101"; // YYMM
        
        // Pass "071" as posEntryMode, and pass track2 so isContactless() returns true.
        CardInputData mockData = new CardInputData(pan, expiry, "071", rawTrack2);

        Toast.makeText(this, "MOCK NFC Triggered", Toast.LENGTH_SHORT).show();
        processCardTransaction(mockData);
        return; 

        // --- REAL LOGIC COMMENTED OUT FOR TESTING ---
        /*
        android.nfc.NfcAdapter adapter = android.nfc.NfcAdapter.getDefaultAdapter(this);
        if (adapter == null) {
            Toast.makeText(this, "Thiết bị không hỗ trợ NFC", Toast.LENGTH_LONG).show();
            return;
        }

        if (!adapter.isEnabled()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("NFC chưa bật")
                .setMessage("Vui lòng bật NFC để sử dụng tính năng này.")
                .setPositiveButton("Cài đặt", (d, w) -> {
                    startActivity(new Intent(android.provider.Settings.ACTION_NFC_SETTINGS));
                })
                .setNegativeButton("Hủy", null)
                .show();
            return;
        }

        // Show Ready to Scan Dialog
        if (nfcScanDialog == null) {
            nfcScanDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
            android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.layout_scan_nfc, null);
            nfcScanDialog.setContentView(view);
            nfcScanDialog.setCancelable(false);
            
            view.findViewById(R.id.btnCancelNfc).setOnClickListener(v -> nfcScanDialog.dismiss());
        }
        nfcScanDialog.show();
        */
    }

    private void showManualEntryDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_manual_entry, null);
        builder.setView(view);
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        com.google.android.material.textfield.TextInputEditText etCardNumber = view.findViewById(R.id.etCardNumber);
        com.google.android.material.textfield.TextInputEditText etExpiry = view.findViewById(R.id.etExpiry);
        com.google.android.material.button.MaterialButton btnConfirm = view.findViewById(R.id.btnConfirmManual);
        android.widget.TextView tvBankName = view.findViewById(R.id.tvBankName);

        // Formatting Logic
        etCardNumber.addTextChangedListener(new android.text.TextWatcher() {
            private boolean isFormatting;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (isFormatting) return;
                isFormatting = true;
                
                String raw = s.toString().replace(" ", "");
                
                // BIN Check
                if (raw.length() >= 6) {
                    tvBankName.setText(getBankName(raw.substring(0, 6)));
                } else {
                    tvBankName.setText("");
                }

                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < raw.length(); i++) {
                    if (i > 0 && i % 4 == 0) formatted.append(' ');
                    formatted.append(raw.charAt(i));
                }
                
                if (!formatted.toString().equals(s.toString())) {
                    etCardNumber.setText(formatted.toString());
                    etCardNumber.setSelection(formatted.length());
                }
                
                checkManualInputs(raw, etExpiry.getText().toString(), btnConfirm);
                isFormatting = false;
            }
        });

        etExpiry.addTextChangedListener(new android.text.TextWatcher() {
            private boolean isFormatting;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (isFormatting) return;
                isFormatting = true;
                
                String raw = s.toString().replace("/", "");
                if (raw.length() >= 2 && !s.toString().contains("/")) {
                    String formatted = raw.substring(0, 2) + "/" + raw.substring(2);
                    etExpiry.setText(formatted);
                    etExpiry.setSelection(formatted.length());
                }
                
                checkManualInputs(etCardNumber.getText().toString().replace(" ", ""), raw, btnConfirm);
                isFormatting = false;
            }
        });

        btnConfirm.setOnClickListener(v -> {
            String pan = etCardNumber.getText().toString().replace(" ", "");
            String expiry = etExpiry.getText().toString().replace("/", "");
            
            // Assume input is strict YYMM or MMYY
            // We use raw expiry for now.
            CardInputData data = new CardInputData(pan, expiry, "011", null);
            dialog.dismiss();
            processCardTransaction(data);
        });

        dialog.show();
    }
    
    private void checkManualInputs(String rawPan, String rawExpiry, com.google.android.material.button.MaterialButton btn) {
        btn.setEnabled(rawPan.length() >= 12 && rawExpiry.length() == 4);
    }

    private String getBankName(String bin) {
        if (bin.startsWith("970403")) return "Sacombank";
        if (bin.startsWith("970415")) return "VietinBank";
        if (bin.startsWith("970436")) return "Vietcombank";
        if (bin.startsWith("970418")) return "BIDV";
        if (bin.startsWith("970405")) return "Agribank";
        if (bin.startsWith("970423")) return "TPBank";
        if (bin.startsWith("9704")) return "NAPAS";
        return "Unknown Bank";
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcHardwareManager != null) {
            nfcHardwareManager.enableReaderMode(this, this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcHardwareManager != null) {
            nfcHardwareManager.disableReaderMode(this);
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        Log.d(TAG, "Card Detected! Tag ID: " + tag.toString());
        
        if (nfcScanDialog != null && nfcScanDialog.isShowing()) {
            nfcScanDialog.dismiss();
        }
        executorService.execute(() -> handleNfcInput(tag));
    }

    private void handleNfcInput(Tag tag) {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            runOnUiThread(() -> Toast.makeText(this, "Not an IsoDep tag", Toast.LENGTH_SHORT).show());
            return;
        }

        try {
            IsoDepTransceiver transceiver = new IsoDepTransceiver(isoDep);
            transceiver.connect();

            ReadCardDataUseCase useCase = new ReadCardDataUseCase(transceiver);
            CardInputData cardData = useCase.execute();

            transceiver.close();
            Log.d(TAG, "NFC Read Success: " + cardData.toString());
            runOnUiThread(() -> processCardTransaction(cardData));

        } catch (Exception e) {
            Log.e(TAG, "NFC Read Failed", e);
            runOnUiThread(() -> Toast.makeText(this, "Read Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private void processCardTransaction(CardInputData data) {
        showLoading("Đang xử lý giao dịch...");

        // 1. Validation Logic
        if (currentTxnType == TxnType.PURCHASE && !CardValidator.isValidAmount(transactionAmount)) {
             hideLoading();
             showResult(TransactionResultActivity.ResultType.LIMIT_EXCEEDED, "Số tiền không hợp lệ", null, currentTxnType);
             return;
        }

        if (data.getPan() == null || data.getExpiryDate() == null) {
            hideLoading();
            showResult(TransactionResultActivity.ResultType.INVALID_CARD, "Dữ liệu thẻ bị thiếu", null, currentTxnType);
            return;
        }
        
        if (CardValidator.isExpired(data.getExpiryDate())) {
             hideLoading();
             showResult(TransactionResultActivity.ResultType.CARD_EXPIRED, null, null, currentTxnType);
             return;
        }

        if (!CardValidator.isValidLuhn(data.getPan())) {
             hideLoading();
             showResult(TransactionResultActivity.ResultType.INVALID_CARD, "Lỗi Luhn Check", null, currentTxnType);
             return;
        }
        
        // Blocking Non-Napas logic for strictness?
        // Let's assume warning only or skip for now to allow testing.
        
        runOnUiThread(() -> {
            if (loadingDialog != null) loadingDialog.setMessage("Đang xử lý " + (currentTxnType == TxnType.BALANCE_INQUIRY ? "Vấn tin" : "Thanh toán") + "...");
        });

        // 2. Async ISO Flow
        executorService.execute(() -> {
            String stan = null;
            try {
                // Config & Context
                // Config & Context
                ConfigManager config = ConfigManager.getInstance(getApplicationContext());
                stan = config.getAndIncrementTrace();
                // config.incrementTrace(); // Removing separate call to avoid duplication/race
                
                String serverId = config.getServerId(); // Default "01"
                String rrn = TransactionContext.calculateRrn(serverId, stan);

                TransactionContext ctx = new TransactionContext.Builder(currentTxnType)
                        .amount4(currentTxnType == TxnType.BALANCE_INQUIRY ? "0" : transactionAmount)
                        .stan11(stan)
                        .rrn37(rrn)
                        .build();

                // Build ISO Message
                IsoMessage isoMsg;
                if (currentTxnType == TxnType.BALANCE_INQUIRY) {
                    isoMsg = IsoRequestBuilder.buildBalanceInquiry(ctx, data);
                } else {
                    isoMsg = IsoRequestBuilder.buildPurchase(ctx, data);
                }
                
                // Pack
                byte[] requestBytes = StandardIsoPacker.pack(isoMsg);
                String requestHex = StandardIsoPacker.bytesToHex(requestBytes);

                // Save Pending
                TransactionEntity entity = new TransactionEntity(
                        stan,
                        currentTxnType == TxnType.BALANCE_INQUIRY ? "0" : transactionAmount,
                        data.getPan(), 
                        "PENDING",
                        requestHex,
                        System.currentTimeMillis()
                );
                
                AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
                db.transactionDao().insert(entity);

                // Network (New TPDU Logic)
                IsoNetworkClient networkClient = new IsoNetworkClient(); 
                byte[] responseBytes = networkClient.sendAndReceive(requestBytes);
                String responseHex = StandardIsoPacker.bytesToHex(responseBytes);

                // Unpack Code
                String respCode = StandardIsoPacker.unpackResponseCode(responseBytes);
                boolean isApproved = "00".equals(respCode);
                String finalStatus = isApproved ? "APPROVED" : "FAILED";

                // Update DB
                db.transactionDao().updateResponse(stan, responseHex, finalStatus);

                // Result
                hideLoading();
                runOnUiThread(() -> {
                    if (isApproved) {
                        showResult(TransactionResultActivity.ResultType.SUCCESS, null, responseHex, currentTxnType);
                    } else {
                        showResult(TransactionResultActivity.ResultType.TRANSACTION_FAILED, "Mã lỗi: " + respCode, responseHex, currentTxnType);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Transaction Failure", e);
                
                // Fail DB
                if (stan != null) {
                    try {
                        AppDatabase.getDatabase(getApplicationContext()).transactionDao().updateStatus(stan, "FAILED");
                    } catch (Exception ex) { Log.e(TAG, "DB Update Error", ex); }
                }

                String reason = e.getMessage();
                if (e instanceof java.net.SocketTimeoutException) reason = "Timeout: Server không phản hồi";
                else if (e instanceof java.io.IOException) reason = "Lỗi kết nối mạng";
                
                String finalReason = reason;
                hideLoading();
                runOnUiThread(() -> showResult(TransactionResultActivity.ResultType.SYSTEM_ERROR, finalReason, null, currentTxnType));
            }
        });
    }

    // --- UI Helpers ---

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

    private void showResult(TransactionResultActivity.ResultType type, String message, String isoResponse, TxnType txnType) {
        Intent intent = new Intent(this, TransactionResultActivity.class);
        intent.putExtra(TransactionResultActivity.EXTRA_RESULT_TYPE, type);
        intent.putExtra(TransactionResultActivity.EXTRA_MESSAGE, message);
        intent.putExtra(TransactionResultActivity.EXTRA_ISO_RESPONSE, isoResponse);
        intent.putExtra("TXN_TYPE", txnType); 
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }
}
