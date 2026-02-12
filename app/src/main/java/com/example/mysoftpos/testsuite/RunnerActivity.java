package com.example.mysoftpos.testsuite;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.mysoftpos.R;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.testsuite.viewmodel.RunnerViewModel;

public class RunnerActivity extends AppCompatActivity {

    private RunnerViewModel viewModel;
    private TextView tvLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_runner);

        tvLog = findViewById(R.id.tvLog);
        viewModel = new ViewModelProvider(this).get(RunnerViewModel.class);

        // Get Intent Data
        android.content.Intent i = getIntent();
        String de22 = i.getStringExtra(com.example.mysoftpos.utils.IntentKeys.DE22);
        String desc = i.getStringExtra(com.example.mysoftpos.utils.IntentKeys.DESC);
        String txnType = i.getStringExtra(com.example.mysoftpos.utils.IntentKeys.TXN_TYPE);
        String pan = i.getStringExtra(com.example.mysoftpos.utils.IntentKeys.PAN);
        String expiry = i.getStringExtra(com.example.mysoftpos.utils.IntentKeys.EXPIRY);
        String pinBlock = i.getStringExtra(com.example.mysoftpos.utils.IntentKeys.PIN_BLOCK);
        String track2 = i.getStringExtra(com.example.mysoftpos.utils.IntentKeys.TRACK2);
        String amount = i.getStringExtra(com.example.mysoftpos.utils.IntentKeys.AMOUNT);
        String currencyCode = i.getStringExtra(com.example.mysoftpos.utils.IntentKeys.CURRENCY_CODE);
        String countryCode = i.getStringExtra(com.example.mysoftpos.utils.IntentKeys.COUNTRY_CODE);

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(desc);

        findViewById(R.id.btnRun).setOnClickListener(v -> {
            tvLog.setText("Starting Transaction...\nMode: " + de22 + "\nAmount: "
                    + (amount != null ? amount : "Default") + "\nCurrency: "
                    + (currencyCode != null ? currencyCode : "Default (704)") + "\n");
            viewModel.runTransaction(de22, track2, pan, expiry, pinBlock, txnType, amount, currencyCode, countryCode);
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Observe Preview (field breakdown on load)
        viewModel.getPreviewMessage().observe(this, preview -> {
            tvLog.setText(preview);
        });

        // Observe Run result (appended after preview)
        viewModel.getLogMessage().observe(this, log -> {
            tvLog.append(log + "\n");
        });

        // Build and display ISO message preview immediately
        viewModel.previewTransaction(de22, track2, pan, expiry, pinBlock, txnType, amount, currencyCode, countryCode);
    }
}
