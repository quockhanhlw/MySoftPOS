package com.example.mysoftpos.iso8583.emv;

import android.util.Log;

import com.example.mysoftpos.nfc.CardTransceiver;
import com.example.mysoftpos.nfc.ApduCommandBuilder;

import java.io.IOException;
import java.util.Map;

/**
 * NAPAS Domestic CHIP — DE 55 Response Processing.
 *
 * After receiving the ISO 8583 response from NAPAS, the terminal must:
 *   1. Parse DE 55 from the response to extract issuer EMV tags.
 *   2. If tag 91 (Issuer Authentication Data) is present:
 *      → Send EXTERNAL AUTHENTICATE APDU to the chip card.
 *   3. If tag 71 (Issuer Script Template 1) is present:
 *      → Execute script commands BEFORE second GENERATE AC.
 *   4. If tag 72 (Issuer Script Template 2) is present:
 *      → Execute script commands AFTER second GENERATE AC.
 *
 * This class encapsulates all post-response card communication.
 */
public final class NapasEmvProcessor {

    private static final String TAG = "NapasEmvProcessor";

    private NapasEmvProcessor() {
    }

    /**
     * Result of DE 55 response processing against the chip card.
     */
    public static class ProcessingResult {
        public final boolean externalAuthSuccess;
        public final boolean script1Executed;
        public final boolean script2Executed;
        public final byte[] issuerScriptResult; // For DF31 in reversal if needed
        public final String errorMessage;       // null if no error

        public ProcessingResult(boolean externalAuthSuccess,
                                boolean script1Executed,
                                boolean script2Executed,
                                byte[] issuerScriptResult,
                                String errorMessage) {
            this.externalAuthSuccess = externalAuthSuccess;
            this.script1Executed = script1Executed;
            this.script2Executed = script2Executed;
            this.issuerScriptResult = issuerScriptResult;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return errorMessage == null;
        }
    }

    /**
     * Process DE 55 response tags against the chip card.
     *
     * <pre>
     * Sequence (per EMV specification):
     *   1. EXTERNAL AUTHENTICATE with tag 91 data (if present)
     *   2. Execute Issuer Script Template 1 / tag 71 (if present) — before 2nd GENERATE AC
     *   3. Second GENERATE AC (TC or AAC based on issuer decision) — optional
     *   4. Execute Issuer Script Template 2 / tag 72 (if present) — after 2nd GENERATE AC
     * </pre>
     *
     * @param responseTags Parsed EMV tags from response DE 55 (via EmvTlvCodec.parseDE55Response)
     * @param transceiver  Active NFC card transceiver (card must still be present)
     * @param approved     Whether the transaction was approved (RC=00)
     * @return ProcessingResult with status of each step
     */
    public static ProcessingResult processDE55Response(
            Map<Integer, byte[]> responseTags,
            CardTransceiver transceiver,
            boolean approved) {

        if (responseTags == null || responseTags.isEmpty()) {
            Log.d(TAG, "No response DE 55 tags to process");
            return new ProcessingResult(true, false, false, null, null);
        }

        boolean externalAuthOk = true;
        boolean script1Done = false;
        boolean script2Done = false;
        byte[] scriptResult = null;
        StringBuilder errors = new StringBuilder();

        // --- Step 1: External Authenticate (Tag 91) ---
        byte[] issuerAuthData = EmvTlvCodec.getIssuerAuthData(responseTags);
        if (issuerAuthData != null && issuerAuthData.length > 0) {
            Log.d(TAG, "Tag 91 present (" + issuerAuthData.length + " bytes), sending EXTERNAL AUTHENTICATE");
            try {
                if (transceiver != null && transceiver.isConnected()) {
                    byte[] extAuthCmd = ApduCommandBuilder.externalAuthenticate(issuerAuthData);
                    byte[] response = transceiver.transceive(extAuthCmd);
                    if (isSuccess(response)) {
                        Log.d(TAG, "EXTERNAL AUTHENTICATE succeeded");
                        externalAuthOk = true;
                    } else {
                        String sw = getStatusWord(response);
                        Log.w(TAG, "EXTERNAL AUTHENTICATE failed: SW=" + sw);
                        externalAuthOk = false;
                        errors.append("ExtAuth failed (SW=").append(sw).append("); ");
                    }
                } else {
                    Log.w(TAG, "Card not connected for EXTERNAL AUTHENTICATE");
                    externalAuthOk = false;
                    errors.append("Card disconnected for ExtAuth; ");
                }
            } catch (IOException e) {
                Log.e(TAG, "EXTERNAL AUTHENTICATE IOException", e);
                externalAuthOk = false;
                errors.append("ExtAuth IO error: ").append(e.getMessage()).append("; ");
            }
        }

        // --- Step 2: Issuer Script Template 1 (Tag 71) — Before 2nd GENERATE AC ---
        byte[] script1 = EmvTlvCodec.getIssuerScript1(responseTags);
        if (script1 != null && script1.length > 0) {
            Log.d(TAG, "Tag 71 present (" + script1.length + " bytes), executing Script 1");
            try {
                scriptResult = executeIssuerScript(script1, transceiver);
                script1Done = true;
                Log.d(TAG, "Script 1 execution complete");
            } catch (Exception e) {
                Log.e(TAG, "Script 1 execution error", e);
                errors.append("Script1 error: ").append(e.getMessage()).append("; ");
            }
        }

        // --- Step 3: Second GENERATE AC would go here in full EMV flow ---
        // In SoftPOS/online-only mode, this is typically skipped because
        // the terminal already received the online authorization result.

        // --- Step 4: Issuer Script Template 2 (Tag 72) — After 2nd GENERATE AC ---
        byte[] script2 = EmvTlvCodec.getIssuerScript2(responseTags);
        if (script2 != null && script2.length > 0) {
            Log.d(TAG, "Tag 72 present (" + script2.length + " bytes), executing Script 2");
            try {
                byte[] result2 = executeIssuerScript(script2, transceiver);
                script2Done = true;
                // Merge script results
                if (scriptResult == null) {
                    scriptResult = result2;
                } else if (result2 != null) {
                    byte[] merged = new byte[scriptResult.length + result2.length];
                    System.arraycopy(scriptResult, 0, merged, 0, scriptResult.length);
                    System.arraycopy(result2, 0, merged, scriptResult.length, result2.length);
                    scriptResult = merged;
                }
                Log.d(TAG, "Script 2 execution complete");
            } catch (Exception e) {
                Log.e(TAG, "Script 2 execution error", e);
                errors.append("Script2 error: ").append(e.getMessage()).append("; ");
            }
        }

        String errorMsg = errors.length() > 0 ? errors.toString().trim() : null;
        return new ProcessingResult(externalAuthOk, script1Done, script2Done, scriptResult, errorMsg);
    }

