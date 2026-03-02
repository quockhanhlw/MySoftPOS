package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mysoftpos.R;
import com.example.mysoftpos.utils.IntentKeys;

public class ChannelSelectActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel_select);

        String scheme = getIntent().getStringExtra(IntentKeys.SCHEME);

        // Breadcrumb
        android.widget.TextView tvBreadcrumbScheme = findViewById(R.id.tvBreadcrumbScheme);
        if (tvBreadcrumbScheme != null && scheme != null)
            tvBreadcrumbScheme.setText(scheme);

        // Swipe back
        com.example.mysoftpos.testsuite.util.SwipeBackHelper.attach(this);
        com.example.mysoftpos.testsuite.util.StepDotsHelper.setActiveStep(this, 2);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnPos).setOnClickListener(v -> {
            Intent i = new Intent(this, TransactionSelectActivity.class);
            i.putExtra(IntentKeys.SCHEME, scheme);
            i.putExtra(IntentKeys.CHANNEL, "POS");
            startActivity(i);
        });

        findViewById(R.id.btnAtm).setOnClickListener(v -> {
            Intent i = new Intent(this, TransactionSelectActivity.class);
            i.putExtra(IntentKeys.SCHEME, scheme);
            i.putExtra(IntentKeys.CHANNEL, "ATM");
            startActivity(i);
        });

        // Transaction History for this scheme
        findViewById(R.id.btnHistory).setOnClickListener(v -> {
            Intent i = new Intent(this, SchemeHistoryActivity.class);
            i.putExtra(IntentKeys.SCHEME, scheme);
            startActivity(i);
        });
    }
}
