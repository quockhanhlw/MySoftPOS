package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mysoftpos.R;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * Screen 3: Transaction Type Selection (Purchase / Balance).
 */
public class TransactionSelectActivity extends AppCompatActivity {

    private String scheme;
    private String channel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_select);

        scheme = getIntent().getStringExtra("SCHEME");
        channel = getIntent().getStringExtra("CHANNEL");
        
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(channel + " - Transaction Type");
        toolbar.setNavigationOnClickListener(v -> finish());

        // Purchase
        findViewById(R.id.cardPurchase).setOnClickListener(v -> {
            navigateToCardList("PURCHASE");
        });

        // Balance
        findViewById(R.id.cardBalance).setOnClickListener(v -> {
            navigateToCardList("BALANCE");
        });
    }

    private void navigateToCardList(String txnType) {
        // We reuse TestSuiteActivity as the Card List screen
        Intent intent = new Intent(this, TestSuiteActivity.class);
        intent.putExtra("SCHEME", scheme);
        intent.putExtra("CHANNEL", channel);
        intent.putExtra("TXN_TYPE", txnType);
        startActivity(intent);
    }
}
