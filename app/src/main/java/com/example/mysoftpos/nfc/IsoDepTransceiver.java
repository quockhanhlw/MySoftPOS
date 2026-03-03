package com.example.mysoftpos.nfc;

import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.IsoDep;
import android.util.Log;

import com.example.mysoftpos.BuildConfig;

import java.io.IOException;

public class IsoDepTransceiver implements CardTransceiver {

    private static final String TAG = "IsoDepTransceiver";
    /** Only log raw APDU bytes in debug builds — release builds must NOT log card data. */
    private static final boolean DEBUG = BuildConfig.DEBUG;
    /**
     * Timeout for IsoDep. Only need 3s since we send 4-5 APDUs (~200ms each).
     * Too long = user waits forever on card error.
     */
    private static final int EXTENDED_TIMEOUT_MS = 3000;
    private final IsoDep isoDep;

    public IsoDepTransceiver(IsoDep isoDep) {
        this.isoDep = isoDep;
    }

    public static IsoDepTransceiver from(Tag tag) {
        IsoDep iso = IsoDep.get(tag);
        if (iso != null) {
            return new IsoDepTransceiver(iso);
        }
        return null;
    }

    public void connect() throws IOException {
        if (isoDep != null) {
            isoDep.connect();
            isoDep.setTimeout(EXTENDED_TIMEOUT_MS);
            Log.d(TAG, "IsoDep connected. timeout=" + EXTENDED_TIMEOUT_MS
                    + "ms, maxTransceiveLen=" + isoDep.getMaxTransceiveLength()
                    + ", isExtLenSupported=" + isoDep.isExtendedLengthApduSupported());
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





