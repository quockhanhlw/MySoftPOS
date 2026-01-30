package com.example.mysoftpos.testsuite.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.testsuite.TestDataProvider;
import com.example.mysoftpos.testsuite.data.TestSuiteDao;
import com.example.mysoftpos.testsuite.model.TestCase;
import com.example.mysoftpos.testsuite.model.TestScenario;
import com.example.mysoftpos.utils.AppExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Senior Refactored ViewModel for Test Suite.
 * Implements "Check-Wait-Act" pattern for thread-safe initialization.
 */
public class TestSuiteViewModel extends AndroidViewModel {

    private final TestSuiteDao dao;
    private final MutableLiveData<List<TestScenario>> testScenarios = new MutableLiveData<>();

    public TestSuiteViewModel(@NonNull Application application) {
        super(application);
        dao = AppDatabase.getInstance(application).testSuiteDao();
    }

    public LiveData<List<TestScenario>> getTestScenarios() {
        return testScenarios;
    }

    /**
     * Senior Implementation of initData with Check-Wait-Act.
     */
    public void initData(String channel, String mti, String procCode) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            // STEP A: Check
            if (dao.getTestCaseCount() == 0) {
                // STEP B: Act
                prepopulateFromScenarios();
            }

            // STEP C: Notify (with filtering)
            loadAndPost(channel, mti, procCode);
        });
    }

    private void prepopulateFromScenarios() {
        List<TestScenario> scenarios = TestDataProvider.generateAllScenarios();
        for (TestScenario s : scenarios) {
            String id = UUID.randomUUID().toString();
            s.setId(id); // Assign ID to scenario if needed
            TestCase tc = new TestCase(
                    id,
                    s.getDescription(),
                    "Auto-generated from Scenario",
                    s.getMti(),
                    "000000",
                    "051",
                    "POS",
                    true,
                    false);
            dao.insertTestCase(tc);
        }
    }

    private void loadAndPost(String channel, String mti, String procCode) {
        List<TestCase> cases;
        if (channel != null) {
            cases = dao.getTestCasesFiltered(channel, mti, procCode);
        } else {
            cases = dao.getAllTestCases();
        }

        List<TestScenario> scenarios = new ArrayList<>();
        for (TestCase tc : cases) {
            TestScenario ts = new TestScenario(tc.getMti(), tc.getName());
            ts.setId(tc.getId()); // Crucial: Link TestScenario to TestCase ID
            ts.setField(3, tc.getProcessingCode());
            ts.setField(22, tc.getPosEntryMode());
            scenarios.add(ts);
        }
        testScenarios.postValue(scenarios);
    }
}
