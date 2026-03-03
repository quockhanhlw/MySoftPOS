package com.example.mysoftpos.iso8583.message;
import com.example.mysoftpos.iso8583.spec.IsoField;
import com.example.mysoftpos.iso8583.message.IsoMessage;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Minimal ISO8583 message container for building requests.
 *
 * Note: Packing/unpacking depends on host packager. For now we focus on field presence correctness.
 */
public final class IsoMessage {
    private final String mti;
    private final Map<Integer, String> fields = new LinkedHashMap<>();

    public IsoMessage(String mti) {
        if (mti == null || mti.length() != 4) {
            throw new IllegalArgumentException("MTI must be 4 chars");
        }
        this.mti = mti;
    }

    public String getMti() {
        return mti;
    }

    public IsoMessage setField(int field, String value) {
        IsoField.checkValid(field);
        if (value == null) {
            fields.remove(field);
        } else {
            fields.put(field, value);
        }
        return this;
    }

    public String getField(int field) {
        return fields.get(field);
    }

    public boolean hasField(int field) {
        return fields.containsKey(field);
    }

    public Set<Integer> getFieldNumbers() {
        return Collections.unmodifiableSet(new TreeSet<>(fields.keySet()));
    }

    public Map<Integer, String> getFields() {
        return Collections.unmodifiableMap(fields);
    }

    /** Debug string to quickly inspect message content.
     *  Sensitive fields (PAN, Track2, PIN) are masked to comply with PCI-DSS.
     *  KHÔNG bao giờ in ra giá trị thực của DE 2, DE 35, DE 52 trong log. */
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MTI=").append(mti);
        Set<Integer> keys = new TreeSet<>(Comparator.naturalOrder());
        keys.addAll(fields.keySet());
        for (int f : keys) {
            sb.append(" | F").append(f).append('=');
            String v = fields.get(f);
            sb.append(maskSensitiveField(f, v));
        }
        return sb.toString();
    }

    /**
     * Che giấu dữ liệu nhạy cảm theo PCI-DSS:
     *  - DE 2  (PAN):        412345xxxxxx2345  (giữ 6 đầu + 4 cuối, che giữa)
     *  - DE 35 (Track 2):    4123456789012345=2512xxxx (che từ ký tự thứ 7 trở đi)
     *  - DE 52 (PIN Block):  **************** (ẩn hoàn toàn)
     */
    public static String maskSensitiveField(int fieldNumber, String value) {
        if (value == null) return "null";
        switch (fieldNumber) {
            case 2:  // PAN – giữ 6 BIN đầu và 4 số cuối
                return maskPan(value);
            case 35: // Track 2 – che phần sau dấu '='
                return maskTrack2(value);
            case 52: // PIN Block – ẩn hoàn toàn
                return "****************";
            default:
                return value;
        }
    }

    private static String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return "****";
        int keep = 6;
        int tail = 4;
        int maskLen = pan.length() - keep - tail;
        if (maskLen <= 0) return "****";
        return pan.substring(0, keep)
                + "*".repeat(maskLen)   // requires minSdk >= 26
                + pan.substring(pan.length() - tail);
    }

    private static String maskTrack2(String track2) {
        if (track2 == null) return "null";
        // Track 2 format: PAN=YYMM…  – che toàn bộ phần sau dấu '='
        int sep = track2.indexOf('=');
        if (sep < 0) {
            // Không có dấu '=', che phần sau BIN 6 số
            if (track2.length() <= 6) return track2;
            return track2.substring(0, 6) + "*".repeat(track2.length() - 6); // requires minSdk >= 26
        }
        String pan  = track2.substring(0, sep);
        String rest = track2.substring(sep + 1);
        return maskPan(pan) + "=" + "*".repeat(rest.length()); // requires minSdk >= 26
    }
}







