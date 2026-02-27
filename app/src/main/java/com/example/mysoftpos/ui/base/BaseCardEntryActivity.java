package com.example.mysoftpos.ui.base;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.cardview.widget.CardView;
import com.example.mysoftpos.R;
import com.example.mysoftpos.di.ServiceLocator;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.domain.usecase.ReadCardDataUseCase;
import com.example.mysoftpos.nfc.IsoDepTransceiver;
import com.example.mysoftpos.nfc.NfcStateManager;
import com.example.mysoftpos.ui.BaseActivity;
import com.example.mysoftpos.utils.config.ConfigManager;
import com.example.mysoftpos.viewmodel.PurchaseViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

/**
 * Base class for card entry activities (Purchase, Balance Inquiry).
 * Encapsulates: tab UI, ripple animation, manual entry, NFC card reading, ViewModel setup.
 *
 * Subclasses override:
 * - {@link #getLayoutResId()} — layout resource
 * - {@link #getSubmitButtonId()} — submit button ID
 * - {@link #onCardDataReady(CardInputData)} — process the transaction
 * - {@link #onTransactionResult(boolean, String, String, String)} — handle result
 * - {@link #onCreateExtra(Bundle)} — additional onCreate setup (optional)
 */
public abstract class BaseCardEntryActivity extends BaseActivity implements NfcAdapter.ReaderCallback {

    private static final String TAG = "BaseCardEntry";

    protected PurchaseViewModel viewModel;
    protected ConfigManager configManager;
    protected EditText etPan;
    protected EditText etExpiry;
    protected FrameLayout layoutLoading;

    private CardView cardManualEntry;
    private CardView cardNfcEntry;
    private LinearLayout layoutNfcDisabled;
    private LinearLayout layoutNfcReady;
    private ImageView ripple1, ripple2, ripple3, ripple4;
    private int currentMode = 0;

    // NFC
    private NfcAdapter nfcAdapter;
    private NfcStateManager nfcStateManager;
    private boolean nfcEnabled = false;
    private boolean nfcCardProcessing = false;

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

    /** Last PAN read from NFC, for masked PAN display in result. */
    private String lastNfcPan;

