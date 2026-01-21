package com.example.mysoftpos.data;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.os.Bundle;

public class NfcHardwareManager {

    private final NfcAdapter nfcAdapter;

    public NfcHardwareManager(Activity activity) {
        this.nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
    }

    public void enableReaderMode(Activity activity, NfcAdapter.ReaderCallback callback) {
        if (nfcAdapter != null) {
            Bundle options = new Bundle();
            // Workaround for some devices: delay presence check
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);

            nfcAdapter.enableReaderMode(
                    activity,
                    callback,
                    NfcAdapter.FLAG_READER_NFC_A |
                    NfcAdapter.FLAG_READER_NFC_B |
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK | // Fix: Stops Zalo/System from intercepting
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options
            );
        }
    }

    public void disableReaderMode(Activity activity) {
        if (nfcAdapter != null) {
            nfcAdapter.disableReaderMode(activity);
        }
    }
}
