package com.example.mysoftpos.domain.model;

import java.util.Map;

/**
 * Standardized Data Model for Card Input.
 * Represents data captured from NFC CHIP (071/072), Manual Entry (012), or Magstripe (021/022).
 *
 * For NFC CHIP (Domestic NAPAS):
 *   - posEntryMode = "071" (Online PIN) or "072" (No CVM)
 *   - emvTags populated from chip via ReadCardDataUseCase
 *   - track2 populated from tag 57 (Track 2 Equivalent Data)
 *   - cardSequenceNumber from tag 5F34
 */
public class CardInputData {
    private final String pan;
    private final String expiryDate; // YYMM
    private final String posEntryMode; // "071"/"072" NFC CHIP, "012" Manual, "021"/"022" Magstripe
    private final String track2; // From tag 57 for NFC, from magstripe for swipe
    private String pinBlock; // Optional PIN Block (DE 52)

    // --- NFC CHIP fields ---
    private Map<Integer, byte[]> emvTags; // EMV tags read from chip (for DE 55)
    private String cardSequenceNumber;     // From tag 5F34, for DE 23 (e.g., "001")

    public CardInputData(String pan, String expiryDate, String posEntryMode, String track2) {
        this.pan = pan;
        this.expiryDate = expiryDate;
        this.posEntryMode = posEntryMode;
        this.track2 = track2;
    }

    public void setPinBlock(String pinBlock) {
        this.pinBlock = pinBlock;
    }

    public String getPinBlock() {
        return pinBlock;
    }

    public String getPan() {
        return pan;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public String getPosEntryMode() {
        return posEntryMode;
    }

    public String getTrack2() {
        return track2;
    }

    // --- EMV Tags (NFC CHIP) ---

    public void setEmvTags(Map<Integer, byte[]> emvTags) {
        this.emvTags = emvTags;
    }

    public Map<Integer, byte[]> getEmvTags() {
        return emvTags;
    }

    public boolean hasEmvData() {
        return emvTags != null && !emvTags.isEmpty();
    }

    // --- Card Sequence Number (DE 23) ---

    public void setCardSequenceNumber(String csn) {
        this.cardSequenceNumber = csn;
    }

    public String getCardSequenceNumber() {
        return cardSequenceNumber != null ? cardSequenceNumber : "000";
    }

    // --- NFC CHIP Detection ---

    /**
     * NFC contactless chip: DE 22 = 071 (Online PIN) or 072 (No CVM).
     */
    public boolean isNfcChip() {
        return "071".equals(posEntryMode) || "072".equals(posEntryMode);
    }

    public boolean isManualEntry() {
        return "011".equals(posEntryMode) || "012".equals(posEntryMode);
    }

    public boolean isMagstripe() {
        return "021".equals(posEntryMode) || "022".equals(posEntryMode);
    }

    @Override
    public String toString() {
        return "CardInputData{" +
                "pan='" + (pan != null ? maskPan(pan) : "null") + '\'' +
                ", expiryDate='" + expiryDate + '\'' +
                ", posEntryMode='" + posEntryMode + '\'' +
                ", hasEmvData=" + hasEmvData() +
                ", csn=" + getCardSequenceNumber() +
                '}';
    }

    private String maskPan(String pan) {
        if (pan.length() < 4)
            return "****";
        return "****" + pan.substring(pan.length() - 4);
    }
}

