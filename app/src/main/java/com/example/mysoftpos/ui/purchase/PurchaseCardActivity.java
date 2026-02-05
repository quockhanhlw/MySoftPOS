package com.example.mysoftpos.ui.purchase;

import com.example.mysoftpos.ui.result.TransactionResultActivity;

import com.example.mysoftpos.R;
import com.example.mysoftpos.ui.base.GlobalViewModelFactory;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.util.Locale;

import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.utils.config.ConfigManager;
import com.example.mysoftpos.viewmodel.PurchaseViewModel;
import com.google.android.material.tabs.TabLayout;

/**
 * Purchase Card Activity with Tab-based UI.
 * 
 * Tab 0: Manual Entry (PAN + Expiry) -> DE 22 = 012
 * Tab 1: Mock Track 2 -> DE 22 = 022
 */
public class PurchaseCardActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    private NfcAdapter nfcAdapter;
    private PurchaseViewModel viewModel;
    private ConfigManager configManager;

    private String amount;
    private String currency;
    private String currencyCode;
    private TxnType txnType;

    private TextView tvAmountDisplay;
    private FrameLayout layoutLoading;
    private CardView cardManualEntry;
    private CardView cardMockTrack2;
    private EditText etPan;
    private EditText etExpiry;
    private int currentMode = 0; // 0 = Manual, 1 = Mock

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_card);

        // Core Init
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        configManager = ConfigManager.getInstance(this);

        // VM Init
        com.example.mysoftpos.ui.base.GlobalViewModelFactory factory = new com.example.mysoftpos.ui.base.GlobalViewModelFactory(
                com.example.mysoftpos.di.ServiceLocator.getInstance(this));
        viewModel = new androidx.lifecycle.ViewModelProvider(this, factory)
                .get(PurchaseViewModel.class);

        // Intent Data
        String typeStr = getIntent().getStringExtra("TXN_TYPE");
        amount = getIntent().getStringExtra("AMOUNT");
        currency = getIntent().getStringExtra("CURRENCY");
        currencyCode = getIntent().getStringExtra("CURRENCY_CODE");
        if (amount == null)
            amount = "0";
        if (currency == null)
            currency = "VND";
        if (currencyCode == null)
            currencyCode = "704";
        txnType = "BALANCE_INQUIRY".equals(typeStr) ? TxnType.BALANCE_INQUIRY : TxnType.PURCHASE;

        // Observe State
        viewModel.getState().observe(this, state -> {
            showLoading(state.isLoading);
            if (state.isSuccess) {
                showResult(true, state.message, state.isoResponse, state.isoRequest);
            } else if (state.message != null && !state.isLoading) {
                showResult(false, state.message, state.isoResponse, state.isoRequest);
            }
        });

        // Bind Views
        ImageButton btnBack = findViewById(R.id.btnBack);
        tvAmountDisplay = findViewById(R.id.tvAmountDisplay);
        layoutLoading = findViewById(R.id.layoutLoading);
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        cardManualEntry = findViewById(R.id.cardManualEntry);
        cardMockTrack2 = findViewById(R.id.cardMockTrack2);
        etPan = findViewById(R.id.etPan);
        etExpiry = findViewById(R.id.etExpiry);
        View btnSubmitManual = findViewById(R.id.btnSubmitManual);
        View cardNfcIcon = findViewById(R.id.cardNfcIcon);
        TextView tvMockPreview = findViewById(R.id.tvMockTrack2Preview);

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Display amount
        if (txnType == TxnType.BALANCE_INQUIRY) {
            findViewById(R.id.amountContainer).setVisibility(View.GONE);
        } else {
            tvAmountDisplay.setText(formatAmount(amount));
        }

        // Mock Track 2 Preview
        String mockTrack2 = configManager.getTrack2("022");
        if (mockTrack2 != null && mockTrack2.length() > 25) {
            tvMockPreview.setText(mockTrack2.substring(0, 25) + "...");
        } else {
            tvMockPreview.setText(mockTrack2);
        }

        // Tab Selection
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentMode = tab.getPosition();
                updateCardVisibility();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        // Manual Entry Submit
        btnSubmitManual.setOnClickListener(v -> {
            String pan = etPan.getText().toString().replaceAll("\\s", "");
            String expiry = etExpiry.getText().toString();

            if (pan.length() < 13 || expiry.length() != 4) {
                Toast.makeText(this, "Invalid PAN or Expiry", Toast.LENGTH_SHORT).show();
                return;
            }

            CardInputData manualData = new CardInputData(pan, expiry, "012", null);
            processTransaction(manualData);
        });

        // Mock Track 2 Tap
        cardNfcIcon.setOnClickListener(v -> {
            String trk2 = configManager.getTrack2("022");
            String mockPan = configManager.getMockPan();
            String mockExp = configManager.getMockExpiry();

            if (trk2 != null && trk2.contains("=")) {
                String[] parts = trk2.split("=");
                mockPan = parts[0];
                if (parts[1].length() >= 4)
                    mockExp = parts[1].substring(0, 4);
            }

            CardInputData mockData = new CardInputData(mockPan, mockExp, "022", trk2);
            Toast.makeText(this, "Using Mock Track 2...", Toast.LENGTH_SHORT).show();
            processTransaction(mockData);
        });
    }

    private void updateCardVisibility() {
        if (currentMode == 0) {
            cardManualEntry.setVisibility(View.VISIBLE);
            cardMockTrack2.setVisibility(View.GONE);
        } else {
            cardManualEntry.setVisibility(View.GONE);
            cardMockTrack2.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null && currentMode == 1) {
            nfcAdapter.enableReaderMode(this, this,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                            | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableReaderMode(this);
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null)
            return;

        // Use mock data for NFC (real implementation would read card)
        String trk2 = configManager.getTrack2("022");
        String mockPan = configManager.getMockPan();
        String mockExp = configManager.getMockExpiry();

        if (trk2 != null && trk2.contains("=")) {
            String[] parts = trk2.split("=");
            mockPan = parts[0];
            if (parts[1].length() >= 4)
                mockExp = parts[1].substring(0, 4);
        }

        CardInputData mockData = new CardInputData(mockPan, mockExp, "022", trk2);
        runOnUiThread(() -> {
            Toast.makeText(this, "Card Detected", Toast.LENGTH_SHORT).show();
            processTransaction(mockData);
        });
    }

    private void processTransaction(CardInputData card) {
        viewModel.processTransaction(card, amount, currencyCode, txnType);
    }

    private void showLoading(boolean loading) {
        if (layoutLoading != null)
            layoutLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showResult(boolean success, String msg, String isoResp, String isoReq) {
        Intent intent = new Intent(this, TransactionResultActivity.class);
        intent.putExtra("TXN_TYPE", txnType.name());
        intent.putExtra("SUCCESS", success);
        intent.putExtra(TransactionResultActivity.EXTRA_RESULT_TYPE,
                success ? TransactionResultActivity.ResultType.SUCCESS
                        : TransactionResultActivity.ResultType.TRANSACTION_FAILED);
        intent.putExtra(TransactionResultActivity.EXTRA_MESSAGE, msg);
        intent.putExtra("AMOUNT", amount);
        intent.putExtra("CURRENCY", currency);
        if (isoResp != null)
            intent.putExtra("RAW_RESPONSE", isoResp);
        if (isoReq != null)
            intent.putExtra("RAW_REQUEST", isoReq);
        startActivity(intent);
        finish();
    }

    private String formatAmount(String amt) {
        try {
            long val = Long.parseLong(amt);
            return String.format(Locale.ROOT, "%,d", val);
        } catch (NumberFormatException e) {
            return amt;
        }
    }
}
