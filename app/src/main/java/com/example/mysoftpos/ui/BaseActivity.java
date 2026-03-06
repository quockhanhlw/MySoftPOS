package com.example.mysoftpos.ui;

import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mysoftpos.utils.LocaleHelper;
import com.example.mysoftpos.utils.NetworkMonitor;
import com.example.mysoftpos.utils.security.RootDetector;
import com.example.mysoftpos.utils.security.SessionManager;

/**
 * PA-DSS compliant base activity.
 * Enforces: FLAG_SECURE, root detection, session timeout, locale handling, and
 * global network monitoring.
 */
public class BaseActivity extends AppCompatActivity {

    private NetworkMonitor networkMonitor;
    private android.widget.Toast networkToast;

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

        // Initialize Global Network Monitor
        networkMonitor = new NetworkMonitor(this, new NetworkMonitor.NetworkCallbackListener() {
            @Override
            public void onNetworkAvailable() {
                showNetworkToast("Đã khôi phục kết nối mạng");
            }

            @Override
            public void onNetworkLost() {
                showNetworkToast("Mất kết nối mạng. Ứng dụng đang ở chế độ offline.");
            }
        });
    }

    private void showNetworkToast(String message) {
        if (networkToast != null) {
            networkToast.cancel();
        }
        networkToast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG);
        networkToast.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (networkMonitor != null) {
            networkMonitor.register();
        }
        // PA-DSS 3.x: Check session timeout only when a session is active
        // (skips auth screens like Login, Register, Welcome where no session exists)
        if (SessionManager.isActive()) {
            SessionManager.checkAndEnforceTimeout(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (networkMonitor != null) {
            networkMonitor.unregister();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        // PA-DSS 3.x: Reset inactivity timer on any user interaction
        SessionManager.onUserInteraction();
    }
}
