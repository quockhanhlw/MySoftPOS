package com.example.mysoftpos.domain.usecase;

import android.util.Log;

import com.example.mysoftpos.nfc.CardTransceiver;
import com.example.mysoftpos.nfc.ApduCommandBuilder;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.emv.EmvTlvCodec;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read Card Data via NFC — FAST & CRASH-SAFE.
 *
 * Flow: PPSE → SELECT AID → GPO → READ RECORD → done.
 * No GENERATE AC (skip để giảm thời gian, ARQC sẽ build phía server nếu cần).
 * Total: 3-5 APDU commands, < 800ms.
 */
public class ReadCardDataUseCase {

    private static final String TAG = "ReadCardUseCase";

    private static final byte[] AID_NAPAS      = hexToBytes("F0000007040001");
    private static final byte[] AID_NAPAS_2    = hexToBytes("A000000727");
    private static final byte[] AID_NAPAS_3    = hexToBytes("A0000007271010");
    private static final byte[] AID_VCCS       = hexToBytes("D4100000030001");
    private static final byte[] AID_VISA       = hexToBytes("A0000000031010");
    private static final byte[] AID_VISA_SHORT = hexToBytes("A000000003");
    private static final byte[] AID_MC         = hexToBytes("A0000000041010");
    private static final byte[] AID_MC_SHORT   = hexToBytes("A000000004");

    private final CardTransceiver transceiver;

    public ReadCardDataUseCase(CardTransceiver transceiver) {
        this.transceiver = transceiver;
    }

