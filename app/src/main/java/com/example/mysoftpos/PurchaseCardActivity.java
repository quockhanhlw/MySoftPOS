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

import com.example.mysoftpos.viewmodel.TransactionState;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PurchaseCardActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    private static final String TAG = "PurchaseCard";
    private NfcAdapter nfcAdapter;
    private ConfigManager configManager;
    private com.example.mysoftpos.viewmodel.PurchaseViewModel viewModel;

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

        // VM Init
        com.example.mysoftpos.ui.GlobalViewModelFactory factory = new com.example.mysoftpos.ui.GlobalViewModelFactory(
                com.example.mysoftpos.di.ServiceLocator.getInstance(this));
        viewModel = new androidx.lifecycle.ViewModelProvider(this, factory)
                .get(com.example.mysoftpos.viewmodel.PurchaseViewModel.class);

        // Intent Data
        String typeStr = getIntent().getStringExtra("TXN_TYPE");
        amount = getIntent().getStringExtra("AMOUNT");
        if (amount == null)
            amount = "0";
        txnType = "BALANCE_INQUIRY".equals(typeStr) ? TxnType.BALANCE_INQUIRY : TxnType.PURCHASE;

        // Observe State
        viewModel.getState().observe(this, state -> {
            showLoading(state.isLoading);
            if (state.isSuccess) {
                showResult(true, state.message, state.isoResponse, state.isoRequest);
            } else if (state.message != null && !state.isLoading) {
                // Error (Now includes ISO Data for "Failed" transactions)
                showResult(false, state.message, state.isoResponse, state.isoRequest);
            }
        });

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

            CardInputData manual = new CardInputData(pan, exp, "011", null);
            processTransaction(manual);
        });

        // Bin Resolver
        TextView tvBank = findViewById(R.id.tvBankName);
        etPan.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

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
            String mockTrk2 = configManager.getTrack2("022"); // Centralized (Magstripe for Manual Trigger)
            String mockPan = configManager.getMockPan();
            String mockExp = configManager.getMockExpiry();

            if (mockTrk2 != null && mockTrk2.contains("=")) {
                String[] parts = mockTrk2.split("=");
                mockPan = parts[0];
                if (parts[1].length() >= 4)
                    mockExp = parts[1].substring(0, 4);
            }

            CardInputData mockData = new CardInputData(
                    mockPan,
                    mockExp,
                    "022",
                    mockTrk2);
            Toast.makeText(this, "Mock Triggered (022)...", Toast.LENGTH_SHORT).show();
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
        if (nfcAdapter != null)
            nfcAdapter.disableReaderMode(this);
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
        if (nfcAdapter != null)
            nfcAdapter.disableReaderMode(this);

        // Animate Card Entry
        View cardContainer = findViewById(R.id.cardInputContainer);
        android.view.animation.Animation slideUp = android.view.animation.AnimationUtils.loadAnimation(this,
                R.anim.slide_in_bottom);
        cardContainer.startAnimation(slideUp);
    }

    private void enableNfcReader() {
        if (nfcAdapter != null) {
            nfcAdapter.enableReaderMode(this, this,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
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
        if (nfcAdapter != null)
            nfcAdapter.disableReaderMode(this);
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null)
            return;

        new Thread(() -> {
            try {
                IsoDepTransceiver transceiver = new IsoDepTransceiver(isoDep);
                transceiver.connect();
                ReadCardDataUseCase useCase = new ReadCardDataUseCase(transceiver);
                CardInputData data = useCase.execute();
                transceiver.close();

                // --- MOCK TEST FORCE (User Request) ---
                // Centralized via ConfigManager
                String mockTrk2 = configManager.getTrack2("072"); // Use 072 for NFC
                String mockPan = configManager.getMockPan();
                String mockExp = configManager.getMockExpiry();

                // Parse PAN/Exp from Track 2 if available
                if (mockTrk2 != null && mockTrk2.contains("=")) {
                    String[] parts = mockTrk2.split("=");
                    mockPan = parts[0];
                    if (parts[1].length() >= 4)
                        mockExp = parts[1].substring(0, 4);
                }

                // Override with Mock Data
                CardInputData mockOverrideData = new CardInputData(
                        mockPan,
                        mockExp,
                        "072", // NFC Mode
                        mockTrk2);
                if (data != null) {
                    mockOverrideData.setEmvTags(data.getEmvTags());
                }
                // IF we needed PIN, we would call mockOverrideData.setPinBlock(...)

                runOnUiThread(() -> {
                    Toast.makeText(this, "NFC Read Success (Config MOCK)", Toast.LENGTH_SHORT).show();
                    processTransaction(mockOverrideData);
                });
            } catch (Exception e) {
                // FALLBACK MOCK (Centralized)
                String mockTrk2 = configManager.getTrack2("072");
                String mockPan = configManager.getMockPan();
                String mockExp = configManager.getMockExpiry();

                if (mockTrk2 != null && mockTrk2.contains("=")) {
                    String[] parts = mockTrk2.split("=");
                    mockPan = parts[0];
                    if (parts[1].length() >= 4)
                        mockExp = parts[1].substring(0, 4);
                }

                CardInputData mockData = new CardInputData(
                        mockPan,
                        mockExp,
                        "072",
                        mockTrk2);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Using Mock Data (Read Error bypassed)", Toast.LENGTH_SHORT).show();
                    processTransaction(mockData);
                });
            }
        }).start();
    }

    private void processTransaction(CardInputData card) {
        viewModel.processTransaction(card, amount, txnType);
    }

    private void showLoading(boolean loading) {
        View v = findViewById(R.id.layoutLoading);
        if (v != null)
            v.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showResult(boolean success, String msg, String isoResp, String isoReq) {
        Intent i = new Intent(this, TransactionResultActivity.class);
        if (success) {
            i.putExtra(TransactionResultActivity.EXTRA_RESULT_TYPE, TransactionResultActivity.ResultType.SUCCESS);
        } else {
            i.putExtra(TransactionResultActivity.EXTRA_RESULT_TYPE,
                    TransactionResultActivity.ResultType.TRANSACTION_FAILED);
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
        if (raw == null)
            return "0000";
        // Remove non-digits
        return raw.replaceAll("[^0-9]", "");
    }
}
