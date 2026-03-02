package com.example.mysoftpos.testsuite.model;

import java.io.Serializable;
import java.util.UUID;

public class Scheme implements Serializable {
    private String id;
    private String name;
    private String prefix;
    private String iconLetter;
    private String color;
    private boolean builtIn;

    // Connection config
    private String serverIp;
    private int serverPort;
    private int timeout; // milliseconds

    // Terminal / Merchant config (per-scheme)
    private String terminalId;       // DE 41
    private String merchantId;       // DE 42
    private String mcc;              // DE 18 (Merchant Category Code)
    private String acquirerId;       // DE 32
    private String currencyCode;     // DE 49
    private String countryCode;      // DE 19
    private String merchantName;     // DE 43 - merchant name part
    private String merchantLocation; // DE 43 - location part
    private String merchantCountry;  // DE 43 - country part (e.g. "VNM")
    private String posConditionCode; // DE 25

    public Scheme() {
        this.id = UUID.randomUUID().toString();
        this.serverIp = "";
        this.serverPort = 0;
        this.timeout = 30000;
        this.terminalId = "";
        this.merchantId = "";
        this.mcc = "";
        this.acquirerId = "";
        this.currencyCode = "";
        this.countryCode = "";
        this.merchantName = "";
        this.merchantLocation = "";
        this.merchantCountry = "";
        this.posConditionCode = "00";
    }

    public Scheme(String name, String prefix, String iconLetter, String color, boolean builtIn) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.prefix = prefix;
        this.iconLetter = iconLetter;
        this.color = color;
        this.builtIn = builtIn;
        this.serverIp = "";
        this.serverPort = 0;
        this.timeout = 30000;
        this.terminalId = "";
        this.merchantId = "";
        this.mcc = "";
        this.acquirerId = "";
        this.currencyCode = "";
        this.countryCode = "";
        this.merchantName = "";
        this.merchantLocation = "";
        this.merchantCountry = "";
        this.posConditionCode = "00";
    }

    // ── Identity ──
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getIconLetter() {
        return iconLetter;
    }

    public void setIconLetter(String iconLetter) {
        this.iconLetter = iconLetter;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    // ── Connection ──
    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    // ── Terminal / Merchant ──
    public String getTerminalId() { return terminalId; }
    public void setTerminalId(String terminalId) { this.terminalId = terminalId; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getMcc() { return mcc; }
    public void setMcc(String mcc) { this.mcc = mcc; }

    public String getAcquirerId() { return acquirerId; }
    public void setAcquirerId(String acquirerId) { this.acquirerId = acquirerId; }


    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }

    public String getMerchantLocation() { return merchantLocation; }
    public void setMerchantLocation(String merchantLocation) { this.merchantLocation = merchantLocation; }

    public String getMerchantCountry() { return merchantCountry; }
    public void setMerchantCountry(String merchantCountry) { this.merchantCountry = merchantCountry; }

    public String getPosConditionCode() { return posConditionCode; }
    public void setPosConditionCode(String posConditionCode) { this.posConditionCode = posConditionCode; }


    /** Returns true if this scheme has a valid IP:port configured */
    public boolean hasConnectionConfig() {
        return serverIp != null && !serverIp.isEmpty() && serverPort > 0;
    }

    /** Returns true if terminal/merchant fields are configured */
    public boolean hasTerminalConfig() {
        return terminalId != null && !terminalId.isEmpty()
                && merchantId != null && !merchantId.isEmpty();
    }

    /**
     * Build DE 43 merchant name/location string.
     * ISO 8583 format: [Name 22 chars][space][Location 13 chars][space][Country 3 chars] = 40 chars
     * Returns empty string if all parts are empty.
     */
    public String buildMerchantNameLocation() {
        String n = merchantName != null ? merchantName.trim() : "";
        String l = merchantLocation != null ? merchantLocation.trim() : "";
        String c = merchantCountry != null ? merchantCountry.trim().toUpperCase() : "";
        if (n.isEmpty() && l.isEmpty() && c.isEmpty()) return "";

        // Truncate to max lengths
        if (n.length() > 22) n = n.substring(0, 22);
        if (l.length() > 13) l = l.substring(0, 13);
        if (c.length() > 3) c = c.substring(0, 3);

        // Pad: name=22, location=13, country=3 → total ~40 chars
        // Format: "name                  location      country"
        StringBuilder sb = new StringBuilder(String.format(java.util.Locale.ROOT, "%-22s%-13s", n, l));
        // Ensure base is exactly 37 chars before country
        if (sb.length() > 37) sb.setLength(37);
        else while (sb.length() < 37) sb.append(' ');

        String cPart = c.isEmpty() ? "   " : String.format(java.util.Locale.ROOT, "%-3s", c);
        return sb.append(cPart).toString();
    }
}
