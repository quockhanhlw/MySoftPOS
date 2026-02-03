package com.example.mysoftpos.nfc;

import android.nfc.Tag;

/**
 * Placeholder wrapper for NFC card read flow.
 * The current implementation uses IsoDepTransceiver + ReadCardDataUseCase.
 * This class is kept for backward compatibility with earlier iterations.
 */
public final class NfcCardReader {

    public interface Callback {
        void onSuccess();
        void onError(Exception e);
    }

    public void read(Tag tag, Callback callback) {
        if (callback != null) {
            callback.onError(new UnsupportedOperationException("NfcCardReader is not used in this version. Use ReadCardDataUseCase."));
        }
    }
}






