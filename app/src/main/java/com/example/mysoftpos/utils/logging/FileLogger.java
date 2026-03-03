package com.example.mysoftpos.utils.logging;

import com.example.mysoftpos.iso8583.util.StandardIsoPacker;
import com.example.mysoftpos.utils.security.PanMasker;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * PA-DSS 1.x / 4.x: Secure ISO Packet Logger.
 * All log output is automatically scrubbed of PAN data.
 * Writes to App-Specific Storage only.
 */
public class FileLogger {

    private static final String TAG = "FileLogger";
    private static final String DIR_NAME = "iso_logs";

    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US);
    private static final DateTimeFormatter FMT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.US);

    // PA-DSS 1.1.4: Log packet with PAN masking
    public static void logPacket(Context context, String type, byte[] data) {
        if (context == null || data == null)
            return;
        // PA-DSS 2.2: Mask any PAN in hex data before logging
        String hex = PanMasker.maskHex(StandardIsoPacker.bytesToHex(data));
        logString(context, type, hex);
    }

    public static void logTestSuitePacket(Context context, String type, byte[] data) {
        if (context == null || data == null)
            return;
        String hex = PanMasker.maskHex(StandardIsoPacker.bytesToHex(data));
        logWithPrefix(context, "test_suite_log_", type, hex);
    }

    public static void logTestSuiteString(Context context, String type, String content) {
        logWithPrefix(context, "test_suite_log_", type, content);
    }

    public static void logString(Context context, String type, String content) {
        logWithPrefix(context, "iso_log_", type, content);
    }

    private static void logWithPrefix(Context context, String prefix, String type, String content) {
        if (context == null)
            return;

        try {
            File dir = new File(context.getExternalFilesDir(null), DIR_NAME);
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "Failed to create log directory");
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            String dateStr = FMT_DATE.format(now);
            File file = new File(dir, prefix + dateStr + ".txt");

            String timestamp = FMT_TIME.format(now);

            // PA-DSS 1.1.4: NEVER log full PAN — mask before writing
            String safeContent = PanMasker.mask(content);

            String logEntry = String.format(Locale.US, "[%s] [%s] %s\n",
                    timestamp, type, safeContent);

            try (FileOutputStream fos = new FileOutputStream(file, true);
                    OutputStreamWriter writer = new OutputStreamWriter(fos)) {
                writer.write(logEntry);
            }

        } catch (IOException e) {
            Log.e(TAG, "Failed to write log", e);
        }
    }
}
