package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.entity.TestCaseEntity;
import com.example.mysoftpos.testsuite.adapter.TestCaseAdapter;
import com.example.mysoftpos.testsuite.viewmodel.DynamicTestCaseViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class DynamicTestCaseActivity extends AppCompatActivity {

    private DynamicTestCaseViewModel viewModel;
    private TestCaseAdapter adapter;
    private long suiteId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dynamic_test_case);

        String suiteName = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.SUITE_NAME);
        suiteId = getIntent().getLongExtra("SUITE_ID", -1);

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(suiteName);

        RecyclerView recyclerView = findViewById(R.id.recyclerViewCases);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new TestCaseAdapter(this::openCaseDetails, this::showOptionsDialog);
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(DynamicTestCaseViewModel.class);
        viewModel.setSuiteId(suiteId);
        viewModel.getCasesForSuite().observe(this, adapter::setCases);

        FloatingActionButton fab = findViewById(R.id.fabAddCase);
        fab.setOnClickListener(v -> showAddCaseDialog());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void openCaseDetails(TestCaseEntity testCase) {
        // For now, details just show the edit dialog
        showEditCaseDialog(testCase);
    }

    private void showOptionsDialog(TestCaseEntity testCase) {
        CharSequence[] options = { "Run", "Edit", "Delete" };
        new android.app.AlertDialog.Builder(this)
                .setTitle(testCase.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        runCase(testCase);
                    } else if (which == 1) {
                        showEditCaseDialog(testCase);
                    } else {
                        new android.app.AlertDialog.Builder(this)
                                .setTitle("Delete Case")
                                .setMessage("Are you sure you want to delete this test case?")
                                .setPositiveButton("Delete", (d, w) -> viewModel.deleteCase(testCase))
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .show();
    }

    private void runCase(TestCaseEntity testCase) {
        com.example.mysoftpos.domain.service.TransactionExecutor executor = com.example.mysoftpos.di.ServiceLocator
                .getInstance(this).getTransactionExecutor();
        viewModel.runCase(this, testCase, executor);
        android.widget.Toast.makeText(this, "Running " + testCase.name + "...", android.widget.Toast.LENGTH_SHORT)
                .show();
    }

    private void showAddCaseDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("New Test Case");

        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_add_suite, null);
        final android.widget.EditText etName = view.findViewById(R.id.etSuiteName);
        final android.widget.EditText etDesc = view.findViewById(R.id.etSuiteDesc);
        etName.setHint("Case Name");
        etDesc.setHint("Transaction Type (PURCHASE/BALANCE)");

        builder.setView(view);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String type = etDesc.getText().toString().trim().toUpperCase();
            if (name.isEmpty()) {
                android.widget.Toast.makeText(this, "Name required", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            if (type.isEmpty())
                type = "PURCHASE";

            TestCaseEntity newCase = new TestCaseEntity();
            newCase.suiteId = suiteId;
            newCase.name = name;
            newCase.transactionType = type;
            newCase.status = "PENDING";
            viewModel.createCase(newCase);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showEditCaseDialog(TestCaseEntity testCase) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Edit Test Case");

        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_add_suite, null);
        final android.widget.EditText etName = view.findViewById(R.id.etSuiteName);
        final android.widget.EditText etDesc = view.findViewById(R.id.etSuiteDesc);
        etName.setHint("Case Name");
        etDesc.setHint("Transaction Type");

        etName.setText(testCase.name);
        etDesc.setText(testCase.transactionType);

        builder.setView(view);

        builder.setPositiveButton("Update", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String type = etDesc.getText().toString().trim().toUpperCase();
            if (name.isEmpty()) {
                android.widget.Toast.makeText(this, "Name required", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            testCase.name = name;
            testCase.transactionType = type;
            viewModel.updateCase(testCase);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }
}
