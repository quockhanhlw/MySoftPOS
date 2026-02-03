package com.example.mysoftpos.ui.balance;
import com.example.mysoftpos.ui.result.TransactionResultActivity;

import com.example.mysoftpos.R;
import com.example.mysoftpos.ui.base.GlobalViewModelFactory;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.utils.config.ConfigManager;
import com.example.mysoftpos.viewmodel.PurchaseViewModel;

/**
 * Balance Inquiry Activity with Tab-based UI.
 * 
 * Tab 0: Manual Entry (PAN + Expiry) -> DE 22 = 012
 * Tab 1: Mock Track 2 -> DE 22 = 902
 */
public class BalanceInquiryActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private CardView cardManualEntry;
    private CardView cardMockTrack2;
    private EditText etPan;
    private EditText etExpiry;
    private MaterialButton btnSubmit;
    private FrameLayout layoutLoading;

    private PurchaseViewModel viewModel;
    private ConfigManager configManager;

    private int currentMode = 0; // 0 = Manual, 1 = Mock

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_balance_inquiry);

        // Init ViewModel
        com.example.mysoftpos.ui.base.GlobalViewModelFactory factory = new com.example.mysoftpos.ui.base.GlobalViewModelFactory(
                com.example.mysoftpos.di.ServiceLocator.getInstance(this));
        viewModel = new androidx.lifecycle.ViewModelProvider(this, factory)
                .get(PurchaseViewModel.class);

        configManager = ConfigManager.getInstance(this);

        // Bind Views
        tabLayout = findViewById(R.id.tabLayout);
        cardManualEntry = findViewById(R.id.cardManualEntry);
        cardMockTrack2 = findViewById(R.id.cardMockTrack2);
        etPan = findViewById(R.id.etPan);
        etExpiry = findViewById(R.id.etExpiry);
        btnSubmit = findViewById(R.id.btnSubmit);
        layoutLoading = findViewById(R.id.layoutLoading);
        View cardNfcIcon = findViewById(R.id.cardNfcIcon);
        TextView tvMockPreview = findViewById(R.id.tvMockTrack2Preview);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Mock Track 2 Preview
        String mockTrack2 = configManager.getTrack2("902");
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
        btnSubmit.setOnClickListener(v -> {
            String pan = etPan.getText().toString().replaceAll("\\s", "");
            String expiry = etExpiry.getText().toString();

            if (pan.length() < 13 || expiry.length() != 4) {
                Toast.makeText(this, "Invalid PAN or Expiry", Toast.LENGTH_SHORT).show();
                return;
            }

            CardInputData cardData = new CardInputData(pan, expiry, "012", null);
            processBalanceInquiry(cardData);
        });

        // Mock Track 2 Tap
        cardNfcIcon.setOnClickListener(v -> {
            String trk2 = configManager.getTrack2("902");
            String mockPan = configManager.getMockPan();
            String mockExpiry = configManager.getMockExpiry();

            if (trk2 != null && trk2.contains("=")) {
                String[] parts = trk2.split("=");
                mockPan = parts[0];
                if (parts[1].length() >= 4)
                    mockExpiry = parts[1].substring(0, 4);
            }

            CardInputData cardData = new CardInputData(mockPan, mockExpiry, "902", trk2);
            Toast.makeText(this, "Using Mock Track 2...", Toast.LENGTH_SHORT).show();
            processBalanceInquiry(cardData);
        });

        // Observe ViewModel State
        viewModel.getState().observe(this, state -> {
            showLoading(state.isLoading);
            if (state.isSuccess) {
                showResult(true, state.message, state.isoResponse, state.isoRequest);
            } else if (state.message != null && !state.isLoading) {
                showResult(false, state.message, state.isoResponse, state.isoRequest);
            }
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

    private void processBalanceInquiry(CardInputData cardData) {
        viewModel.processTransaction(cardData, "0", "704", TxnType.BALANCE_INQUIRY);
    }

    private void showLoading(boolean show) {
        layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(!show);
    }

    private void showResult(boolean success, String message, String isoResponse, String isoRequest) {
        Intent intent = new Intent(this, TransactionResultActivity.class);
        intent.putExtra("TXN_TYPE", TxnType.BALANCE_INQUIRY.name());
        intent.putExtra("SUCCESS", success);
        intent.putExtra("MESSAGE", message);

        if (isoResponse != null) {
            intent.putExtra("RAW_RESPONSE", isoResponse);
        }
        if (isoRequest != null) {
            intent.putExtra("RAW_REQUEST", isoRequest);
        }

        startActivity(intent);
        finish();
    }
}








