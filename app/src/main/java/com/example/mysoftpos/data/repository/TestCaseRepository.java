package com.example.mysoftpos.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.dao.TestCaseDao;
import com.example.mysoftpos.data.local.entity.TestCaseEntity;
import com.example.mysoftpos.testsuite.storage.TestStorageProvider;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TestCaseRepository {

    private final TestCaseDao dao;
    private final TestStorageProvider storage;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public TestCaseRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.dao = db.testCaseDao();
        this.storage = new TestStorageProvider(context);
    }

    public LiveData<List<TestCaseEntity>> getCasesBySuite(long suiteId) {
        return dao.getCasesBySuite(suiteId);
    }

    public void saveValidationResult(long suiteId, String name, String type,
            byte[] request, byte[] response, boolean passed) {
        executor.execute(() -> {
            try {
                String status = passed ? "PASS" : "FAIL";
                String reqPath = storage.saveIsoFile("req", suiteId, name, request);
                String resPath = storage.saveIsoFile("res", suiteId, name, response);

                TestCaseEntity entity = new TestCaseEntity();
                entity.suiteId = suiteId;
                entity.name = name;
                entity.transactionType = type;
                entity.status = status;
                entity.requestFilePath = reqPath;
                entity.responseFilePath = resPath;
                entity.timestamp = System.currentTimeMillis();

                dao.insert(entity);

            } catch (Exception e) {
                android.util.Log.e("TestCaseRepo", "Save result", e);
            }
        });
    }

    public void insert(TestCaseEntity entity) {
        executor.execute(() -> dao.insert(entity));
    }

    public void update(TestCaseEntity entity) {
        executor.execute(() -> dao.update(entity));
    }

    public void delete(TestCaseEntity entity) {
        executor.execute(() -> dao.delete(entity));
    }

    public LiveData<List<TestCaseEntity>> getCustomCasesByType(String type) {
        return dao.getCustomCasesByType(type);
    }
}
