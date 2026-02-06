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
import android.widget.ImageView;
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
 * Tab 1: Mock Track 2 -> DE 22 = 022
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

    private ImageView ripple1;
    private ImageView ripple2;
    private ImageView ripple3;
    private ImageView ripple4;

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

        ripple1 = findViewById(R.id.ripple1);
        ripple2 = findViewById(R.id.ripple2);
        ripple3 = findViewById(R.id.ripple3);
        ripple4 = findViewById(R.id.ripple4);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

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
            String trk2 = configManager.getTrack2("022");
            String mockPan = configManager.getMockPan();
            String mockExpiry = configManager.getMockExpiry();

            if (trk2 != null && trk2.contains("=")) {
                String[] parts = trk2.split("=");
                mockPan = parts[0];
                if (parts[1].length() >= 4)
                    mockExpiry = parts[1].substring(0, 4);
            }

            CardInputData cardData = new CardInputData(mockPan, mockExpiry, "022", trk2);
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
            stopRippleAnimation();
        } else {
            cardManualEntry.setVisibility(View.GONE);
            cardMockTrack2.setVisibility(View.VISIBLE);
            startRippleAnimation();
        }
    }

    private void startRippleAnimation() {
        if (ripple1 != null && ripple2 != null && ripple3 != null && ripple4 != null) {
            ripple1.setVisibility(View.VISIBLE);
            ripple2.setVisibility(View.VISIBLE);
            ripple3.setVisibility(View.VISIBLE);
            ripple4.setVisibility(View.VISIBLE);

            android.view.animation.Animation anim1 = android.view.animation.AnimationUtils.loadAnimation(this,
                    R.anim.ripple_effect);
            ripple1.startAnimation(anim1);

            android.view.animation.Animation anim2 = android.view.animation.AnimationUtils.loadAnimation(this,
                    R.anim.ripple_effect);
            anim2.setStartOffset(750);
            ripple2.startAnimation(anim2);

            android.view.animation.Animation anim3 = android.view.animation.AnimationUtils.loadAnimation(this,
                    R.anim.ripple_effect);
            anim3.setStartOffset(1500);
            ripple3.startAnimation(anim3);

            android.view.animation.Animation anim4 = android.view.animation.AnimationUtils.loadAnimation(this,
                    R.anim.ripple_effect);
            anim4.setStartOffset(2250);
            ripple4.startAnimation(anim4);
        }
    }

    private void stopRippleAnimation() {
        if (ripple1 != null) {
            ripple1.clearAnimation();
            ripple1.setVisibility(View.GONE);
        }
        if (ripple2 != null) {
            ripple2.clearAnimation();
            ripple2.setVisibility(View.GONE);
        }
        if (ripple3 != null) {
            ripple3.clearAnimation();
            ripple3.setVisibility(View.GONE);
        }
        if (ripple4 != null) {
            ripple4.clearAnimation();
            ripple4.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentMode == 1) {
            startRippleAnimation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopRippleAnimation();
    }

    private void processBalanceInquiry(CardInputData cardData) {
        String username = getIntent().getStringExtra("USERNAME");
        if (username == null)
            username = "Guest";
        viewModel.processTransaction(cardData, "0", "704", TxnType.BALANCE_INQUIRY, username);
    }

    private void showLoading(boolean show) {
        layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(!show);
    }

    private void showResult(boolean success, String message, String isoResponse, String isoRequest) {
        Intent intent = new Intent(this, TransactionResultActivity.class);
        intent.putExtra("TXN_TYPE", TxnType.BALANCE_INQUIRY.name());
        intent.putExtra("SUCCESS", success);
        intent.putExtra(TransactionResultActivity.EXTRA_RESULT_TYPE,
                success ? TransactionResultActivity.ResultType.SUCCESS
                        : TransactionResultActivity.ResultType.TRANSACTION_FAILED);
        intent.putExtra(TransactionResultActivity.EXTRA_MESSAGE, message);

        if (isoResponse != null) {
            intent.putExtra("RAW_RESPONSE", isoResponse);
        }
        if (isoRequest != null) {
            intent.putExtra("RAW_REQUEST", isoRequest);
        }

        // Receipt Extras
        String maskedPan = etPan.getText().toString().length() > 4
                ? "**** " + etPan.getText().toString().substring(etPan.getText().length() - 4)
                : "**** 0000";
        intent.putExtra("MASKED_PAN", maskedPan);

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss dd/MM/yyyy",
                java.util.Locale.getDefault());
        intent.putExtra("TXN_DATE", sdf.format(new java.util.Date()));
        intent.putExtra("TXN_ID", "TXN" + System.currentTimeMillis() % 100000000);

        startActivity(intent);
        finish();
    }
}
