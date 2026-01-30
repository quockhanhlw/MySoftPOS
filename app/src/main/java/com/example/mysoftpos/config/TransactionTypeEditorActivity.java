package com.example.mysoftpos.config;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.config.adapter.FieldConfigAdapter;
import com.example.mysoftpos.data.local.entity.FieldConfiguration;
import com.example.mysoftpos.data.local.entity.TransactionType;
import com.example.mysoftpos.data.repository.ConfigurationRepository;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class TransactionTypeEditorActivity extends AppCompatActivity {

    private long typeId = -1;
    private TransactionType currentType;
    private List<FieldConfiguration> currentFields = new ArrayList<>();

    private TextInputEditText etName, etMti, etProcessingCode;
    private RecyclerView recyclerView;
    private FieldConfigAdapter adapter;
    private ConfigurationRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_type_editor);

        repository = new ConfigurationRepository(this);
        typeId = getIntent().getLongExtra("TYPE_ID", -1);

        initViews();

        if (typeId != -1) {
            loadData();
        } else {
            // New Entry - Initialize default field set (Optional: could pre-populate)
            // For now start empty
        }
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());

        etName = findViewById(R.id.etName);
        etMti = findViewById(R.id.etMti);
        etProcessingCode = findViewById(R.id.etProcessingCode);

        recyclerView = findViewById(R.id.recyclerViewFields);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FieldConfigAdapter();
        adapter.setOnItemClickListener(this::showFieldEditor);
        recyclerView.setAdapter(adapter);
    }

    private void loadData() {
        repository.getAllTransactionTypes(new ConfigurationRepository.DataCallback<List<TransactionType>>() {
            @Override
            public void onSuccess(List<TransactionType> data) {
                // Determine which one is ours (Not ideal, but Dao lacks getById in this
                // simplified request)
                // Wait, I added getTransactionTypeById in Dao.
                // But Repository exposes getAllTransactionTypes.
                // I should have added getById to repo.
                // For now, filter client side or update repo.
                // Let's rely on repo update in future, for now iterate.
                for (TransactionType t : data) {
                    if (t.id == typeId) {
                        currentType = t;
                        break;
                    }
                }

                if (currentType != null) {
                    runOnUiThread(() -> {
                        etName.setText(currentType.name);
                        etMti.setText(currentType.mti);
                        etProcessingCode.setText(currentType.processingCode);
                        loadFields();
                    });
                }
            }

            @Override
            public void onError(String error) {
            }
        });
    }

    private void loadFields() {
        repository.getFieldConfigsForType(typeId, new ConfigurationRepository.DataCallback<List<FieldConfiguration>>() {
            @Override
            public void onSuccess(List<FieldConfiguration> data) {
                currentFields = data;

                // Ensure common fields exist if list is empty?
                // Or let user add them?
                // For simplicity, let's just show what's in DB.
                // But we need a way to ADD fields.
                // The current layout doesn't have an "Add Field" button.
                // Wait, fields are typically fixed per standard (0-128).
                // We should probably pre-populate the list with 0-128 and let user edit.
                // Or just show configured ones.
                // Let's add a "+" button to the layout via code or edit XML.
                // Actually, let's just show the ones configured for now,
                // and maybe auto-generate a template if empty.

                if (currentFields.isEmpty()) {
                    // Populate default template
                    populateDefaultFields();
                }

                runOnUiThread(() -> adapter.setData(currentFields));
            }

            @Override
            public void onError(String error) {
            }
        });
    }

    private void populateDefaultFields() {
        // Initial set for a standard transaction
        currentFields.add(new FieldConfiguration(typeId, 3, "INPUT", null));
        currentFields.add(new FieldConfiguration(typeId, 4, "INPUT", null));
        currentFields.add(new FieldConfiguration(typeId, 11, "AUTO", null));
        currentFields.add(new FieldConfiguration(typeId, 22, "AUTO", null));
        currentFields.add(new FieldConfiguration(typeId, 41, "CONFIG", null));
        currentFields.add(new FieldConfiguration(typeId, 42, "CONFIG", null));
    }

    private void save() {
        String name = etName.getText().toString();
        String mti = etMti.getText().toString();
        String proc = etProcessingCode.getText().toString();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(mti)) {
            Toast.makeText(this, "Name and MTI are required", Toast.LENGTH_SHORT).show();
            return;
        }

        TransactionType type = new TransactionType(name, mti, proc);
        if (typeId != -1)
            type.id = typeId;

        repository.insertTransactionType(type, new ConfigurationRepository.DataCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                // Now save fields
                long finalTypeId = (typeId == -1) ? id : typeId;

                // Update transactionTypeId for all fields
                for (FieldConfiguration fc : currentFields) {
                    fc.transactionTypeId = finalTypeId;
                }

                repository.saveFieldConfigs(currentFields, new ConfigurationRepository.DataCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean data) {
                        runOnUiThread(() -> {
                            Toast.makeText(TransactionTypeEditorActivity.this, "Saved!", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }

                    @Override
                    public void onError(String error) {
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(
                        () -> Toast.makeText(TransactionTypeEditorActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showFieldEditor(FieldConfiguration config) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_edit_field);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView tvTitle = dialog.findViewById(R.id.tvTitle);
        Spinner spinnerSource = dialog.findViewById(R.id.spinnerSource);
        TextInputEditText etValue = dialog.findViewById(R.id.etValue);
        View btnSaveDialog = dialog.findViewById(R.id.btnSave);

        tvTitle.setText("Edit Field " + config.fieldId);
        etValue.setText(config.value);

        String[] sources = getResources().getStringArray(R.array.field_sources);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sources);
        spinnerSource.setAdapter(adapter);

        if (config.sourceType != null) {
            for (int i = 0; i < sources.length; i++) {
                if (sources[i].equals(config.sourceType)) {
                    spinnerSource.setSelection(i);
                    break;
                }
            }
        }

        btnSaveDialog.setOnClickListener(v -> {
            String selectedSource = spinnerSource.getSelectedItem().toString();
            String value = etValue.getText().toString();

            config.sourceType = selectedSource;
            config.value = value;

            this.adapter.notifyDataSetChanged();
            dialog.dismiss();
        });

        dialog.show();
    }
}
