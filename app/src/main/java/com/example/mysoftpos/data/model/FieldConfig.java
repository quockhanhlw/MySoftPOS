package com.example.mysoftpos.data.model;

public class FieldConfig {
    public String type; // FIXED, INPUT, AUTO, CONFIG
    public String value; // For FIXED
    public String param; // For INPUT (e.g. "amount")
    public String generator; // For AUTO (e.g. "STAN", "DATE", "RRN")
    public String configKey; // For CONFIG (e.g. "terminal_id")

    public enum Type {
        FIXED, INPUT, AUTO, CONFIG
    }
}
