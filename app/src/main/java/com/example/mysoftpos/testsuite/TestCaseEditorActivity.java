package com.example.mysoftpos.testsuite;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.entity.TransactionType;
import com.example.mysoftpos.data.repository.ConfigurationRepository;
import com.example.mysoftpos.testsuite.model.TestCase;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.example.mysoftpos.utils.AppExecutors;

public class TestCaseEditorActivity extends AppCompatActivity {

    private TextInputEditText etName;
    private Spinner spinnerTxnType, spinnerChannel;
    private MaterialCheckBox cbRequiresAmount, cbRequiresPin;
    private ConfigurationRepository configRepo;
    private List<TransactionType> transactionTypes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_case_editor);

        configRepo = new ConfigurationRepository(this);

        initViews();
        loadTransactionTypes();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());

        etName = findViewById(R.id.etName);
        spinnerTxnType = findViewById(R.id.spinnerTxnType);
        spinnerChannel = findViewById(R.id.spinnerChannel);
        cbRequiresAmount = findViewById(R.id.cbRequiresAmount);
        cbRequiresPin = findViewById(R.id.cbRequiresPin);
    }

    private void loadTransactionTypes() {
        configRepo.getAllTransactionTypes(new ConfigurationRepository.DataCallback<List<TransactionType>>() {
            @Override
            public void onSuccess(List<TransactionType> data) {
                transactionTypes = data;
                List<String> names = new ArrayList<>();
                for (TransactionType t : data)
                    names.add(t.name);

                runOnUiThread(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(TestCaseEditorActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, names);
                    spinnerTxnType.setAdapter(adapter);
                });
            }

            @Override
            public void onError(String error) {
            }
        });
    }

    private void save() {
        String name = etName.getText().toString();
        if (name.isEmpty()) {
            Toast.makeText(this, "Name Required", Toast.LENGTH_SHORT).show();
            return;
        }

        int typeIndex = spinnerTxnType.getSelectedItemPosition();
        if (typeIndex < 0 || typeIndex >= transactionTypes.size()) {
            Toast.makeText(this, "Select Transaction Type", Toast.LENGTH_SHORT).show();
            return;
        }
        TransactionType selectedType = transactionTypes.get(typeIndex);

        String channel = spinnerChannel.getSelectedItem().toString(); // Need resources array
        boolean reqAmt = cbRequiresAmount.isChecked();
        boolean reqPin = cbRequiresPin.isChecked();

        AppExecutors.getInstance().diskIO().execute(() -> {
            com.example.mysoftpos.testsuite.model.TestCase tc = new com.example.mysoftpos.testsuite.model.TestCase(
                    UUID.randomUUID().toString(),
                    name,
                    "User Created",
                    selectedType.mti,
                    selectedType.processingCode,
                    "021",
                    channel,
                    reqAmt,
                    reqPin,
                    null);

            tc.setTransactionTypeId(selectedType.id);

            com.example.mysoftpos.data.local.AppDatabase.getInstance(this).testSuiteDao().insertTestCase(tc);

            runOnUiThread(() -> {
                Toast.makeText(this, "Saved: " + name, Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}
