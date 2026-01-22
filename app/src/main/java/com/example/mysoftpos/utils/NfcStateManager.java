package com.example.mysoftpos.utils;

import android.content.Context;
import android.nfc.NfcAdapter;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * Observes NFC enable/disable state and updates UI.
 * Designed to be safe even if NFC is not available.
 */
public final class NfcStateManager {

    public interface Listener {
        void onNfcState(boolean enabled);
    }

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;

    public NfcStateManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void startMonitoring() {
        if (running) return;
        running = true;
        poll();
    }

    public void stopMonitoring() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void poll() {
        if (!running) return;
        boolean enabled = false;
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
        if (adapter != null) {
            enabled = adapter.isEnabled();
        }
        if (listener != null) listener.onNfcState(enabled);
        handler.postDelayed(this::poll, 800);
    }

    public static void updateViewVisibility(View readyView, View disabledView, boolean enabled) {
        if (readyView != null) readyView.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (disabledView != null) disabledView.setVisibility(enabled ? View.GONE : View.VISIBLE);
    }
}

