package com.example.mysoftpos.testsuite.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room Entity for storing test execution history.
 * 
 * Status Flow:
 * ═══════════════════════════════════════════════════════════════════
 * PENDING  → Insert BEFORE socket send
 *    ↓
 * SUCCESS  → Response Code = "00"
 * FAIL     → Response Code ≠ "00" 
 * TIMEOUT  → SocketTimeoutException (30s)
 * ERROR    → IOException (Connection Refused, Network Error)
 * ═══════════════════════════════════════════════════════════════════
 */
@Entity(tableName = "test_results")
public class TestResult {
    
    // ═══════════════════════════════════════════════════════════════════
    //                         STATUS ENUM
    // ═══════════════════════════════════════════════════════════════════
    
    public enum Status {
        PENDING,    // Inserted before network call
        SUCCESS,    // Response Code = "00"
        FAIL,       // Response Code ≠ "00"
        TIMEOUT,    // SocketTimeoutException
        ERROR       // IOException (Connection Refused, etc.)
    }
    
    // ═══════════════════════════════════════════════════════════════════
    //                         FIELDS
    // ═══════════════════════════════════════════════════════════════════
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public String testCaseId;
    public String testCaseName;
    public String cardProfileId;
    public String cardName;
    public String amount;
    public String stan;           // DE 11 - Trace Number
    
    // Request Info
    public String requestMti;
    public String posEntryMode;   // DE 22
    public String requestHex;
    
    // Response Info
    public String responseMti;
    public String responseCode;   // DE 39
    public String rrn;            // DE 37
    public String authCode;       // DE 38
    public String responseHex;
    
    // Status (replaces boolean success)
    public String status;         // PENDING, SUCCESS, FAIL, TIMEOUT, ERROR
    public String errorMessage;
    public long executionTimeMs;
    public long timestamp;
    
    public TestResult() {
        this.timestamp = System.currentTimeMillis();
        this.status = Status.PENDING.name();
    }
    
    // ═══════════════════════════════════════════════════════════════════
    //                         HELPERS
    // ═══════════════════════════════════════════════════════════════════
    
    public boolean isSuccess() {
        return Status.SUCCESS.name().equals(status);
    }
    
    public Status getStatusEnum() {
        try {
            return Status.valueOf(status);
        } catch (Exception e) {
            return Status.ERROR;
        }
    }
    
    public String getStatusText() {
        Status s = getStatusEnum();
        switch (s) {
            case SUCCESS: return "PASS";
            case FAIL: return "FAIL";
            case TIMEOUT: return "TIMEOUT";
            case ERROR: return "ERROR";
            case PENDING: return "PENDING";
            default: return "UNKNOWN";
        }
    }
    
    public String getFormattedTime() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US);
        return sdf.format(new java.util.Date(timestamp));
    }
    
    // For backward compatibility with existing code
    public boolean getSuccess() {
        return isSuccess();
    }
}