    /**
     * Execute an Issuer Script (tag 71 or 72 content).
     *
     * <pre>
     * The value of tag 71/72 is a constructed TLV containing one or more
     * command TLVs. Each inner TLV has:
     *   Tag 86 (Issuer Script Command) → value is the raw APDU to send.
     *
     * We parse the inner TLVs and send each tag-86 value as an APDU command.
     * </pre>
     *
     * @param scriptData Raw bytes of the tag 71 or 72 value
     * @param transceiver Active NFC transceiver
     * @return Concatenated script execution results (for tag DF31), or null
     */
    private static byte[] executeIssuerScript(byte[] scriptData, CardTransceiver transceiver)
            throws IOException {

        if (transceiver == null || !transceiver.isConnected()) {
            throw new IOException("Card not connected for Issuer Script execution");
        }

        // Parse inner TLVs to find tag 86 (Issuer Script Command)
        Map<Integer, byte[]> innerTags = EmvTlvCodec.parseDE55Bytes(scriptData);

        // Tag 86 contains the APDU command to send to the card
        byte[] apduCommand = innerTags.get(0x86);
        if (apduCommand == null) {
            // Some implementations may have multiple 86 tags or different structure.
            // Try executing the whole script data as a sequence of commands.
            Log.d(TAG, "No tag 86 found in script, attempting raw APDU execution");

            // Fallback: parse as a sequence of TLV where each 86 tag is a command
            java.io.ByteArrayOutputStream resultBaos = new java.io.ByteArrayOutputStream();
            int offset = 0;
            while (offset < scriptData.length) {
                // Read tag
                if (offset >= scriptData.length) break;
                int tagByte = scriptData[offset++] & 0xFF;
                if (tagByte == 0x00 || tagByte == 0xFF) continue; // padding

                // Read length
                if (offset >= scriptData.length) break;
                int len = scriptData[offset++] & 0xFF;
                if ((len & 0x80) != 0) {
                    int numLenBytes = len & 0x7F;
                    len = 0;
                    for (int i = 0; i < numLenBytes && offset < scriptData.length; i++) {
                        len = (len << 8) | (scriptData[offset++] & 0xFF);
                    }
                }

                if (offset + len > scriptData.length) break;

                // If tag is 86, the value is an APDU command
                if (tagByte == 0x86) {
                    byte[] cmd = new byte[len];
                    System.arraycopy(scriptData, offset, cmd, 0, len);
                    byte[] resp = transceiver.transceive(cmd);
                    if (resp != null && resp.length >= 2) {
                        resultBaos.write(resp, resp.length - 2, 2); // SW1 SW2
                    }
                }
                offset += len;
            }
            byte[] results = resultBaos.toByteArray();
            return results.length > 0 ? results : null;
        }

        // Single tag 86 command
        byte[] response = transceiver.transceive(apduCommand);
        if (response != null && response.length >= 2) {
            // Return SW1+SW2 as script result
            return new byte[]{response[response.length - 2], response[response.length - 1]};
        }
        return null;
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static boolean isSuccess(byte[] response) {
        if (response == null || response.length < 2) return false;
        int sw1 = response[response.length - 2] & 0xFF;
        int sw2 = response[response.length - 1] & 0xFF;
        return sw1 == 0x90 && sw2 == 0x00;
    }

    private static String getStatusWord(byte[] response) {
        if (response == null || response.length < 2) return "NULL";
        return String.format("%02X%02X",
                response[response.length - 2] & 0xFF,
                response[response.length - 1] & 0xFF);
    }
}

