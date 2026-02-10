package com.example.mysoftpos.testsuite.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class TestScenario implements Serializable {
    private String mti;
    private String description;
    private Map<Integer, String> fields = new HashMap<>();
    private boolean selected;
    private String userPin;

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

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getUserPin() {
        return userPin;
    }

    public void setUserPin(String userPin) {
        this.userPin = userPin;
    }

    public String getPosEntryMode() {
        return getField(22);
    }
}
