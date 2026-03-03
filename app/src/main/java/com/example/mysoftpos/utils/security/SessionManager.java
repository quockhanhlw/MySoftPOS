package com.example.mysoftpos.utils.security;

import android.app.Activity;
import android.content.Intent;

import com.example.mysoftpos.ui.auth.LoginActivity;

/**
 * PA-DSS 3.x: Session timeout management.
 * Auto-logout after 15 minutes of inactivity.
 * Thread-safe via volatile fields.
 */
public final class SessionManager {

    private SessionManager() {
    }

    // PA-DSS 3.1: Session timeout = 15 minutes
    private static final long SESSION_TIMEOUT_MS = 15 * 60 * 1000L;

    // Volatile for thread visibility (UI thread + background threads)
    private static volatile long lastInteractionTime = System.currentTimeMillis();
    private static volatile boolean sessionActive = false;

    /** Call on every user interaction to reset the timer. */
    public static void onUserInteraction() {
        lastInteractionTime = System.currentTimeMillis();
    }

    /** Mark session as active after login. */
    public static void startSession() {
        sessionActive = true;
        lastInteractionTime = System.currentTimeMillis();
    }

    /** End session (logout). */
    public static void endSession() {
        sessionActive = false;
    }

    /** Check if session is currently active. */
    public static boolean isActive() {
        return sessionActive;
    }

    /** Check if session has timed out. */
    public static boolean isSessionTimedOut() {
        if (!sessionActive)
            return false;
        return (System.currentTimeMillis() - lastInteractionTime) > SESSION_TIMEOUT_MS;
    }

    /**
     * PA-DSS 3.1: Check and enforce session timeout.
     * If timed out, redirect to login and clear back stack.
     * 
     * @return true if session was timed out and user redirected
     */
    public static boolean checkAndEnforceTimeout(Activity activity) {
        if (!sessionActive)
            return false;
        if (!isSessionTimedOut())
            return false;

        endSession();

        // PA-DSS 4.x: Log the timeout event
        AuditLogger.log(activity, "SESSION", "TIMEOUT",
                true, "SessionManager", "Session timed out after 15 min inactivity");

        Intent intent = new Intent(activity, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("SESSION_TIMEOUT", true);
        activity.startActivity(intent);
        activity.finish();
        return true;
    }
}
