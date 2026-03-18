package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.entity.TestCaseEntity;
import com.example.mysoftpos.testsuite.adapter.TestCaseAdapter;
import com.example.mysoftpos.testsuite.viewmodel.DynamicTestCaseViewModel;
import com.example.mysoftpos.ui.BaseActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class DynamicTestCaseActivity extends BaseActivity {

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
        CharSequence[] options = {
                getString(R.string.dynamic_case_option_run),
                getString(R.string.dynamic_case_option_edit),
                getString(R.string.dynamic_case_option_delete)
        };
        new android.app.AlertDialog.Builder(this)
                .setTitle(testCase.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        runCase(testCase);
                    } else if (which == 1) {
                        showEditCaseDialog(testCase);
                    } else {
                        new android.app.AlertDialog.Builder(this)
                                .setTitle(R.string.dynamic_case_delete_title)
                                .setMessage(R.string.dynamic_case_delete_message)
                                .setPositiveButton(R.string.dynamic_case_option_delete, (d, w) -> viewModel.deleteCase(testCase))
                                .setNegativeButton(R.string.common_cancel, null)
                                .show();
                    }
                })
                .show();
    }

    private void runCase(TestCaseEntity testCase) {
        com.example.mysoftpos.domain.service.TransactionExecutor executor = com.example.mysoftpos.di.ServiceLocator
                .getInstance(this).getTransactionExecutor();
        viewModel.runCase(this, testCase, executor);
        android.widget.Toast.makeText(this,
                        getString(R.string.dynamic_case_running, testCase.name),
                        android.widget.Toast.LENGTH_SHORT)
                .show();
    }

    private void showAddCaseDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.dynamic_case_new_title);

        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_add_suite, null);
        final android.widget.EditText etName = view.findViewById(R.id.etSuiteName);
        final android.widget.EditText etDesc = view.findViewById(R.id.etSuiteDesc);
        etName.setHint(R.string.dynamic_case_name_hint);
        etDesc.setHint(R.string.dynamic_case_type_hint);

        builder.setView(view);

        builder.setPositiveButton(R.string.dynamic_case_action_create, (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String type = etDesc.getText().toString().trim().toUpperCase();
            if (name.isEmpty()) {
                android.widget.Toast.makeText(this, R.string.dynamic_case_name_required, android.widget.Toast.LENGTH_SHORT).show();
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
        builder.setNegativeButton(R.string.common_cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showEditCaseDialog(TestCaseEntity testCase) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.dynamic_case_edit_title);

        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_add_suite, null);
        final android.widget.EditText etName = view.findViewById(R.id.etSuiteName);
        final android.widget.EditText etDesc = view.findViewById(R.id.etSuiteDesc);
        etName.setHint(R.string.dynamic_case_name_hint);
        etDesc.setHint(R.string.dynamic_case_type_simple_hint);

        etName.setText(testCase.name);
        etDesc.setText(testCase.transactionType);

        builder.setView(view);

        builder.setPositiveButton(R.string.dynamic_case_action_update, (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String type = etDesc.getText().toString().trim().toUpperCase();
            if (name.isEmpty()) {
                android.widget.Toast.makeText(this, R.string.dynamic_case_name_required, android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            testCase.name = name;
            testCase.transactionType = type;
            viewModel.updateCase(testCase);
        });
        builder.setNegativeButton(R.string.common_cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }
}
