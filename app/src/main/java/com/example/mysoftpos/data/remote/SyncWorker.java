package com.example.mysoftpos.data.remote;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.entity.TransactionEntity;
import com.example.mysoftpos.data.repository.SensitiveDataMaskingService;
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.time.Instant;

import retrofit2.Response;

/**
 * WorkManager-based reliable background sync for transactions.
 *
 * Replaces raw {@code new Thread(() -> ...).start()} with a system-managed
 * Worker that:
 * - Survives process death
 * - Respects network constraints (only runs with connectivity)
 * - Automatically retries on failure with exponential backoff
 * - Prevents duplicate sync jobs
 */
public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";
    public static final String UNIQUE_PERIODIC_WORK = "mysoftpos_periodic_sync";
    private final SensitiveDataMaskingService maskingService = new SensitiveDataMaskingService();

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        if (!ApiClient.isLoggedIn(context)) {
            Log.d(TAG, "Not logged in, skipping sync");
            return Result.success();
        }

        long backendUserId = ApiClient.getUserId(context);
        if (backendUserId <= 0) {
            Log.w(TAG, "Missing backend user id in session, skipping sync");
            return Result.success();
        }

        try {
            AppDatabase db = AppDatabase.getInstance(context);
            com.example.mysoftpos.data.local.entity.PosAccountEntity currentUser = db.posAccountDao().findByBackendId(backendUserId);
            List<TransactionEntity> allTxns;
            if (currentUser != null) {
                allTxns = db.transactionDao().getCompletedTransactionsForSync(currentUser.id, backendUserId);
            } else {
                Log.w(TAG, "No local pos_account mapped for backend user id=" + backendUserId
                        + ", fallback sync by legacy user_id");
                allTxns = db.transactionDao().getCompletedTransactionsByLegacyUserIdSync(backendUserId);
            }

            if (allTxns == null || allTxns.isEmpty()) {
                Log.d(TAG, "No transactions to sync");
                return Result.success();
            }

            List<ApiService.TxnItem> items = new ArrayList<>();
            for (TransactionEntity txn : allTxns) {

                ApiService.TxnItem item = new ApiService.TxnItem();
                item.traceNumber = txn.traceNumber;
                item.amount = txn.amount;
                item.status = normalizeStatusForBackend(txn.status);
                item.deviceId = android.os.Build.MODEL;
                item.txnTimestamp = txn.timestamp;
                item.requestHex = maskingService.maskIsoHex(txn.requestHex);
                item.responseHex = maskingService.maskIsoHex(txn.responseHex);
                item.processingCode = txn.processingCode;
                item.currencyCode = txn.currencyCode;
                item.rrn = txn.rrn;
                item.terminalId = null;
                item.cardId = txn.cardId;

                try {
                    com.example.mysoftpos.data.local.entity.TransactionWithDetails details =
                            db.transactionDao().getTransactionWithDetailsByIdSync(txn.id);
                    if (details != null && details.card != null) {
                        item.maskedPan = details.card.panMasked;
                        item.cardScheme = details.card.scheme;
                    }
                    if (details != null && details.terminal != null
                            && details.terminal.terminalCode != null
                            && !details.terminal.terminalCode.trim().isEmpty()) {
                        item.terminalCode = details.terminal.terminalCode.trim();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get transaction details: " + e.getMessage());
                }
                items.add(item);
            }

            if (items.isEmpty()) {
                return Result.success();
            }

            String token = ApiClient.bearerToken(context);
            int successCount = 0;
            int failureCount = 0;
            for (ApiService.TxnItem item : items) {
                ApiService.TransactionSyncRequest request =
                        new ApiService.TransactionSyncRequest(java.util.Collections.singletonList(item));
                try {
                    Response<Map<String, Integer>> response =
                            ApiClient.getService(context).syncTransactions(token, request).execute();
                    if (response.isSuccessful() && response.body() != null) {
                        Integer syncedCount = response.body().get("syncedCount");
                        int accepted = syncedCount != null ? syncedCount : 0;
                        if (accepted > 0) {
                            db.transactionDao().markSyncedAtByTraceNumbers(
                                    java.util.Collections.singletonList(item.traceNumber),
                                    Instant.now().toString());
                            successCount++;
                            Log.i(TAG, "Synced trace=" + item.traceNumber
                                    + ", scheme=" + item.cardScheme + ", maskedPan=" + item.maskedPan);
                        } else {
                            failureCount++;
                            Log.w(TAG, "Backend returned syncedCount=0 for trace=" + item.traceNumber
                                    + ", scheme=" + item.cardScheme + ", maskedPan=" + item.maskedPan);
                        }
                    } else {
                        failureCount++;
                        Log.w(TAG, "Sync failed for trace=" + item.traceNumber + ", http=" + response.code());
                    }
                } catch (Exception ex) {
                    failureCount++;
                    Log.w(TAG, "Sync exception for trace=" + item.traceNumber + ": " + ex.getMessage());
                }
            }

            Log.i(TAG, "Worker sync finished. success=" + successCount + ", failed=" + failureCount);
            return failureCount == 0 ? Result.success() : Result.retry();
        } catch (Exception e) {
            Log.e(TAG, "Sync error: " + e.getMessage(), e);
            return Result.retry();
        }
    }

    // ==================== Static helpers for scheduling ====================

    /**
     * Enqueue a one-time sync job (e.g., after a transaction completes).
     * Requires network connectivity.
     */
    public static void enqueueOneTime(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueue(request);
    }

    /**
     * Schedule periodic sync (e.g., every 15 minutes).
     * Safe to call multiple times — uses KEEP policy to avoid duplicates.
     */
    public static void schedulePeriodicSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest periodicRequest = new PeriodicWorkRequest.Builder(
                SyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest);
    }

    /**
     * Cancel periodic sync (e.g., on logout).
     */
    public static void cancelPeriodicSync(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK);
    }

    private String normalizeStatusForBackend(String status) {
        if (status == null) {
            return null;
        }
        String normalized = status.trim();
        return normalized.length() > 20 ? normalized.substring(0, 20) : normalized;
    }
}

