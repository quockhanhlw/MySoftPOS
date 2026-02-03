package com.example.mysoftpos.utils.logging;
import com.example.mysoftpos.utils.logging.FileLogger;
import com.example.mysoftpos.iso8583.util.StandardIsoPacker;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Validated Logger for ISO Packets.
 * Writes to App-Specific Storage:
 * /Android/data/com.example.mysoftpos/files/iso_logs/
 */
public class FileLogger {

    private static final String TAG = "FileLogger";
    private static final String DIR_NAME = "iso_logs";

    public static void logPacket(Context context, String type, byte[] data) {
        if (context == null || data == null)
            return;

        String hex = StandardIsoPacker.bytesToHex(data);
        logString(context, type, hex);
    }

    public static void logTestSuitePacket(Context context, String type, byte[] data) {
        if (context == null || data == null)
            return;
        String hex = StandardIsoPacker.bytesToHex(data);
        logWithPrefix(context, "test_suite_log_", type, hex);
    }

    public static void logTestSuiteString(Context context, String type, String content) {
        logWithPrefix(context, "test_suite_log_", type, content);
    }

    // Main logging method now delegates to logWithPrefix using default prefix
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

            // Daily Log File with Prefix
            String dateStr = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
            File file = new File(dir, prefix + dateStr + ".txt");

            String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
            String logEntry = String.format(Locale.US, "[%s] [%s] %s\n", timestamp, type, content);

            try (FileOutputStream fos = new FileOutputStream(file, true);
                    OutputStreamWriter writer = new OutputStreamWriter(fos)) {
                writer.write(logEntry);
            }

            Log.d(TAG, "Logged to " + file.getAbsolutePath());

        } catch (IOException e) {
            Log.e(TAG, "Failed to write log", e);
        }
    }
}







