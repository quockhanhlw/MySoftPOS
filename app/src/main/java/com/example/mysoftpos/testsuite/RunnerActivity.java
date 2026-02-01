package com.example.mysoftpos.testsuite;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.mysoftpos.R;
import com.example.mysoftpos.testsuite.viewmodel.RunnerViewModel;

public class RunnerActivity extends AppCompatActivity {

    private RunnerViewModel viewModel;
    private TextView tvLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_runner); // Will create layout

        tvLog = findViewById(R.id.tvIsoLog);
        viewModel = new ViewModelProvider(this).get(RunnerViewModel.class);

        // Get Intent Data
        String de22 = getIntent().getStringExtra("DE_22");
        String desc = getIntent().getStringExtra("DESC");
        String channel = getIntent().getStringExtra("CHANNEL");

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(desc);

        findViewById(R.id.btnRun).setOnClickListener(v -> {
            runTransaction(de22);
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Observe Logs
        viewModel.getLogMessage().observe(this, log -> {
            tvLog.append(log + "\n");
        });
    }

    private void runTransaction(String de22) {
        tvLog.setText("Starting Transaction...\nMode: " + de22 + "\n");
        viewModel.runTransaction(de22);
    }
}
