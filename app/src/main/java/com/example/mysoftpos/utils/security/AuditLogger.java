package com.example.mysoftpos.utils.security;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PA-DSS 4.x: Audit logging service.
 * Captures all security-relevant events as required by PA-DSS.
 * Writes asynchronously to avoid blocking the calling thread.
 *
 * Each log entry contains:
 * - User identification
 * - Event type
 * - Date and timestamp
 * - Success or failure indicator
 * - Event origin (source)
 * - Identity/name of affected resource
 */
public final class AuditLogger {

    private AuditLogger() {
    }

    private static final String DIR_NAME = "audit_logs";
    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US);
    private static final DateTimeFormatter FMT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS",
            Locale.US);

    // Single-thread executor for async log writes (PA-DSS 4.x: non-blocking)
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AuditLogger");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /**
     * PA-DSS 4.1-4.5: Log a security event asynchronously.
     *
     * @param context   Application context
     * @param userId    User identification (or "SYSTEM" for system events)
     * @param eventType Type of event
     * @param success   True if action succeeded, false if failed
     * @param source    Origin of event (class/component name)
     * @param details   Description of affected resource/data
     */
    public static void log(Context context, String userId, String eventType,
            boolean success, String source, String details) {
        if (context == null)
            return;

        // Capture timestamp and context immediately on calling thread
        final LocalDateTime now = LocalDateTime.now();
        final Context appCtx = context.getApplicationContext();
        final String safeDetails = PanMasker.mask(details);

        // Write asynchronously to avoid blocking login/transaction flows
        WRITER.execute(() -> writeEntry(appCtx, now, userId, eventType, success, source, safeDetails));
    }

    /** Convenience: log with "SYSTEM" as userId */
    public static void logSystem(Context context, String eventType,
            boolean success, String source, String details) {
        log(context, "SYSTEM", eventType, success, source, details);
    }

    private static void writeEntry(Context appCtx, LocalDateTime now, String userId,
            String eventType, boolean success, String source,
            String safeDetails) {
        try {
            // PA-DSS 4.x: Write to app-specific internal storage (not external)
            File dir = new File(appCtx.getFilesDir(), DIR_NAME);
            if (!dir.exists() && !dir.mkdirs())
                return;

            String dateStr = FMT_DATE.format(now);
            File file = new File(dir, "audit_" + dateStr + ".log");

            // PA-DSS 4.3: Pipe-delimited format for centralized logging
            String timestamp = FMT_TIME.format(now);
            String status = success ? "SUCCESS" : "FAILURE";
            String entry = String.format(Locale.US, "%s|%s|%s|%s|%s|%s\n",
                    timestamp, userId, eventType, status, source, safeDetails);

            try (FileOutputStream fos = new FileOutputStream(file, true);
                    OutputStreamWriter writer = new OutputStreamWriter(fos)) {
                writer.write(entry);
            }
        } catch (Exception ignored) {
            // Audit log failure must not crash the app
        }
    }
}
