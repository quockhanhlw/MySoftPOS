package com.example.mysoftpos;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mysoftpos.testsuite.SchemeSelectActivity;

/**
 * Main Dashboard.
 * Entry point for all features after login.
 */
public class MainDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_dashboard);

        // ═══════════════════════════════════════════════════════════════════
        //                         FEATURE NAVIGATION
        // ═══════════════════════════════════════════════════════════════════

        // Purchase Feature
        findViewById(R.id.cardPurchase).setOnClickListener(v -> {
            Intent intent = new Intent(this, PurchaseAmountActivity.class);
            startActivity(intent);
        });

        // Balance Inquiry Feature
        findViewById(R.id.cardBalance).setOnClickListener(v -> {
            Intent intent = new Intent(this, PurchaseCardActivity.class);
            intent.putExtra("TXN_TYPE", "BALANCE_INQUIRY");
            intent.putExtra("AMOUNT", "0");
            startActivity(intent);
        });

        // Settings Feature
        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });
        
        // ═══════════════════════════════════════════════════════════════════
        //                         TEST SUITE (NEW)
        // ═══════════════════════════════════════════════════════════════════
        
        // Test Suite - ISO 8583 Testing
        findViewById(R.id.cardTest).setOnClickListener(v -> {
            Intent intent = new Intent(this, SchemeSelectActivity.class);
            startActivity(intent);
        });

        // History Feature (Placeholder)
        findViewById(R.id.cardHistory).setOnClickListener(v -> {
            // TODO: Implement HistoryActivity
        });
    }
}
