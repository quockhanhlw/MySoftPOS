package com.example.mysoftpos.config;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.config.adapter.TransactionTypeAdapter;
import com.example.mysoftpos.data.local.entity.TransactionType;
import com.example.mysoftpos.data.repository.ConfigurationRepository;

import java.util.List;

public class TransactionTypeListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TransactionTypeAdapter adapter;
    private ConfigurationRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_type_list);

        repository = new ConfigurationRepository(this);

        initViews();
        loadData();
    }

    private void initViews() {
        // Back
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Add
        findViewById(R.id.fabAdd).setOnClickListener(v -> {
            Intent intent = new Intent(this, TransactionTypeEditorActivity.class);
            startActivity(intent);
        });

        // List
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionTypeAdapter();
        adapter.setOnItemClickListener(type -> {
            Intent intent = new Intent(this, TransactionTypeEditorActivity.class);
            intent.putExtra("TYPE_ID", type.id);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);
    }

    private void loadData() {
        repository.getAllTransactionTypes(new ConfigurationRepository.DataCallback<List<TransactionType>>() {
            @Override
            public void onSuccess(List<TransactionType> data) {
                runOnUiThread(() -> adapter.setData(data));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(TransactionTypeListActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }
}
