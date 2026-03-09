package com.example.mysoftpos.iso8583;

import java.util.Map;
import java.util.Set;

/**
 * Backward-compatible wrapper for the current ISO message implementation.
 * Keeps older JVM tests compiling against the root iso8583 package.
 */
public final class IsoMessage {
    private final com.example.mysoftpos.iso8583.message.IsoMessage delegate;

    public IsoMessage(String mti) {
        this(new com.example.mysoftpos.iso8583.message.IsoMessage(mti));
    }

    private IsoMessage(com.example.mysoftpos.iso8583.message.IsoMessage delegate) {
        this.delegate = delegate;
    }

    static IsoMessage fromInternal(com.example.mysoftpos.iso8583.message.IsoMessage internal) {
        return new IsoMessage(internal);
    }

    com.example.mysoftpos.iso8583.message.IsoMessage unwrap() {
        return delegate;
    }

    public String getMti() {
        return delegate.getMti();
    }

    public IsoMessage setField(int field, String value) {
        delegate.setField(field, value);
        return this;
    }

    public String getField(int field) {
        return delegate.getField(field);
    }

    public boolean hasField(int field) {
        return delegate.hasField(field);
    }

    public Set<Integer> getFieldNumbers() {
        return delegate.getFieldNumbers();
    }

    public Map<Integer, String> getFields() {
        return delegate.getFields();
    }
}

