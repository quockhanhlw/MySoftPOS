package com.example.mysoftpos.ui;

import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mysoftpos.utils.LocaleHelper;
import com.example.mysoftpos.utils.security.RootDetector;
import com.example.mysoftpos.utils.security.SessionManager;

/**
 * PA-DSS compliant base activity.
 * Enforces: FLAG_SECURE, root detection, session timeout, locale handling.
 */
public class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // PA-DSS 10.x: Prevent screenshots and screen recording on all screens
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        // PA-DSS 10.x: Refuse to run on rooted devices (skip for emulator during dev)
        if (RootDetector.isRooted() && !RootDetector.isEmulator()) {
            com.example.mysoftpos.utils.security.AuditLogger.logSystem(
                    this, "ROOT_DETECTED", false, "BaseActivity",
                    "App blocked on rooted device");
            Toast.makeText(this,
                    "This app cannot run on rooted devices for security reasons.",
                    Toast.LENGTH_LONG).show();
            finishAffinity();
            return;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // PA-DSS 3.x: Check session timeout only when a session is active
        // (skips auth screens like Login, Register, Welcome where no session exists)
        if (SessionManager.isActive()) {
            SessionManager.checkAndEnforceTimeout(this);
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        // PA-DSS 3.x: Reset inactivity timer on any user interaction
        SessionManager.onUserInteraction();
    }
}
