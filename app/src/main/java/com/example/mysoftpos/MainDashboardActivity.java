package com.example.mysoftpos;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class MainDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_dashboard);

        // Initialize views
        CardView cardPurchase = findViewById(R.id.cardPurchase);
        CardView cardVoid = findViewById(R.id.cardVoid);
        CardView cardRefund = findViewById(R.id.cardRefund);
        CardView cardHistory = findViewById(R.id.cardHistory);
        CardView cardLogon = findViewById(R.id.cardLogon);
        CardView cardSettings = findViewById(R.id.cardSettings);

        // Set click listeners
        cardPurchase.setOnClickListener(v -> {
            Intent intent = new Intent(MainDashboardActivity.this, PurchaseAmountActivity.class);
            startActivity(intent);
        });

        cardVoid.setOnClickListener(v -> {
            // TODO: Start Void/Reversal flow (RRN/STAN original data)
            android.widget.Toast.makeText(this, "Hủy/Đảo - Coming soon", android.widget.Toast.LENGTH_SHORT).show();
        });

        cardRefund.setOnClickListener(v -> {
            android.widget.Toast.makeText(this, "Hoàn tiền - Coming soon", android.widget.Toast.LENGTH_SHORT).show();
        });

        cardHistory.setOnClickListener(v -> {
            android.widget.Toast.makeText(this, "Lịch sử - Coming soon", android.widget.Toast.LENGTH_SHORT).show();
        });

        cardLogon.setOnClickListener(v -> {
            android.widget.Toast.makeText(this, "Logon - Coming soon", android.widget.Toast.LENGTH_SHORT).show();
        });

        cardSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainDashboardActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // no-op: header removed
    }

    // remove checkConnectionStatus/updateConnectionStatus
}
