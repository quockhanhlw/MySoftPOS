package com.example.mysoftpos.config;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mysoftpos.R;
import com.example.mysoftpos.testsuite.TestSuiteActivity;

public class ConfigDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config_dashboard);

        // Back Button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Transaction Types
        findViewById(R.id.cardTransactionTypes).setOnClickListener(v -> {
            Intent intent = new Intent(this, TransactionTypeListActivity.class);
            startActivity(intent);
        });

        // Test Suites
        findViewById(R.id.cardTestSuites).setOnClickListener(v -> {
            // Check if TestSuiteActivity exists or create logic for it
            Intent intent = new Intent(this, TestSuiteActivity.class);
            startActivity(intent);
        });
    }
}
