package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mysoftpos.R;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * Screen 2: Channel Selection (POS / ATM).
 */
public class ChannelSelectActivity extends AppCompatActivity {

    private String scheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel_select);

        scheme = getIntent().getStringExtra("SCHEME");
        
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(scheme + " - Select Channel");
        toolbar.setNavigationOnClickListener(v -> finish());

        // POS
        findViewById(R.id.cardPos).setOnClickListener(v -> {
            navigateToTransactionSelect("POS");
        });

        // ATM
        findViewById(R.id.cardAtm).setOnClickListener(v -> {
            navigateToTransactionSelect("ATM");
        });
    }

    private void navigateToTransactionSelect(String channel) {
        Intent intent = new Intent(this, TransactionSelectActivity.class);
        intent.putExtra("SCHEME", scheme);
        intent.putExtra("CHANNEL", channel);
        startActivity(intent);
    }
}
