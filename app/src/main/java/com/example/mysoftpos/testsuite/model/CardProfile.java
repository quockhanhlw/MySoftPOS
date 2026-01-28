package com.example.mysoftpos.testsuite.model;

/**
 * Represents a Test Card profile for ISO 8583 testing.
 * Pre-configured cards for dropdown selection in Test Runner.
 */
public class CardProfile {
    private final String id;
    private final String name;        // Display name (e.g., "BIDV Chip Card")
    private final String pan;         // Card number
    private final String expiryDate;  // YYMM
    private final String track2;      // Full Track 2 data
    private final String cardType;    // "CHIP", "MAGSTRIPE", "NFC"
    private final String issuerName;  // Bank name

    public CardProfile(String id, String name, String pan, String expiryDate, 
                       String track2, String cardType, String issuerName) {
        this.id = id;
        this.name = name;
        this.pan = pan;
        this.expiryDate = expiryDate;
        this.track2 = track2;
        this.cardType = cardType;
        this.issuerName = issuerName;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getPan() { return pan; }
    public String getExpiryDate() { return expiryDate; }
    public String getTrack2() { return track2; }
    public String getCardType() { return cardType; }
    public String getIssuerName() { return issuerName; }

    @Override
    public String toString() {
        return name + " (" + issuerName + ")";
    }
}
