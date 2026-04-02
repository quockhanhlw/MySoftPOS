package com.example.mysoftpos.utils.format;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class DateTimeFormatUtils {

    private static final String UI_PATTERN = "HH:mm:ss dd/MM/yyyy";

    private DateTimeFormatUtils() {
    }

    private static DateTimeFormatter uiFormatter() {
        return DateTimeFormatter.ofPattern(UI_PATTERN, Locale.getDefault());
    }

    public static String formatEpochMillis(long millis) {
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        return uiFormatter().format(dt);
    }

    public static String formatBackendTimestamp(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.trim().isEmpty()) {
            return "-";
        }
        String raw = rawTimestamp.trim();

        try {
            LocalDateTime dt = LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return uiFormatter().format(dt);
        } catch (DateTimeParseException ignored) {
        }

        try {
            long epochMillis = Long.parseLong(raw);
            return formatEpochMillis(epochMillis);
        } catch (Exception ignored) {
        }

        return raw;
    }

    public static String normalizeDisplayTimestamp(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.trim().isEmpty()) {
            return "-";
        }
        String raw = rawTimestamp.trim();

        // Already in target pattern
        try {
            LocalDateTime dt = LocalDateTime.parse(raw, uiFormatter());
            return uiFormatter().format(dt);
        } catch (Exception ignored) {
        }

        return formatBackendTimestamp(raw);
    }
}

