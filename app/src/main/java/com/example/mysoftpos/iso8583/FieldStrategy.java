package com.example.mysoftpos.iso8583;

/**
 * Strategy Enum for determining how to populate a Data Element.
 */
public enum FieldStrategy {
    /** Hardcoded static value (e.g., 25="00") */
    FIXED,
    
    /** Direct input from User/Card Data (e.g., PAN, Expiry) */
    INPUT,
    
    /** Retrieved from ConfigManager (e.g., Terminal ID, Merchant ID) */
    CONFIG,
    
    /** Automatically generated System Trace Audit Number */
    AUTO_STAN,
    
    /** Automatically generated RRN based on STAN/Date */
    AUTO_RRN,
    
    /** Current Date/Time (DE 7, 12, 13, 15) */
    AUTO_DATE,
    
    /** Computed via complex Logic (DE 22, 55, etc.) */
    COMPUTED,
    
    /** Optional/Conditional (only if present) */
    OPTIONAL_INPUT
}