    public CardInputData execute() throws IOException {
        long t0 = System.currentTimeMillis();
        Map<Integer, byte[]> emvTags = new LinkedHashMap<>();

        // ── Step 1: SELECT PPSE → discover AID ──────────────────────────
        byte[] selectedAid = null;
        byte[] selectResp = null;

        try {
            byte[] ppse = send(ApduCommandBuilder.selectPpse());
            if (ppse != null) {
                byte[] aid = findTag(ppse, 0x4F);
                if (aid != null && aid.length >= 5) {
                    Log.d(TAG, "PPSE found AID: " + bytesToHex(aid));
                    byte[] r = send(ApduCommandBuilder.selectAid(aid));
                    if (r != null && isOk(r)) {
                        selectedAid = aid;
                        selectResp = r;
                    }
                } else {
                    Log.d(TAG, "PPSE ok but no AID found in FCI");
                }
            }
        } catch (IOException e) {
            if (isTagLost(e)) throw e;
            Log.d(TAG, "PPSE failed: " + e.getMessage());
        }

        // ── Step 2: Fallback — direct SELECT AID ────────────────────────
        if (selectedAid == null) {
            byte[][] aids = {AID_NAPAS, AID_NAPAS_2, AID_NAPAS_3, AID_VCCS,
                             AID_VISA, AID_VISA_SHORT, AID_MC, AID_MC_SHORT};
            for (byte[] aid : aids) {
                try {
                    byte[] r = send(ApduCommandBuilder.selectAid(aid));
                    if (r != null && r.length >= 2) {
                        int sw = ((r[r.length-2] & 0xFF) << 8) | (r[r.length-1] & 0xFF);
                        Log.d(TAG, "SELECT " + bytesToHex(aid) + " → SW=" + String.format("%04X", sw)
                                + " len=" + r.length);
                        // Accept: 9000, 62XX, or any response that has FCI data (len > 2)
                        // and is not a hard error (6A82=not found, 6A81=not supported, 6999=applet error)
                        if (isOk(r) || (r.length > 2 && sw != 0x6A82 && sw != 0x6A81 && sw != 0x6999)) {
                            selectedAid = aid;
                            selectResp = r;
                            Log.d(TAG, "AID matched: " + bytesToHex(aid));
                            break;
                        }
                    } else {
                        Log.d(TAG, "SELECT " + bytesToHex(aid) + " → null/empty");
                    }
                } catch (IOException e) {
                    Log.d(TAG, "SELECT " + bytesToHex(aid) + " → IOException: " + e.getMessage());
                    if (isTagLost(e)) throw e;
                }
            }
        }

        if (selectedAid == null) {
            Log.e(TAG, "No AID matched after trying all candidates");
            throw new IOException("Thẻ không được hỗ trợ. Vui lòng thử lại.");
        }

        emvTags.put(0x84, selectedAid);
        collectTags(selectResp, emvTags);

        Log.d(TAG, "SELECT " + (System.currentTimeMillis() - t0) + "ms");

        // ── Step 3: GET PROCESSING OPTIONS ───────────────────────────────
        byte[] pdol = emvTags.get(0x9F38);
        byte[] gpo;
        if (pdol != null && pdol.length > 0) {
            gpo = send(ApduCommandBuilder.getProcessingOptions(buildPdolData(pdol)));
        } else {
            gpo = send(ApduCommandBuilder.getProcessingOptions());
        }

        if (gpo == null || !isOk(gpo)) {
            throw new IOException("Thẻ từ chối (GPO)");
        }
        collectTags(gpo, emvTags);

        Log.d(TAG, "GPO " + (System.currentTimeMillis() - t0) + "ms");

        // ── Step 4: READ RECORD (AFL-based, stop khi có tag 57) ──────────
        if (!emvTags.containsKey(0x57)) {
            byte[] afl = emvTags.get(0x94);
            if (afl != null && afl.length >= 4) {
                readAfl(afl, emvTags);
            } else {
                // Minimal: SFI 1, record 1 only
                try {
                    byte[] r = send(ApduCommandBuilder.readRecord(1, 1));
                    if (r != null && isOk(r)) collectTags(r, emvTags);
                } catch (IOException ignored) {}
            }
        }

        Log.d(TAG, "READ " + (System.currentTimeMillis() - t0) + "ms");

        // ── Verify Tag 57 ────────────────────────────────────────────────
        byte[] tag57 = emvTags.get(0x57);
        if (tag57 == null) {
            throw new IOException("Không đọc được dữ liệu thẻ");
        }

        EmvTlvCodec.Track2Data t2 = EmvTlvCodec.decodeTag57(tag57);
        if (t2 == null) {
            throw new IOException("Dữ liệu thẻ không hợp lệ");
        }

        // ── Build terminal EMV tags (cho DE 55 sau này) ──────────────────
        setTerminalTags(emvTags);

        Log.d(TAG, "DONE " + (System.currentTimeMillis() - t0) + "ms, tags=" + emvTags.size());

        // ── Result ───────────────────────────────────────────────────────
        CardInputData card = new CardInputData(t2.pan, t2.expiryYYMM, "072",
                EmvTlvCodec.buildDE35FromTag57(tag57));
        card.setEmvTags(emvTags);
        card.setCardSequenceNumber(EmvTlvCodec.extractCardSequenceNumber(emvTags));
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────
    // READ RECORD — AFL-based, stop khi có tag 57
    // ─────────────────────────────────────────────────────────────────────

    private void readAfl(byte[] afl, Map<Integer, byte[]> tags) {
        for (int i = 0; i + 3 < afl.length; i += 4) {
            int sfi = (afl[i] & 0xFF) >> 3;
            int first = afl[i + 1] & 0xFF;
            int last = afl[i + 2] & 0xFF;
            if (sfi < 1 || sfi > 30 || first < 1 || last < first) continue;

            for (int rec = first; rec <= last; rec++) {
                try {
                    byte[] r = send(ApduCommandBuilder.readRecord(sfi, rec));
                    if (r != null && isOk(r)) {
                        collectTags(r, tags);
                    }
                } catch (IOException e) {
                    if (isTagLost(e)) return; // thẻ mất → dừng
                }
                // Đã có tag 57 → dừng đọc record
                if (tags.containsKey(0x57)) return;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Terminal tags (set lên cho DE 55 builder dùng sau này)
    // ─────────────────────────────────────────────────────────────────────

    private void setTerminalTags(Map<Integer, byte[]> tags) {
        putIfAbsent(tags, EmvTlvCodec.TAG_AMOUNT_AUTHORIZED, EmvTlvCodec.encodeBcdFixed("0", 6));
        putIfAbsent(tags, EmvTlvCodec.TAG_AMOUNT_OTHER, EmvTlvCodec.encodeBcdFixed("0", 6));
        putIfAbsent(tags, EmvTlvCodec.TAG_TERMINAL_COUNTRY, new byte[]{0x07, 0x04});
        putIfAbsent(tags, EmvTlvCodec.TAG_TXN_CURRENCY_CODE, new byte[]{0x07, 0x04});
        putIfAbsent(tags, EmvTlvCodec.TAG_TXN_DATE, EmvTlvCodec.encodeTransactionDate());
        putIfAbsent(tags, EmvTlvCodec.TAG_TXN_TYPE, new byte[]{0x00});
        putIfAbsent(tags, EmvTlvCodec.TAG_UNPREDICTABLE_NUMBER, EmvTlvCodec.generateUnpredictableNumber());
        putIfAbsent(tags, EmvTlvCodec.TAG_TVR, new byte[5]);
    }

    private void putIfAbsent(Map<Integer, byte[]> map, int key, byte[] val) {
        if (!map.containsKey(key)) map.put(key, val);
    }

    // ─────────────────────────────────────────────────────────────────────
    // PDOL builder
    // ─────────────────────────────────────────────────────────────────────

    private byte[] buildPdolData(byte[] pdol) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int off = 0;
        while (off < pdol.length) {
            int b = pdol[off++] & 0xFF;
            int tagNum = b;
            if ((b & 0x1F) == 0x1F && off < pdol.length) {
                tagNum = (b << 8) | (pdol[off++] & 0xFF);
            }
            if (off >= pdol.length) break;
            int len = pdol[off++] & 0xFF;

            byte[] v = pdolValue(tagNum, len);
            out.write(v, 0, v.length);
        }
        return out.toByteArray();
    }

    private byte[] pdolValue(int tag, int len) {
        byte[] r;
        switch (tag) {
            case 0x9F66: r = new byte[]{(byte)0xA6, 0x20, 0x00, 0x00}; break;
            case 0x9F02: r = new byte[6]; break;
            case 0x9F03: r = new byte[6]; break;
            case 0x9F1A: r = new byte[]{0x07, 0x04}; break;
            case 0x5F2A: r = new byte[]{0x07, 0x04}; break;
            case 0x9A:   r = EmvTlvCodec.encodeTransactionDate(); break;
            case 0x9C:   r = new byte[]{0x00}; break;
            case 0x9F37: r = EmvTlvCodec.generateUnpredictableNumber(); break;
            case 0x9F35: r = new byte[]{0x22}; break;
            case 0x9F33: r = new byte[]{(byte)0xE0, (byte)0xF0, (byte)0xC8}; break;
            default:     r = new byte[len]; break;
        }
        if (r.length == len) return r;
        byte[] fit = new byte[len];
        System.arraycopy(r, 0, fit, Math.max(0, len - r.length), Math.min(r.length, len));
        return fit;
    }

    // ─────────────────────────────────────────────────────────────────────
    // APDU send with SW chaining
    // ─────────────────────────────────────────────────────────────────────

    private byte[] send(byte[] cmd) throws IOException {
        byte[] r = transceiver.transceive(cmd);
        if (r == null || r.length < 2) return null;

        int sw1 = r[r.length - 2] & 0xFF;
        int sw2 = r[r.length - 1] & 0xFF;

        // SW1=61 → GET RESPONSE
        if (sw1 == 0x61) {
            byte[] gr = transceiver.transceive(new byte[]{0x00, (byte)0xC0, 0x00, 0x00, (byte)sw2});
            if (gr != null && gr.length > 2 && r.length > 2) {
                byte[] merged = new byte[r.length - 2 + gr.length];
                System.arraycopy(r, 0, merged, 0, r.length - 2);
                System.arraycopy(gr, 0, merged, r.length - 2, gr.length);
                return merged;
            }
            return gr;
        }
        return r;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Tag extraction & collection
    // ─────────────────────────────────────────────────────────────────────

    /** Find a tag in APDU response (strips SW). Recursive into templates. */
    private byte[] findTag(byte[] resp, int targetTag) {
        if (resp == null || resp.length <= 2) return null;
        byte[] data = Arrays.copyOfRange(resp, 0, resp.length - 2);
        return findTagIn(data, targetTag, 0);
    }

    private byte[] findTagIn(byte[] data, int target, int depth) {
        if (depth > 5 || data == null || data.length < 2) return null;
        try {
            Map<Integer, byte[]> tags = EmvTlvCodec.parseDE55Bytes(data);
            for (Map.Entry<Integer, byte[]> e : tags.entrySet()) {
                if (e.getKey() == target) return e.getValue();
                if (isTemplate(e.getKey()) && e.getValue().length > 2) {
                    byte[] found = findTagIn(e.getValue(), target, depth + 1);
                    if (found != null) return found;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Collect all tags from APDU response into map. */
    private void collectTags(byte[] resp, Map<Integer, byte[]> out) {
        if (resp == null || resp.length <= 2) return;
        byte[] data = Arrays.copyOfRange(resp, 0, resp.length - 2);
        if (data.length == 0) return;
        try {
            parseInto(data, out, 0);
        } catch (Exception e) {
            Log.w(TAG, "TLV parse error: " + e.getMessage());
        }
    }

    private void parseInto(byte[] data, Map<Integer, byte[]> out, int depth) {
        if (depth > 5 || data == null || data.length == 0) return;
        Map<Integer, byte[]> parsed = EmvTlvCodec.parseDE55Bytes(data);
        for (Map.Entry<Integer, byte[]> e : parsed.entrySet()) {
            out.put(e.getKey(), e.getValue());
            if (isTemplate(e.getKey()) && e.getValue().length > 2) {
                parseInto(e.getValue(), out, depth + 1);
            }
        }
    }

    private boolean isTemplate(int t) {
        return t == 0x70 || t == 0x77 || t == 0x6F || t == 0xA5 || t == 0x61 || t == 0xBF0C;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private boolean isOk(byte[] r) {
        if (r == null || r.length < 2) return false;
        int sw = ((r[r.length - 2] & 0xFF) << 8) | (r[r.length - 1] & 0xFF);
        return sw == 0x9000 || (sw >= 0x6200 && sw <= 0x62FF);
    }

    private boolean isTagLost(IOException e) {
        return e instanceof android.nfc.TagLostException
                || (e.getMessage() != null && e.getMessage().contains("Tag was lost"));
    }

    private static byte[] hexToBytes(String h) {
        byte[] d = new byte[h.length() / 2];
        for (int i = 0; i < d.length; i++)
            d[i] = (byte)((Character.digit(h.charAt(i*2),16) << 4) + Character.digit(h.charAt(i*2+1),16));
        return d;
    }

    private static String bytesToHex(byte[] b) {
        if (b == null) return "";
        char[] hex = "0123456789ABCDEF".toCharArray();
        char[] c = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            c[i * 2] = hex[(b[i] >> 4) & 0xF];
            c[i * 2 + 1] = hex[b[i] & 0xF];
        }
        return new String(c);
    }
}

