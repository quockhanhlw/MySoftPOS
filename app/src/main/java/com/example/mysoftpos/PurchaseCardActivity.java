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
import com.google.android.material.textfield.TextInputEditText;

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
    private TextInputEditText etPan, etExp;
    
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
        tvTitle = findViewById(R.id.tvTitle);
        tvAmountDisplay = findViewById(R.id.tvAmountDisplay);
        layoutSelection = findViewById(R.id.layoutMethodSelection);
        layoutNfc = findViewById(R.id.layoutNfcMode);
        layoutManual = findViewById(R.id.layoutManualMode);
        
        etPan = findViewById(R.id.etCardNumber);
        etExp = findViewById(R.id.etExpiry);

        tvTitle.setText("Thanh toán");
        tvAmountDisplay.setText(amount + " VND");

        // --- SELECTION LOGIC ---
        findViewById(R.id.cardOptionNfc).setOnClickListener(v -> showNfcMode());
        findViewById(R.id.cardOptionManual).setOnClickListener(v -> showManualMode());

        // --- BACK NAVIGATION LOGIC ---
        findViewById(R.id.btnBackToSelectionFromNfc).setOnClickListener(v -> showSelectionMode());
        findViewById(R.id.btnBackToSelectionFromManual).setOnClickListener(v -> showSelectionMode());

        // --- MANUAL PROCESSING LOGIC ---
        findViewById(R.id.btnProcess).setOnClickListener(v -> {
            String pan = etPan.getText().toString().trim();
            String exp = etExp.getText().toString().trim();
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
    
    @Override
    public void onBackPressed() {
        if (layoutSelection.getVisibility() != View.VISIBLE) {
            showSelectionMode();
        } else {
            super.onBackPressed();
        }
    }

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

                runOnUiThread(() -> {
                    Toast.makeText(this, "NFC Read Success", Toast.LENGTH_SHORT).show();
                    processTransaction(data);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "NFC Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void processTransaction(CardInputData card) {
        executor.execute(() -> {
            TransactionContext ctx = new TransactionContext();
            TransactionEntity entity = new TransactionEntity();

            try {
                // Validation
                boolean isPurchase = (txnType == TxnType.PURCHASE);
                String amtF4 = isPurchase ? TransactionContext.formatAmount12(amount) : "000000000000";
                
                TransactionValidator.ValidationResult v = TransactionValidator.validate(card, amount, isPurchase);
                if (v != TransactionValidator.ValidationResult.VALID) {
                    runOnUiThread(() -> showResult(false, "Validation Failed: " + v));
                    return;
                }

                // Context
                ctx.txnType = txnType;
                ctx.amount4 = amtF4;
                ctx.stan11 = configManager.getAndIncrementTrace();
                ctx.generateDateTime();
                ctx.rrn37 = TransactionContext.calculateRrn(configManager.getServerId(), ctx.stan11);
                ctx.terminalId41 = configManager.getTerminalId();
                ctx.merchantId42 = configManager.getMerchantId();
                ctx.ip = configManager.getServerIp();
                ctx.port = configManager.getServerPort();

                // Build
                IsoMessage req = (txnType == TxnType.BALANCE_INQUIRY)
                        ? Iso8583Builder.buildBalanceMsg(ctx, card)
                        : Iso8583Builder.buildPurchaseMsg(ctx, card);
                
                byte[] packed = StandardIsoPacker.pack(req);

                // Log
                entity.traceNumber = ctx.stan11;
                entity.amount = amount;
                entity.pan = card.getPan();
                entity.status = "PENDING";
                entity.requestHex = StandardIsoPacker.bytesToHex(packed);
                entity.timestamp = System.currentTimeMillis();
                appDatabase.transactionDao().insert(entity);

                // Send (Double Safety)
                IsoNetworkClient client = new IsoNetworkClient(ctx.ip, ctx.port);
                byte[] resp;
                try {
                    resp = client.sendAndReceive(packed);
                } catch (SocketTimeoutException e) {
                    handleAutoReversal(ctx, card, entity);
                    return;
                }

                IsoMessage respMsg = new StandardIsoPacker().unpack(resp);
                String rc = respMsg.getField(IsoField.RESPONSE_CODE_39);
                entity.responseHex = StandardIsoPacker.bytesToHex(resp);
                entity.status = "00".equals(rc) ? "APPROVED" : "DECLINED " + rc;
                appDatabase.transactionDao().update(entity);
                
                runOnUiThread(() -> showResult("00".equals(rc), "RC: " + rc));

            } catch (Exception e) {
                Log.e(TAG, "Error", e);
                runOnUiThread(() -> showResult(false, "Error: " + e.getMessage()));
            }
        });
    }

    private void handleAutoReversal(TransactionContext ctx, CardInputData card, TransactionEntity entity) {
        try {
            String newTrace = configManager.getAndIncrementTrace();
            IsoMessage rev = Iso8583Builder.buildReversalAdvice(ctx, card, newTrace);
            byte[] packedRev = StandardIsoPacker.pack(rev);
            
            entity.status = "TIMEOUT_REVERSAL_INIT";
            appDatabase.transactionDao().update(entity);

            IsoNetworkClient revClient = new IsoNetworkClient(ctx.ip, ctx.port);
            revClient.sendAndReceive(packedRev);
            
            entity.status = "TIMEOUT_REVERSED";
            appDatabase.transactionDao().update(entity);
            runOnUiThread(() -> showResult(false, "Giao dịch đã hủy (Timeout)"));

        } catch (Exception e) {
             entity.status = "TIMEOUT_REVERSAL_FAILED";
             appDatabase.transactionDao().update(entity);
             runOnUiThread(() -> showResult(false, "Lỗi time out"));
        }
    }

    private void showResult(boolean success, String msg) {
        Intent i = new Intent(this, TransactionResultActivity.class);
        i.putExtra("SUCCESS", success);
        i.putExtra("MESSAGE", msg);
        startActivity(i);
        finish();
    }
}
