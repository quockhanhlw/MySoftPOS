package com.example.mysoftpos.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.TransactionTemplateDao;
import com.example.mysoftpos.data.local.TransactionTemplateEntity;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TemplateRepository {

    private final TransactionTemplateDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public TemplateRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.dao = db.transactionTemplateDao();
    }

    public LiveData<List<TransactionTemplateEntity>> getAllTemplates() {
        return dao.getAllTemplates();
    }

    public void insert(TransactionTemplateEntity template) {
        executor.execute(() -> dao.insert(template));
    }

    // Export template to JSON file
    public void exportTemplate(TransactionTemplateEntity template, File destFile) throws IOException {
        try {
            JSONObject json = new JSONObject();
            json.put("name", template.name);
            json.put("mti", template.mti);
            json.put("field_config_json", template.fieldConfigJson);
            // createdAt is redundant for export

            try (FileWriter writer = new FileWriter(destFile)) {
                writer.write(json.toString());
            }
        } catch (Exception e) {
            throw new IOException("Failed to serialize template", e);
        }
    }

    // Import template from JSON file
    public void importTemplate(File srcFile) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Scanner scanner = new Scanner(srcFile, StandardCharsets.UTF_8.name())) {
            while (scanner.hasNextLine()) {
                sb.append(scanner.nextLine());
            }
        }

        try {
            JSONObject json = new JSONObject(sb.toString());
            TransactionTemplateEntity template = new TransactionTemplateEntity();
            template.name = json.optString("name");
            template.mti = json.optString("mti");
            template.fieldConfigJson = json.optString("field_config_json");
            template.createdAt = System.currentTimeMillis();

            insert(template);
        } catch (Exception e) {
            throw new IOException("Failed to parse template", e);
        }
    }
}
