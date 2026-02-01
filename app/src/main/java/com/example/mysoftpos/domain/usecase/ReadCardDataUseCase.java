package com.example.mysoftpos.domain.usecase;

import android.util.Log;

import com.example.mysoftpos.data.CardTransceiver;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.utils.ApduCommandBuilder;
import com.example.mysoftpos.utils.TlvParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class ReadCardDataUseCase {

    private static final String TAG = "ReadCardDataUseCase";

    // Known AIDs
    private static final byte[] AID_VISA = hexStringToByteArray("A000000003");
    private static final byte[] AID_MASTERCARD = hexStringToByteArray("A000000004");
    private static final byte[] AID_NAPAS = hexStringToByteArray("A000000727"); // Standard Napas prefix
    private static final byte[] AID_NAPAS_EXT = hexStringToByteArray("A0000007271010"); // Extended Napas AID

    private final CardTransceiver transceiver;

    public ReadCardDataUseCase(CardTransceiver transceiver) {
        this.transceiver = transceiver;
    }

    public CardInputData execute() throws IOException {
        if (!transceiver.isConnected()) {
            throw new IOException("Transceiver not connected");
        }

        // 1. SELECT PPSE (Optional but good for wake-up)
        // We ignore failure here as we will try direct selection next.
        try {
            transceiver.transceive(ApduCommandBuilder.selectPpse());
        } catch (Exception ignored) {
        }

        // 2. Select AID (Priority Order: Visa -> MC -> Napas -> Napas Ext)
        byte[] selectedAid = null;
        if (trySelectAid(AID_VISA))
            selectedAid = AID_VISA;
        else if (trySelectAid(AID_MASTERCARD))
            selectedAid = AID_MASTERCARD;
        else if (trySelectAid(AID_NAPAS))
            selectedAid = AID_NAPAS;
        else if (trySelectAid(AID_NAPAS_EXT))
            selectedAid = AID_NAPAS_EXT;

        if (selectedAid == null) {
            throw new IOException("No supported Application found");
        }

        // 3. GET PROCESSING OPTIONS (GPO)
        byte[] gpoResponse = transceiver.transceive(ApduCommandBuilder.getProcessingOptions());
        if (!isSuccess(gpoResponse)) {
            throw new IOException("GPO failed");
        }

        // 4. READ RECORD (Scan SFI 1-10, Rec 1-10) - Simplified scan
        // In full EMV, we parse AFL from GPO response. For Quick Read, we can scan or
        // assume standard locations.
        // Track 2 (Tag 57) is our target.
        String track2Hex = null;
        Map<String, String> emvTags = new HashMap<>();

        // Capture from GPO (AIP 82)
        extractTags(gpoResponse, emvTags, "82");

        for (int sfi = 1; sfi <= 10; sfi++) {
            for (int rec = 1; rec <= 10; rec++) {
                byte[] readRecordResp = transceiver.transceive(ApduCommandBuilder.readRecord(sfi, rec));
                if (isSuccess(readRecordResp)) {
                    // Try find Track2
                    if (track2Hex == null) {
                        byte[] t2 = TlvParser.findTag(readRecordResp, 0x57);
                        if (t2 != null) {
                            track2Hex = TlvParser.bytesToHex(t2).replace("F", "");
                        }
                    }

                    // Capture critical EMV tags
                    extractTags(readRecordResp, emvTags, "9F26", "9F27", "9F10", "9F37", "9F36", "95", "9A", "9C",
                            "5F2A", "82");
                }
            }
        }

        if (track2Hex == null) {
            throw new IOException("Track2 Data (Tag 57) not found");
        }

        String pan = com.example.mysoftpos.utils.CardDataHelper.extractPan(track2Hex);
        String expiry = com.example.mysoftpos.utils.CardDataHelper.extractExpiry(track2Hex);

        CardInputData data = new CardInputData(
                pan,
                expiry,
                "071", // NFC
                track2Hex);
        data.setEmvTags(emvTags);
        return data;
    }

    private boolean trySelectAid(byte[] aid) throws IOException {
        byte[] response = transceiver.transceive(ApduCommandBuilder.selectAid(aid));
        return isSuccess(response);
    }

    private boolean isSuccess(byte[] response) {
        if (response == null || response.length < 2)
            return false;
        int sw1 = response[response.length - 2] & 0xFF;
        int sw2 = response[response.length - 1] & 0xFF;
        return sw1 == 0x90 && sw2 == 0x00;
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private void extractTags(byte[] data, Map<String, String> targetMap, String... tags) {
        for (String tag : tags) {
            try {
                int tagInt = Integer.parseInt(tag, 16);
                byte[] val = TlvParser.findTag(data, tagInt);
                if (val != null) {
                    targetMap.put(tag, TlvParser.bytesToHex(val));
                }
            } catch (Exception e) {
                // Ignore parsing errors for individual tags
            }
        }
    }
}
