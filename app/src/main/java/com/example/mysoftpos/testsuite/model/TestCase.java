package com.example.mysoftpos.testsuite.model;

/**
 * Represents a Test Case scenario for ISO 8583 testing.
 * Defines the fixed fields and transaction type.
 */
public class TestCase {
    private final String id;
    private final String name;           // "Purchase", "Balance Inquiry", etc.
    private final String description;
    private final String mti;            // Message Type Indicator
    private final String processingCode; // DE 3
    private final String posEntryMode;   // DE 22 (e.g., "011", "071")
    private final String channel;        // "POS", "ATM", "QRC"
    private final boolean requiresAmount;
    private final boolean requiresPIN;

    public TestCase(String id, String name, String description, String mti, 
                    String processingCode, String posEntryMode, String channel, 
                    boolean requiresAmount, boolean requiresPIN) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.mti = mti;
        this.processingCode = processingCode;
        this.posEntryMode = posEntryMode;
        this.channel = channel;
        this.requiresAmount = requiresAmount;
        this.requiresPIN = requiresPIN;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getMti() { return mti; }
    public String getProcessingCode() { return processingCode; }
    public String getPosEntryMode() { return posEntryMode; }
    public String getChannel() { return channel; }
    public boolean isRequiresAmount() { return requiresAmount; }
    public boolean isRequiresPIN() { return requiresPIN; }

    public String getDisplayName() {
        return channel + " - " + name;
    }
}
