package com.example.mysoftpos.nfc;

import android.nfc.Tag;

/**
 * @deprecated Dead code — no longer used. NFC card reading flows through
 * {@link IsoDepTransceiver} + {@link com.example.mysoftpos.domain.usecase.ReadCardDataUseCase}.
 * This class will be removed in a future cleanup.
 */
@Deprecated
public final class NfcCardReader {

    public interface Callback {
        void onSuccess();
        void onError(Exception e);
    }

    public void read(Tag tag, Callback callback) {
        if (callback != null) {
            callback.onError(new UnsupportedOperationException(
                    "NfcCardReader is deprecated. Use ReadCardDataUseCase instead."));
        }
    }
}






