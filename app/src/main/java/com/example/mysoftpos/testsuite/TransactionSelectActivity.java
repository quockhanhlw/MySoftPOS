package com.example.mysoftpos.testsuite;

import com.example.mysoftpos.iso8583.TxnType;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mysoftpos.R;

public class TransactionSelectActivity extends AppCompatActivity {

    private String channel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_select); // Will create layout next

        channel = getIntent().getStringExtra("CHANNEL");
        if (channel == null)
            channel = "POS";

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(channel + " - Select Transaction");

        View btnPurchase = findViewById(R.id.btnPurchase);
        View btnBalance = findViewById(R.id.btnBalance);

        btnPurchase.setOnClickListener(v -> navigateToTestSuite("PURCHASE"));
        btnBalance.setOnClickListener(v -> navigateToTestSuite("BALANCE"));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void navigateToTestSuite(String txnType) {
        Intent intent = new Intent(this, TestSuiteActivity.class);
        intent.putExtra("CHANNEL", channel);
        intent.putExtra("TXN_TYPE", txnType);
        startActivity(intent);
    }
}
