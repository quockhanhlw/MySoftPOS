package com.example.mysoftpos.data.remote;

import android.content.Context;
import android.util.Log;

import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.dao.TestCaseDao;
import com.example.mysoftpos.data.local.dao.TestSuiteDao;
import com.example.mysoftpos.data.local.dao.CardDao;
import com.example.mysoftpos.data.local.entity.CardEntity;
import com.example.mysoftpos.data.local.entity.TestCaseEntity;
import com.example.mysoftpos.data.local.entity.TestSuiteEntity;
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;

import java.util.List;
import java.util.ArrayList;

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
                                    pullCards(token);
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
            long backendSuiteId = dto.id != null ? dto.id : 0;
            if (backendSuiteId <= 0) {
                continue;
            }

            TestSuiteEntity suiteEntity = suiteDao.findByBackendIdSync(backendSuiteId);
            if (suiteEntity == null) {
                suiteEntity = new TestSuiteEntity();
            }
            suiteEntity.backendId = backendSuiteId;
            suiteEntity.adminBackendId = dto.adminId != null ? dto.adminId : 0;
            suiteEntity.name = dto.name;
            suiteEntity.description = dto.description;
            if (suiteEntity.createdAt <= 0) {
                suiteEntity.createdAt = System.currentTimeMillis();
            }
            long localSuiteId = suiteEntity.id > 0 ? suiteEntity.id : suiteDao.insert(suiteEntity);
            if (suiteEntity.id > 0) {
                suiteDao.update(suiteEntity);
            }

            // Now pull cases for this suite
            pullCasesForSuite(backendSuiteId, localSuiteId, token, caseDao);
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
                    long backendCaseId = dto.id != null ? dto.id : 0;
                    TestCaseEntity entity = backendCaseId > 0 ? caseDao.findByBackendIdSync(backendCaseId) : null;
                    if (entity == null) {
                        entity = new TestCaseEntity();
                    }
                    entity.backendId = backendCaseId;
                    entity.suiteId = localSuiteId;
                    entity.name = dto.name;
                    entity.transactionType = dto.transactionType;
                    entity.status = dto.status;
                    entity.amount = dto.amount;
                    entity.de22 = dto.de22;
                    entity.maskedPan = dto.maskedPan;
                    entity.expiry = dto.expiry;
                    entity.requestFilePath = dto.reqFilePath;
                    entity.responseFilePath = dto.resFilePath;
                    entity.scheme = dto.scheme;
                    entity.fieldConfigJson = dto.fieldConfigJson;
                    entity.isDefault = dto.isDefault != null && dto.isDefault;
                    entity.timestamp = System.currentTimeMillis();
                    if (entity.id > 0) {
                        caseDao.update(entity);
                    } else {
                        caseDao.insert(entity);
                    }
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

        final String token = ApiClient.bearerToken(context);
        new Thread(() -> {
            try {
                pushSuites(token);
                pushCards(token);
                pull();
            } catch (Exception e) {
                Log.w(TAG, "Push sync failed: " + e.getMessage());
            }
        }).start();
    }

    private void pushSuites(String token) throws Exception {
        AppDatabase db = AppDatabase.getInstance(context);
        TestSuiteDao suiteDao = db.testSuiteDao();
        TestCaseDao caseDao = db.testCaseDao();

        List<TestSuiteEntity> suites = suiteDao.getAllSuitesSync();
        List<ApiService.TestSuiteDto> payload = new ArrayList<>();
        for (TestSuiteEntity suite : suites) {
            ApiService.TestSuiteDto suiteDto = new ApiService.TestSuiteDto();
            if (suite.backendId > 0) {
                suiteDto.id = suite.backendId;
            }
            suiteDto.name = suite.name;
            suiteDto.description = suite.description;
            suiteDto.testCases = new ArrayList<>();

            List<TestCaseEntity> cases = caseDao.getCasesBySuiteSync(suite.id);
            for (TestCaseEntity entity : cases) {
                ApiService.TestCaseDto dto = new ApiService.TestCaseDto();
                if (entity.backendId > 0) {
                    dto.id = entity.backendId;
                }
                dto.name = entity.name;
                dto.transactionType = entity.transactionType;
                dto.status = entity.status;
                dto.amount = entity.amount;
                dto.de22 = entity.de22;
                dto.maskedPan = entity.maskedPan;
                dto.expiry = entity.expiry;
                dto.reqFilePath = entity.requestFilePath;
                dto.resFilePath = entity.responseFilePath;
                dto.scheme = entity.scheme;
                dto.fieldConfigJson = entity.fieldConfigJson;
                dto.isDefault = entity.isDefault;
                suiteDto.testCases.add(dto);
            }
            payload.add(suiteDto);
        }

        if (payload.isEmpty()) {
            return;
        }
        Response<java.util.Map<String, Integer>> response = ApiClient.getService(context)
                .syncTestSuites(token, payload)
                .execute();
        if (!response.isSuccessful()) {
            throw new IllegalStateException("syncTestSuites HTTP " + response.code());
        }
    }

    private void pushCards(String token) throws Exception {
        CardDao cardDao = AppDatabase.getInstance(context).cardDao();
        List<CardEntity> cards = cardDao.getAllSync();
        if (cards.isEmpty()) {
            return;
        }

        List<ApiService.CardDto> payload = new ArrayList<>();
        for (CardEntity entity : cards) {
            ApiService.CardDto dto = new ApiService.CardDto();
            if (entity.backendId > 0) {
                dto.id = entity.backendId;
            }
            dto.panMasked = entity.panMasked;
            dto.bin = entity.bin;
            dto.last4 = entity.last4;
            dto.scheme = entity.scheme;
            dto.adminId = entity.adminBackendId > 0 ? entity.adminBackendId : null;
            dto.posAccountId = entity.posAccountBackendId > 0 ? entity.posAccountBackendId : null;
            payload.add(dto);
        }

        Response<java.util.Map<String, Integer>> response = ApiClient.getService(context)
                .syncCards(token, payload)
                .execute();
        if (!response.isSuccessful()) {
            throw new IllegalStateException("syncCards HTTP " + response.code());
        }
    }

    private void pullCards(String token) {
        try {
            Response<List<ApiService.CardDto>> resp = ApiClient.getService(context).getCards(token).execute();
            if (!resp.isSuccessful() || resp.body() == null) {
                return;
            }
            CardDao cardDao = AppDatabase.getInstance(context).cardDao();
            for (ApiService.CardDto dto : resp.body()) {
                CardEntity entity = null;
                if (dto.id != null && dto.id > 0) {
                    entity = cardDao.findByBackendId(dto.id);
                }
                if (entity == null && dto.panMasked != null) {
                    entity = cardDao.getByPanMasked(dto.panMasked);
                }
                if (entity == null) {
                    entity = new CardEntity();
                }
                entity.backendId = dto.id != null ? dto.id : 0;
                entity.panMasked = dto.panMasked;
                entity.bin = dto.bin;
                entity.last4 = dto.last4;
                entity.scheme = dto.scheme;
                entity.adminBackendId = dto.adminId != null ? dto.adminId : 0;
                entity.posAccountBackendId = dto.posAccountId != null ? dto.posAccountId : 0;
                cardDao.insert(entity);
            }
        } catch (Exception e) {
            Log.w(TAG, "Card pull failed: " + e.getMessage());
        }
    }
}

