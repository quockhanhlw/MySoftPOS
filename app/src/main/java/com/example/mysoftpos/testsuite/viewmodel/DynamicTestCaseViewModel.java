package com.example.mysoftpos.testsuite.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.dao.TestCaseDao;
import com.example.mysoftpos.data.local.entity.TestCaseEntity;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DynamicTestCaseViewModel extends AndroidViewModel {

    private final TestCaseDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private long suiteId = -1;

    public DynamicTestCaseViewModel(@NonNull Application application) {
        super(application);
        dao = AppDatabase.getInstance(application).testCaseDao();
    }

    public void setSuiteId(long id) {
        this.suiteId = id;
    }

    public LiveData<List<TestCaseEntity>> getCasesForSuite() {
        if (suiteId == -1) {
            throw new IllegalStateException("Suite ID not set");
        }
        return dao.getCasesBySuite(suiteId);
    }

    public void deleteCase(TestCaseEntity testCase) {
        executor.execute(() -> dao.delete(testCase));
    }

}





