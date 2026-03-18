package com.example.mysoftpos.testsuite;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.entity.TestSuiteEntity;
import com.example.mysoftpos.testsuite.adapter.TestSuiteAdapter;
import com.example.mysoftpos.testsuite.viewmodel.DynamicTestSuiteViewModel;
import com.example.mysoftpos.ui.BaseActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class DynamicTestSuiteActivity extends BaseActivity {

    private DynamicTestSuiteViewModel viewModel;
    private TestSuiteAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dynamic_test_suite);

        RecyclerView recyclerView = findViewById(R.id.recyclerViewSuites);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new TestSuiteAdapter(this::openSuite, this::showOptionsDialog);
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(DynamicTestSuiteViewModel.class);
        viewModel.getAllSuites().observe(this, adapter::setSuites);

        FloatingActionButton fab = findViewById(R.id.fabAddSuite);
        fab.setOnClickListener(v -> showAddSuiteDialog());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void openSuite(TestSuiteEntity suite) {
        Intent intent = new Intent(this, DynamicTestCaseActivity.class);
        intent.putExtra("SUITE_ID", suite.id);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.SUITE_NAME, suite.name);
        startActivity(intent);
    }

    private void showOptionsDialog(TestSuiteEntity suite) {
        CharSequence[] options = {
                getString(R.string.dynamic_suite_option_rename),
                getString(R.string.dynamic_suite_option_delete)
        };
        new AlertDialog.Builder(this)
                .setTitle(suite.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showEditSuiteDialog(suite);
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.dynamic_suite_delete_title)
                                .setMessage(R.string.dynamic_suite_delete_message)
                                .setPositiveButton(R.string.dynamic_suite_option_delete, (d, w) -> viewModel.deleteSuite(suite))
                                .setNegativeButton(R.string.common_cancel, null)
                                .show();
                    }
                })
                .show();
    }

    private void showEditSuiteDialog(TestSuiteEntity suite) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dynamic_suite_edit_title);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_suite, null);
        final EditText etName = view.findViewById(R.id.etSuiteName);
        final EditText etDesc = view.findViewById(R.id.etSuiteDesc);

        etName.setText(suite.name);
        etDesc.setText(suite.description);

        builder.setView(view);

        builder.setPositiveButton(R.string.dynamic_suite_action_update, (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String desc = etDesc.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, R.string.dynamic_suite_name_required, Toast.LENGTH_SHORT).show();
                return;
            }
            suite.name = name;
            suite.description = desc;
            viewModel.updateSuite(suite);
        });
        builder.setNegativeButton(R.string.common_cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showAddSuiteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dynamic_suite_new_title);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_suite, null);
        final EditText etName = view.findViewById(R.id.etSuiteName);
        final EditText etDesc = view.findViewById(R.id.etSuiteDesc);

        builder.setView(view);

        builder.setPositiveButton(R.string.dynamic_suite_action_create, (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String desc = etDesc.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, R.string.dynamic_suite_name_required, Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.createSuite(name, desc);
        });
        builder.setNegativeButton(R.string.common_cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }
}
