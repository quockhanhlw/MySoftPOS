package com.example.mysoftpos;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Main Dashboard.
 * Simplified to launch feature Activities.
 */
public class MainDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_dashboard);

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

        // Other features (Settings, History) would go here
    }
}
