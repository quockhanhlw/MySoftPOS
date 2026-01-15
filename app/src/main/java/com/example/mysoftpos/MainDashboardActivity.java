package com.example.mysoftpos;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class MainDashboardActivity extends AppCompatActivity {

    private TextView tvConnectionStatus;
    private View vStatusIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_dashboard);

        // Initialize views
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        vStatusIndicator = findViewById(R.id.vStatusIndicator);
        CardView cardPurchase = findViewById(R.id.cardPurchase);
        CardView cardVoid = findViewById(R.id.cardVoid);
        CardView cardRefund = findViewById(R.id.cardRefund);
        CardView cardHistory = findViewById(R.id.cardHistory);
        CardView cardLogon = findViewById(R.id.cardLogon);
        CardView cardSettings = findViewById(R.id.cardSettings);

        // Check connection status
        checkConnectionStatus();

        // Set click listeners
        cardPurchase.setOnClickListener(v -> {
            Toast.makeText(this, "Thanh toán - Coming soon", Toast.LENGTH_SHORT).show();
            // TODO: Start AmountEntryActivity
        });

        cardVoid.setOnClickListener(v -> {
            Toast.makeText(this, "Hủy giao dịch - Coming soon", Toast.LENGTH_SHORT).show();
            // TODO: Start VoidActivity
        });

        cardRefund.setOnClickListener(v -> {
            Toast.makeText(this, "Hoàn tiền - Coming soon", Toast.LENGTH_SHORT).show();
            // TODO: Start RefundActivity
        });

        cardHistory.setOnClickListener(v -> {
            Toast.makeText(this, "Lịch sử - Coming soon", Toast.LENGTH_SHORT).show();
            // TODO: Start HistoryLogActivity
        });

        cardLogon.setOnClickListener(v -> {
            Toast.makeText(this, "Logon - Coming soon", Toast.LENGTH_SHORT).show();
            // TODO: Perform Logon (0800)
        });

        cardSettings.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(MainDashboardActivity.this,
                    Class.forName("com.example.mysoftpos.SettingsActivity"));
                startActivity(intent);
            } catch (ClassNotFoundException e) {
                Toast.makeText(this, "Loi: Khong tim thay Settings", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkConnectionStatus();
    }

    private void checkConnectionStatus() {
        // Check if configuration exists
        SharedPreferences prefs = getSharedPreferences("SoftPOSConfig", MODE_PRIVATE);
        String ip = prefs.getString("server_ip", "");
        String port = prefs.getString("port", "");
        boolean configured = !ip.isEmpty() && !port.isEmpty();
        updateConnectionStatus(configured);
    }

    private void updateConnectionStatus(boolean connected) {

        if (connected) {
            tvConnectionStatus.setText(R.string.dashboard_status_online);
            vStatusIndicator.setBackgroundResource(R.drawable.status_indicator);
            vStatusIndicator.setBackgroundTintList(getColorStateList(R.color.status_online));
        } else {
            tvConnectionStatus.setText(R.string.dashboard_status_offline);
            vStatusIndicator.setBackgroundResource(R.drawable.status_indicator);
            vStatusIndicator.setBackgroundTintList(getColorStateList(R.color.status_offline));
        }
    }
}
