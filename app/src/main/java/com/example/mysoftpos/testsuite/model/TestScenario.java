package com.example.mysoftpos.testsuite.model;

import java.util.HashMap;
import java.util.Map;

public class TestScenario {
    private String mti;
    private String description;
    private Map<Integer, String> fields = new HashMap<>();

    public TestScenario(String mti, String description) {
        this.mti = mti;
        this.description = description;
    }

    public void setField(int bit, String value) {
        fields.put(bit, value);
    }

    public String getField(int bit) {
        return fields.get(bit);
    }

    public String getDescription() {
        return description;
    }

    // Helper for DE 22/Pos Mode
    public String getPosEntryMode() {
        return getField(22);
    }
}
