package com.example.mysoftpos.data.remote;

import android.content.Context;
import android.util.Log;

import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.dao.TestCaseDao;
import com.example.mysoftpos.data.local.dao.TestSuiteDao;
import com.example.mysoftpos.data.local.entity.TestCaseEntity;
import com.example.mysoftpos.data.local.entity.TestSuiteEntity;
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Syncs TestSuites and TestCases between the local Room database
 * and the backend API.
 * <p>
 * pull() — downloads suites/cases from backend → local Room
 * push() — uploads local suites/cases → backend (bulk sync)
 */
public class TestSuiteSyncManager {

    private static final String TAG = "TestSuiteSyncMgr";
    private final Context context;

    public TestSuiteSyncManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Pull test suites and their cases from backend and upsert into local Room.
     */
    public void pull() {
        if (!ApiClient.isLoggedIn(context)) {
            Log.d(TAG, "Not logged in, skipping test suite sync");
            return;
        }

        String token = ApiClient.bearerToken(context);
        ApiClient.getService(context).getTestSuites(token)
                .enqueue(new Callback<List<ApiService.TestSuiteDto>>() {
                    @Override
                    public void onResponse(Call<List<ApiService.TestSuiteDto>> call,
                                           Response<List<ApiService.TestSuiteDto>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            new Thread(() -> {
                                try {
                                    pullSuitesWithCases(response.body(), token);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error pulling test suites: " + e.getMessage(), e);
                                }
                            }).start();
                        } else {
                            Log.w(TAG, "Failed to pull test suites: HTTP " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<List<ApiService.TestSuiteDto>> call, Throwable t) {
                        Log.w(TAG, "Test suite pull network error: " + t.getMessage());
                    }
                });
    }

    private void pullSuitesWithCases(List<ApiService.TestSuiteDto> suiteDtos, String token) {
        AppDatabase db = AppDatabase.getInstance(context);
        TestSuiteDao suiteDao = db.testSuiteDao();
        TestCaseDao caseDao = db.testCaseDao();

        for (ApiService.TestSuiteDto dto : suiteDtos) {
            // Upsert suite
            TestSuiteEntity suiteEntity = new TestSuiteEntity();
            suiteEntity.backendId = dto.id != null ? dto.id : 0;
            suiteEntity.adminBackendId = dto.adminId != null ? dto.adminId : 0;
            suiteEntity.name = dto.name;
            suiteEntity.description = dto.description;
            suiteEntity.createdAt = System.currentTimeMillis();

            long localSuiteId = suiteDao.insert(suiteEntity);
            if (localSuiteId == -1) {
                // Already exists (REPLACE strategy), re-read
                // For simplicity, skip if can't insert — it was already synced
                Log.d(TAG, "Suite '" + dto.name + "' may already exist locally");
                continue;
            }

            // Now pull cases for this suite
            if (dto.id != null) {
                pullCasesForSuite(dto.id, localSuiteId, token, caseDao);
            }
        }
        Log.i(TAG, "Pulled " + suiteDtos.size() + " test suites from backend");
    }

    private void pullCasesForSuite(long backendSuiteId, long localSuiteId,
                                    String token, TestCaseDao caseDao) {
        try {
            Response<List<ApiService.TestCaseDto>> resp =
                    ApiClient.getService(context)
                            .getTestCases(token, backendSuiteId).execute();

            if (resp.isSuccessful() && resp.body() != null) {
                for (ApiService.TestCaseDto dto : resp.body()) {
                    TestCaseEntity entity = new TestCaseEntity();
                    entity.suiteId = localSuiteId;
                    entity.name = dto.name;
                    entity.transactionType = dto.transactionType;
                    entity.status = dto.status;
                    entity.amount = dto.amount;
                    entity.de22 = dto.de22;
                    entity.pan = dto.maskedPan;
                    entity.expiry = dto.expiry;
                    entity.track2 = dto.track2;
                    entity.scheme = dto.scheme;
                    entity.fieldConfigJson = dto.fieldConfigJson;
                    entity.timestamp = System.currentTimeMillis();
                    caseDao.insert(entity);
                }
                Log.d(TAG, "Pulled " + resp.body().size() + " cases for suite " + backendSuiteId);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error pulling cases for suite " + backendSuiteId + ": " + e.getMessage());
        }
    }

    /**
     * Push all local test suites and cases to the backend (bulk sync).
     * Backend handles deduplication.
     */
    public void push() {
        if (!ApiClient.isLoggedIn(context)) {
            Log.d(TAG, "Not logged in, skipping test suite push");
            return;
        }

        // Push is more complex since we need to read LiveData synchronously.
        // For now, we rely on pull() after login to keep backend in sync.
        // A full push would use getAllSuitesSync() which we can add later.
        Log.d(TAG, "Push not yet implemented — use pull() for sync");
    }
}

