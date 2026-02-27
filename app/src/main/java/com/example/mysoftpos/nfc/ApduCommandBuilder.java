package com.example.mysoftpos.nfc;

import java.io.ByteArrayOutputStream;

/**
 * APDU Command Builder for EMV NFC Contactless Chip transactions.
 *
 * Full NAPAS Domestic CHIP transaction flow:
 *   1. SELECT PPSE (2PAY.SYS.DDF01)
 *   2. SELECT AID (F0000007040001 for NAPAS Domestic)
 *   3. GET PROCESSING OPTIONS (GPO)
 *   4. READ RECORD
 *   5. GENERATE APPLICATION CRYPTOGRAM (GENERATE AC) — request ARQC
 *   6. EXTERNAL AUTHENTICATE — send Issuer Auth Data (tag 91) to card
 *   7. Issuer Script execution (raw APDU from tag 71/72 → tag 86)
 */
public class ApduCommandBuilder {

    private static final byte[] PPSE_NAME = "2PAY.SYS.DDF01".getBytes();

    // =====================================================================
    // SELECT
    // =====================================================================

    public static byte[] selectPpse() {
        return select(PPSE_NAME);
    }

    public static byte[] selectAid(byte[] aid) {
        return select(aid);
    }

    private static byte[] select(byte[] data) {
        byte[] command = new byte[5 + data.length + 1];
        command[0] = (byte) 0x00; // CLA
        command[1] = (byte) 0xA4; // INS (SELECT)
        command[2] = (byte) 0x04; // P1
        command[3] = (byte) 0x00; // P2
        command[4] = (byte) data.length; // Lc
        System.arraycopy(data, 0, command, 5, data.length);
        command[command.length - 1] = (byte) 0x00; // Le
        return command;
    }

    // =====================================================================
    // GET PROCESSING OPTIONS (GPO)
    // =====================================================================

    /** GPO with empty PDOL. */
    public static byte[] getProcessingOptions() {
        return new byte[]{
                (byte) 0x80, (byte) 0xA8, (byte) 0x00, (byte) 0x00,
                (byte) 0x02, (byte) 0x83, (byte) 0x00, (byte) 0x00
        };
    }

    /** GPO with PDOL data (for cards that require terminal data). */
    public static byte[] getProcessingOptions(byte[] pdolData) {
        if (pdolData == null || pdolData.length == 0) {
            return getProcessingOptions();
        }
        int dataLen = pdolData.length;
        byte[] command = new byte[5 + 2 + dataLen + 1];
        command[0] = (byte) 0x80;
        command[1] = (byte) 0xA8;
        command[2] = (byte) 0x00;
        command[3] = (byte) 0x00;
        command[4] = (byte) (2 + dataLen);
        command[5] = (byte) 0x83;
        command[6] = (byte) dataLen;
        System.arraycopy(pdolData, 0, command, 7, dataLen);
        command[command.length - 1] = (byte) 0x00;
        return command;
    }

    // =====================================================================
    // READ RECORD
    // =====================================================================

    public static byte[] readRecord(int sfi, int recordNumber) {
        byte p2 = (byte) ((sfi << 3) | 4);
        return new byte[]{
                (byte) 0x00, (byte) 0xB2, (byte) recordNumber, p2, (byte) 0x00
        };
    }

    // =====================================================================
    // GENERATE APPLICATION CRYPTOGRAM (GENERATE AC)
    // =====================================================================

    public static final byte CRYPTOGRAM_ARQC = (byte) 0x80;
    public static final byte CRYPTOGRAM_TC   = (byte) 0x40;
    public static final byte CRYPTOGRAM_AAC  = (byte) 0x00;

    /**
     * GENERATE AC — asks chip card to produce Application Cryptogram (ARQC).
     *
     * Card responds with: 9F26 (ARQC), 9F27 (CID), 9F10 (IAD), 9F36 (ATC).
     *
     * @param cryptogramType CRYPTOGRAM_ARQC / TC / AAC
     * @param cdolData       CDOL1 data (terminal values matching card's CDOL1 list)
     */
    public static byte[] generateAC(byte cryptogramType, byte[] cdolData) {
        if (cdolData == null) cdolData = new byte[0];
        byte[] command = new byte[5 + cdolData.length + 1];
        command[0] = (byte) 0x80;
        command[1] = (byte) 0xAE;
        command[2] = cryptogramType;
        command[3] = (byte) 0x00;
        command[4] = (byte) cdolData.length;
        if (cdolData.length > 0) {
            System.arraycopy(cdolData, 0, command, 5, cdolData.length);
        }
        command[command.length - 1] = (byte) 0x00;
        return command;
    }

    /**
     * Build default CDOL1 data for GENERATE AC (NAPAS Domestic CHIP).
     *
     * Standard CDOL1 order (29 bytes total):
     *   9F02 (6) + 9F03 (6) + 9F1A (2) + 95 (5) + 5F2A (2) + 9A (3) + 9C (1) + 9F37 (4)
     */
    public static byte[] buildDefaultCdol1Data(
            byte[] amountAuth, byte[] amountOther, byte[] countryCode,
            byte[] tvr, byte[] currencyCode, byte[] txnDate,
            byte[] txnType, byte[] unpredNum) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeFixed(baos, amountAuth, 6);
        writeFixed(baos, amountOther, 6);
        writeFixed(baos, countryCode, 2);
        writeFixed(baos, tvr, 5);
        writeFixed(baos, currencyCode, 2);
        writeFixed(baos, txnDate, 3);
        writeFixed(baos, txnType, 1);
        writeFixed(baos, unpredNum, 4);
        return baos.toByteArray();
    }

    // =====================================================================
    // EXTERNAL AUTHENTICATE
    // =====================================================================

    /**
     * Send Issuer Authentication Data (tag 91) to card.
     * CLA=00 INS=82 P1=00 P2=00 Lc=len Data=issuerAuthData (no Le).
     */
    public static byte[] externalAuthenticate(byte[] issuerAuthData) {
        if (issuerAuthData == null || issuerAuthData.length == 0) {
            throw new IllegalArgumentException("Issuer Auth Data (tag 91) must not be empty");
        }
        byte[] command = new byte[5 + issuerAuthData.length];
        command[0] = (byte) 0x00;
        command[1] = (byte) 0x82;
        command[2] = (byte) 0x00;
        command[3] = (byte) 0x00;
        command[4] = (byte) issuerAuthData.length;
        System.arraycopy(issuerAuthData, 0, command, 5, issuerAuthData.length);
        return command;
    }

    // =====================================================================
    // GET DATA
    // =====================================================================

    /** GET DATA to read a specific tag (e.g., 9F36 ATC, 9F17 PIN Try Counter). */
    public static byte[] getData(byte tagHigh, byte tagLow) {
        return new byte[]{
                (byte) 0x80, (byte) 0xCA, tagHigh, tagLow, (byte) 0x00
        };
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static void writeFixed(ByteArrayOutputStream baos, byte[] data, int len) {
        if (data == null) data = new byte[0];
        if (data.length >= len) {
            baos.write(data, 0, len);
        } else {
            for (int i = 0; i < len - data.length; i++) {
                baos.write(0x00);
            }
            baos.write(data, 0, data.length);
        }
    }
}

