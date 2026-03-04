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
        CharSequence[] options = { "Rename", "Delete" };
        new AlertDialog.Builder(this)
                .setTitle(suite.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showEditSuiteDialog(suite);
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("Delete Suite")
                                .setMessage("Are you sure you want to delete this suite and all its cases?")
                                .setPositiveButton("Delete", (d, w) -> viewModel.deleteSuite(suite))
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .show();
    }

    private void showEditSuiteDialog(TestSuiteEntity suite) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Test Suite");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_suite, null);
        final EditText etName = view.findViewById(R.id.etSuiteName);
        final EditText etDesc = view.findViewById(R.id.etSuiteDesc);

        etName.setText(suite.name);
        etDesc.setText(suite.description);

        builder.setView(view);

        builder.setPositiveButton("Update", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String desc = etDesc.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show();
                return;
            }
            suite.name = name;
            suite.description = desc;
            viewModel.updateSuite(suite);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showAddSuiteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("New Test Suite");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_suite, null);
        final EditText etName = view.findViewById(R.id.etSuiteName);
        final EditText etDesc = view.findViewById(R.id.etSuiteDesc);

        builder.setView(view);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String desc = etDesc.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.createSuite(name, desc);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }
}
