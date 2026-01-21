package com.example.mysoftpos.data;

import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.IsoDep;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;

public class IsoDepTransceiver implements CardTransceiver {

    private static final String TAG = "IsoDepTransceiver";
    private static final boolean DEBUG = true; // Temporary replacement for BuildConfig.DEBUG
    private static final int EXTENDED_TIMEOUT_MS = 5000;
    private final IsoDep isoDep;

    public IsoDepTransceiver(IsoDep isoDep) {
        this.isoDep = isoDep;
    }

    public static IsoDepTransceiver from(Tag tag) {
        IsoDep iso = IsoDep.get(tag);
        if (iso != null) {
            return new IsoDepTransceiver(iso);
        }
        return null; // Or throw exception based on preference, returning null for now
    }

    public void connect() throws IOException {
        if (isoDep != null) {
            isoDep.connect();
            isoDep.setTimeout(EXTENDED_TIMEOUT_MS);
            Log.d(TAG, "IsoDep connected with timeout: " + EXTENDED_TIMEOUT_MS + "ms");
        } else {
            throw new IOException("IsoDep is null");
        }
    }

    @Override
    public byte[] transceive(byte[] commandApdu) throws IOException {
        if (isoDep == null || !isoDep.isConnected()) {
            throw new IOException("IsoDep not connected");
        }

        if (DEBUG) {
            Log.d(TAG, "Sending APDU: " + bytesToHex(commandApdu));
        }

        try {
            byte[] response = isoDep.transceive(commandApdu);

            if (DEBUG) {
                Log.d(TAG, "Received APDU: " + bytesToHex(response));
            }
            return response;

        } catch (TagLostException e) {
            Log.e(TAG, "Tag Lost during transceive", e);
            throw e;
        } catch (IOException e) {
            Log.e(TAG, "IOException during transceive", e);
            throw e;
        }
    }

    @Override
    public boolean isConnected() {
        return isoDep != null && isoDep.isConnected();
    }

    @Override
    public void close() {
        if (isoDep != null) {
            try {
                isoDep.close();
                Log.d(TAG, "IsoDep connection closed");
            } catch (IOException e) {
                Log.e(TAG, "Error closing IsoDep", e);
            }
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
