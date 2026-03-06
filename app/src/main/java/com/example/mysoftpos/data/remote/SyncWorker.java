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
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

        try {
            AppDatabase db = AppDatabase.getInstance(context);
            List<TransactionEntity> allTxns = db.transactionDao().getAllTransactions();

            if (allTxns == null || allTxns.isEmpty()) {
                Log.d(TAG, "No transactions to sync");
                return Result.success();
            }

            List<ApiService.TxnItem> items = new ArrayList<>();
            for (TransactionEntity txn : allTxns) {
                if (txn.status == null || "PENDING".equals(txn.status)) continue;

                ApiService.TxnItem item = new ApiService.TxnItem();
                item.traceNumber = txn.traceNumber;
                item.amount = txn.amount;
                item.status = txn.status;
                item.deviceId = android.os.Build.MODEL;
                item.txnTimestamp = txn.timestamp;

                try {
                    com.example.mysoftpos.data.local.entity.TransactionWithDetails details =
                            db.transactionDao().getTransactionWithDetailsByIdSync(txn.id);
                    if (details != null && details.card != null) {
                        item.maskedPan = details.card.panMasked;
                        item.cardScheme = details.card.scheme;
                    }
                    if (details != null && details.terminal != null) {
                        item.terminalCode = details.terminal.terminalCode;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get transaction details: " + e.getMessage());
                }

                if (item.terminalCode == null || item.terminalCode.isEmpty()) {
                    item.terminalCode = "AUTO0001";
                }
                items.add(item);
            }

            if (items.isEmpty()) {
                return Result.success();
            }

            // Synchronous Retrofit call (Worker runs on background thread already)
            String token = ApiClient.bearerToken(context);
            ApiService.TransactionSyncRequest request = new ApiService.TransactionSyncRequest(items);

            Response<Map<String, Integer>> response =
                    ApiClient.getService(context).syncTransactions(token, request).execute();

            if (response.isSuccessful() && response.body() != null) {
                Integer count = response.body().get("syncedCount");
                Log.i(TAG, "Synced " + count + " transactions to backend");
                return Result.success();
            } else {
                Log.w(TAG, "Sync failed: HTTP " + response.code());
                return Result.retry(); // WorkManager will retry with backoff
            }
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
}

