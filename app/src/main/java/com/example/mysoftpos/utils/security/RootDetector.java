package com.example.mysoftpos.utils.security;

import java.io.File;

/**
 * PA-DSS 10.x: Root and tamper detection.
 * Refuse to run on rooted/compromised devices.
 */
public final class RootDetector {

    private RootDetector() {
    }

    private static final String[] ROOT_PATHS = {
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/su/bin/su",
            "/data/adb/magisk",
            "/sbin/magisk"
    };

    /**
     * Check if device is rooted.
     * 
     * @return true if root indicators found
     */
    public static boolean isRooted() {
        return checkRootBinaries() || checkBuildTags() || checkSuCommand();
    }

    // PA-DSS 10.x: Check for su binaries on filesystem
    private static boolean checkRootBinaries() {
        for (String path : ROOT_PATHS) {
            if (new File(path).exists())
                return true;
        }
        return false;
    }

    // PA-DSS 10.x: Check build tags for test-keys
    private static boolean checkBuildTags() {
        String tags = android.os.Build.TAGS;
        return tags != null && tags.contains("test-keys");
    }

    // PA-DSS 10.x: Try to locate su binary via PATH
    private static boolean checkSuCommand() {
        Process proc = null;
        try {
            proc = Runtime.getRuntime().exec(new String[] { "/system/xbin/which", "su" });
            return proc.waitFor() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            // Prevent process leak
            if (proc != null)
                proc.destroy();
        }
    }

    /**
     * Check if running on an emulator (debug/test environment).
     */
    public static boolean isEmulator() {
        return android.os.Build.FINGERPRINT.contains("generic")
                || android.os.Build.FINGERPRINT.contains("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || android.os.Build.BRAND.startsWith("generic")
                || android.os.Build.DEVICE.startsWith("generic")
                || "google_sdk".equals(android.os.Build.PRODUCT);
    }
}
