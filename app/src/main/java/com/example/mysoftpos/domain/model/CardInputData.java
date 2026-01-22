package com.example.mysoftpos.domain.model;

import java.util.Map;
import java.util.Collections;

/**
 * Standardized Data Model for Card Input.
 * Represents data captured from either NFC (051) or Manual Entry (012).
 */
public class CardInputData {
    private final String pan;
    private final String expiryDate; // YYMM
    private final String posEntryMode; // "051" for NFC, "012" for Manual
    private final String track2; // Nullable, only for NFC
    private final Map<String, String> emvTags;

    public CardInputData(String pan, String expiryDate, String posEntryMode, String track2) {
        this(pan, expiryDate, posEntryMode, track2, Collections.emptyMap());
    }

    public CardInputData(String pan, String expiryDate, String posEntryMode, String track2, Map<String, String> emvTags) {
        this.pan = pan;
        this.expiryDate = expiryDate;
        this.posEntryMode = posEntryMode;
        this.track2 = track2;
        this.emvTags = emvTags != null ? emvTags : Collections.emptyMap();
    }
    
    // MainDashboardActivity Usage (6 args: pan, expiry, track2, posMode, pin, emv)
    public CardInputData(String pan, String expiryDate, String track2, String posEntryMode, String pinBlock, Map<String, String> emvTags) {
         this(pan, expiryDate, posEntryMode, track2, emvTags);
         // pinBlock ignored for now as it's not in the fields yet, or add it if needed
    }

    public String getPan() {
        return pan;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    // Helper to get POS Entry Mode
    public String getPosEntryMode() {
        return isContactless() ? "071" : "011"; 
    }

    public String getTrack2() {
        return track2;
    }
    
    public boolean isContactless() {
        return track2 != null && !track2.isEmpty();
    }

    public Map<String, String> getEmvTags() {
        return emvTags;
    }

    @Override
    public String toString() {
        return "CardInputData{" +
                "pan='" + (pan != null ? maskPan(pan) : "null") + '\'' +
                ", expiryDate='" + expiryDate + '\'' +
                ", posEntryMode='" + posEntryMode + '\'' +
                '}';
    }

    private String maskPan(String pan) {
        if (pan.length() < 4) return "****";
        return "****" + pan.substring(pan.length() - 4);
    }
}
