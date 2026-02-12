package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mysoftpos.R;

public class ChannelSelectActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel_select); // Will create layout next

        View btnPos = findViewById(R.id.btnPos);
        View btnAtm = findViewById(R.id.btnAtm);

        btnPos.setOnClickListener(v -> navigateToTransaction("POS"));
        btnAtm.setOnClickListener(v -> navigateToTransaction("ATM"));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void navigateToTransaction(String channel) {
        Intent intent = new Intent(this, PerformanceSelectActivity.class); // Fix: use PerformanceSelectActivity
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.CHANNEL, channel);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.SCHEME,
                getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.SCHEME));
        startActivity(intent);
    }
}
