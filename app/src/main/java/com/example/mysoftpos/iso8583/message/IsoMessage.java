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

    /** Debug string to quickly inspect message content (NOT for production logs with PAN/PIN). */
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MTI=").append(mti);
        Set<Integer> keys = new TreeSet<>(Comparator.naturalOrder());
        keys.addAll(fields.keySet());
        for (int f : keys) {
            sb.append(" | F").append(f).append('=');
            String v = fields.get(f);
            sb.append(v);
        }
        return sb.toString();
    }
}







