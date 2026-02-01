package com.example.mysoftpos.testsuite.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.TestSuiteDao;
import com.example.mysoftpos.data.local.TestSuiteEntity;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DynamicTestSuiteViewModel extends AndroidViewModel {

    private final TestSuiteDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public DynamicTestSuiteViewModel(@NonNull Application application) {
        super(application);
        dao = AppDatabase.getInstance(application).testSuiteDao();
    }

    public LiveData<List<TestSuiteEntity>> getAllSuites() {
        return dao.getAllSuites();
    }

    public void createSuite(String name, String description) {
        executor.execute(() -> {
            TestSuiteEntity suite = new TestSuiteEntity();
            suite.name = name;
            suite.description = description;
            suite.createdAt = System.currentTimeMillis();
            dao.insert(suite);
        });
    }

    public void deleteSuite(TestSuiteEntity suite) {
        executor.execute(() -> dao.delete(suite));
    }
}