    public String getLastNfcPan() {
        return lastNfcPan;
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

        // NFC Adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        // Bind common views
        ImageButton btnBack = findViewById(R.id.btnBack);
        layoutLoading = findViewById(R.id.layoutLoading);
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        cardManualEntry = findViewById(R.id.cardManualEntry);
        cardNfcEntry = findViewById(R.id.cardMockTrack2); // keep ID for layout compat
        layoutNfcDisabled = findViewById(R.id.layoutNfcDisabled);
        layoutNfcReady = findViewById(R.id.layoutNfcReady);
        etPan = findViewById(R.id.etPan);
        etExpiry = findViewById(R.id.etExpiry);
        View btnSubmit = findViewById(getSubmitButtonId());

        // tvMockTrack2Preview kept in layout for backward compat but not used in code

        ripple1 = findViewById(R.id.ripple1);
        ripple2 = findViewById(R.id.ripple2);
        ripple3 = findViewById(R.id.ripple3);
        ripple4 = findViewById(R.id.ripple4);

        // Back
        btnBack.setOnClickListener(v -> finish());

        // Enable NFC button
        MaterialButton btnEnableNfc = findViewById(R.id.btnEnableNfc);
        if (btnEnableNfc != null) {
            btnEnableNfc.setOnClickListener(v -> openNfcSettings());
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

        // NFC State Monitoring
        nfcStateManager = new NfcStateManager(this, enabled -> {
            nfcEnabled = enabled;
            if (currentMode == 1) {
                updateNfcSubViews();
            }
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

    // ==================== NFC Settings ====================

    private void openNfcSettings() {
        if (nfcAdapter == null) {
            Toast.makeText(this, getString(R.string.nfc_not_supported), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        }
    }

    // ==================== NFC Reader Mode ====================

    private void enableNfcReaderMode() {
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            Bundle options = new Bundle();
            // This delay only controls the background "is card still there?" ping AFTER
            // the card is already discovered. Does NOT affect initial detection speed.
            // Too low (250ms) → ping collides with our APDU commands → TagLostException
            // 2500ms → safe window for our 4-5 APDU commands (~800ms total)
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 2500);
            nfcAdapter.enableReaderMode(this, this,
                    NfcAdapter.FLAG_READER_NFC_A |
                    NfcAdapter.FLAG_READER_NFC_B |
                    NfcAdapter.FLAG_READER_NFC_F |
                    NfcAdapter.FLAG_READER_NFC_V |
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK |
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
        }
    }

    private void disableNfcReaderMode() {
        if (nfcAdapter != null) {
            try {
                nfcAdapter.disableReaderMode(this);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if (nfcCardProcessing) return;
        nfcCardProcessing = true;

        IsoDepTransceiver transceiver = IsoDepTransceiver.from(tag);
        if (transceiver == null) {
            nfcCardProcessing = false;
            runOnUiThread(() -> Toast.makeText(this,
                    getString(R.string.nfc_reading_error, "Thẻ không hỗ trợ"),
                    Toast.LENGTH_SHORT).show());
            return;
        }

        new Thread(() -> {
            CardInputData cardData = null;
            try {
                transceiver.connect();
                ReadCardDataUseCase useCase = new ReadCardDataUseCase(transceiver);
                cardData = useCase.execute();
                lastNfcPan = cardData.getPan();
            } catch (Throwable e) {
                // Catch Throwable, not just Exception — prevents app crash
                Log.e(TAG, "NFC read failed", e);
                final String errMsg = e.getMessage() != null ? e.getMessage() : "Lỗi đọc thẻ";
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            getString(R.string.nfc_reading_error, errMsg),
                            Toast.LENGTH_SHORT).show();
                });
            } finally {
                // ALWAYS close transceiver and reset flag
                try { transceiver.close(); } catch (Exception ignored) {}
                nfcCardProcessing = false;
            }

            // Deliver result on UI thread AFTER transceiver is closed
            if (cardData != null) {
                final CardInputData result = cardData;
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.nfc_card_read_success), Toast.LENGTH_SHORT).show();
                    try {
                        onCardDataReady(result);
                    } catch (Exception e) {
                        Log.e(TAG, "onCardDataReady failed", e);
                        Toast.makeText(this,
                                getString(R.string.nfc_reading_error, "Lỗi xử lý: " + e.getMessage()),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    // ==================== View Visibility ====================

    private void updateCardVisibility() {
        if (currentMode == 0) {
            // Manual Entry mode
            cardManualEntry.setVisibility(View.VISIBLE);
            cardNfcEntry.setVisibility(View.GONE);
            stopRippleAnimation();
            disableNfcReaderMode();
        } else {
            // NFC mode
            cardManualEntry.setVisibility(View.GONE);
            cardNfcEntry.setVisibility(View.VISIBLE);
            updateNfcSubViews();
        }
    }

    private void updateNfcSubViews() {
        if (nfcAdapter == null) {
            // Device has no NFC hardware
            layoutNfcDisabled.setVisibility(View.VISIBLE);
            layoutNfcReady.setVisibility(View.GONE);
            stopRippleAnimation();
            disableNfcReaderMode();

            // Change button text to indicate not supported
            MaterialButton btnEnableNfc = findViewById(R.id.btnEnableNfc);
            if (btnEnableNfc != null) {
                btnEnableNfc.setText(R.string.nfc_not_supported);
                btnEnableNfc.setEnabled(false);
            }
        } else if (!nfcEnabled) {
            // NFC hardware exists but is OFF
            layoutNfcDisabled.setVisibility(View.VISIBLE);
            layoutNfcReady.setVisibility(View.GONE);
            stopRippleAnimation();
            disableNfcReaderMode();

            MaterialButton btnEnableNfc = findViewById(R.id.btnEnableNfc);
            if (btnEnableNfc != null) {
                btnEnableNfc.setText(R.string.nfc_enable_button);
                btnEnableNfc.setEnabled(true);
            }
        } else {
            // NFC is ON → show scan UI
            layoutNfcDisabled.setVisibility(View.GONE);
            layoutNfcReady.setVisibility(View.VISIBLE);
            startRippleAnimation();
            enableNfcReaderMode();
        }
    }

    // ==================== Ripple Animation ====================

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

    // ==================== Lifecycle ====================

    @Override
    protected void onResume() {
        super.onResume();
        nfcStateManager.startMonitoring();
        nfcCardProcessing = false; // Reset on resume

        if (currentMode == 1) {
            updateNfcSubViews();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        nfcStateManager.stopMonitoring();
        stopRippleAnimation();
        disableNfcReaderMode();
    }

    protected void showLoading(boolean loading) {
        if (layoutLoading != null)
            layoutLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
