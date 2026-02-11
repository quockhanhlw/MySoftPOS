package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mysoftpos.R;

public class PerformanceSelectActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_performance_select);

        findViewById(R.id.btnSingle).setOnClickListener(v -> navigate("SINGLE"));
        findViewById(R.id.btnMulti).setOnClickListener(v -> navigate("MULTI"));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void navigate(String perfMode) {
        Intent intent = new Intent(this, TransactionSelectActivity.class);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.CHANNEL,
                getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.CHANNEL));
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.SCHEME,
                getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.SCHEME));
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.PERF_MODE, perfMode);
        startActivity(intent);
    }
}
