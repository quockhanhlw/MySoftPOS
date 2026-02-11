package com.example.mysoftpos.ui.base;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.cardview.widget.CardView;
import com.example.mysoftpos.R;
import com.example.mysoftpos.di.ServiceLocator;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.ui.BaseActivity;
import com.example.mysoftpos.utils.config.ConfigManager;
import com.example.mysoftpos.viewmodel.PurchaseViewModel;
import com.google.android.material.tabs.TabLayout;

/**
 * Base class for card entry activities (Purchase, Balance Inquiry).
 * Encapsulates: tab UI, ripple animation, manual entry, mock Track2, ViewModel
 * setup.
 *
 * Subclasses override:
 * - {@link #getLayoutResId()} — layout resource
 * - {@link #getSubmitButtonId()} — submit button ID (btnSubmitManual or
 * btnSubmit)
 * - {@link #onCardDataReady(CardInputData)} — process the transaction
 * - {@link #onTransactionResult(boolean, String, String, String)} — handle
 * result
 * - {@link #onCreateExtra(Bundle)} — additional onCreate setup (optional)
 */
public abstract class BaseCardEntryActivity extends BaseActivity {

    protected PurchaseViewModel viewModel;
    protected ConfigManager configManager;
    protected EditText etPan;
    protected EditText etExpiry;
    protected FrameLayout layoutLoading;

    private CardView cardManualEntry;
    private CardView cardMockTrack2;
    private ImageView ripple1, ripple2, ripple3, ripple4;
    private int currentMode = 0;

    protected abstract int getLayoutResId();

    protected abstract int getSubmitButtonId();

    protected abstract void onCardDataReady(CardInputData card);

    protected abstract void onTransactionResult(boolean success, String message, String isoResponse, String isoRequest);

    /** Override for additional onCreate logic. Called after base setup. */
    protected void onCreateExtra(Bundle savedInstanceState) {
    }

    protected int getCurrentMode() {
        return currentMode;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResId());

        // ViewModel
        GlobalViewModelFactory factory = new GlobalViewModelFactory(ServiceLocator.getInstance(this));
        viewModel = new androidx.lifecycle.ViewModelProvider(this, factory)
                .get(PurchaseViewModel.class);

        configManager = ConfigManager.getInstance(this);

        // Bind common views
        ImageButton btnBack = findViewById(R.id.btnBack);
        layoutLoading = findViewById(R.id.layoutLoading);
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        cardManualEntry = findViewById(R.id.cardManualEntry);
        cardMockTrack2 = findViewById(R.id.cardMockTrack2);
        etPan = findViewById(R.id.etPan);
        etExpiry = findViewById(R.id.etExpiry);
        View btnSubmit = findViewById(getSubmitButtonId());
        View cardNfcIcon = findViewById(R.id.cardNfcIcon);
        TextView tvMockPreview = findViewById(R.id.tvMockTrack2Preview);

        ripple1 = findViewById(R.id.ripple1);
        ripple2 = findViewById(R.id.ripple2);
        ripple3 = findViewById(R.id.ripple3);
        ripple4 = findViewById(R.id.ripple4);

        // Back
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
                Toast.makeText(this, getString(R.string.err_invalid_pan), Toast.LENGTH_SHORT).show();
                return;
            }

            CardInputData manualData = new CardInputData(pan, expiry, "012", null);
            onCardDataReady(manualData);
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
            Toast.makeText(this, getString(R.string.msg_using_mock_track2), Toast.LENGTH_SHORT).show();
            onCardDataReady(mockData);
        });

        // Observe state
        viewModel.getState().observe(this, state -> {
            showLoading(state.isLoading);
            if (state.isSuccess) {
                onTransactionResult(true, state.message, state.isoResponse, state.isoRequest);
            } else if (state.message != null && !state.isLoading) {
                onTransactionResult(false, state.message, state.isoResponse, state.isoRequest);
            }
        });

        // Subclass extra init
        onCreateExtra(savedInstanceState);
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

    protected void startRippleAnimation() {
        if (ripple1 == null || ripple2 == null || ripple3 == null || ripple4 == null)
            return;

        ripple1.setVisibility(View.VISIBLE);
        ripple2.setVisibility(View.VISIBLE);
        ripple3.setVisibility(View.VISIBLE);
        ripple4.setVisibility(View.VISIBLE);

        android.view.animation.Animation a1 = android.view.animation.AnimationUtils.loadAnimation(this,
                R.anim.ripple_effect);
        ripple1.startAnimation(a1);

        android.view.animation.Animation a2 = android.view.animation.AnimationUtils.loadAnimation(this,
                R.anim.ripple_effect);
        a2.setStartOffset(750);
        ripple2.startAnimation(a2);

        android.view.animation.Animation a3 = android.view.animation.AnimationUtils.loadAnimation(this,
                R.anim.ripple_effect);
        a3.setStartOffset(1500);
        ripple3.startAnimation(a3);

        android.view.animation.Animation a4 = android.view.animation.AnimationUtils.loadAnimation(this,
                R.anim.ripple_effect);
        a4.setStartOffset(2250);
        ripple4.startAnimation(a4);
    }

    protected void stopRippleAnimation() {
        ImageView[] ripples = { ripple1, ripple2, ripple3, ripple4 };
        for (ImageView r : ripples) {
            if (r != null) {
                r.clearAnimation();
                r.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentMode == 1)
            startRippleAnimation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopRippleAnimation();
    }

    protected void showLoading(boolean loading) {
        if (layoutLoading != null)
            layoutLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
