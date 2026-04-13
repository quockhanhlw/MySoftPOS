package com.example.mysoftpos.data.remote;

import android.content.Context;
import android.util.Log;

import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.entity.TransactionEntity;
import com.example.mysoftpos.data.repository.SensitiveDataMaskingService;
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Instant;

import retrofit2.Response;

/**
 * Syncs local transactions to the backend API.
 * Call {@link #syncUnsynced()} after each transaction completes,
 * or periodically.
 */
public class TransactionSyncManager {

    private static final String TAG = "TxnSyncManager";
    private final Context context;
    private final SensitiveDataMaskingService maskingService = new SensitiveDataMaskingService();

    public TransactionSyncManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Collects all local transactions and attempts to push them to the backend.
     * The backend ignores duplicates (by traceNumber), so it's safe to call repeatedly.
     */
    public void syncUnsynced() {
        if (!ApiClient.isLoggedIn(context)) {
            Log.d(TAG, "Not logged in, skipping sync");
            return;
        }

        long backendUserId = ApiClient.getUserId(context);
        if (backendUserId <= 0) {
            Log.w(TAG, "Missing backend user id in session, skipping sync");
            return;
        }

        new Thread(() -> {
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
                    return;
                }

                int successCount = 0;
                int failureCount = 0;
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
                    item.cardId = txn.cardId;
                    item.terminalId = null;

                    // Get card and terminal info from transaction details
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


                    String token = ApiClient.bearerToken(context);
                    ApiService.TransactionSyncRequest request = new ApiService.TransactionSyncRequest(
                            java.util.Collections.singletonList(item));

                    try {
                        Response<Map<String, Integer>> response =
                                ApiClient.getService(context).syncTransactions(token, request).execute();
                        if (response.isSuccessful() && response.body() != null) {
                            Integer syncedCount = response.body().get("syncedCount");
                            int accepted = syncedCount != null ? syncedCount : 0;
                            if (accepted > 0) {
                                markLocalSynced(java.util.Collections.singletonList(txn.traceNumber), Instant.now().toString());
                                successCount++;
                                Log.i(TAG, "Synced trace=" + txn.traceNumber
                                        + ", scheme=" + item.cardScheme + ", maskedPan=" + item.maskedPan);
                            } else {
                                failureCount++;
                                Log.w(TAG, "Backend returned syncedCount=0 for trace=" + txn.traceNumber
                                        + ", scheme=" + item.cardScheme + ", maskedPan=" + item.maskedPan);
                            }
                        } else {
                            failureCount++;
                            Log.w(TAG, "Sync failed for trace=" + txn.traceNumber
                                    + ", http=" + response.code());
                        }
                    } catch (Exception ex) {
                        failureCount++;
                        Log.w(TAG, "Sync exception for trace=" + txn.traceNumber + ": " + ex.getMessage());
                    }
                }

                Log.i(TAG, "Sync finished. success=" + successCount + ", failed=" + failureCount);

            } catch (Exception e) {
                Log.e(TAG, "Sync error: " + e.getMessage(), e);
            }
        }).start();
    }

    private void markLocalSynced(List<String> traceNumbers, String syncedAt) {
        if (traceNumbers == null || traceNumbers.isEmpty()) {
            return;
        }
        try {
            AppDatabase.getInstance(context).transactionDao()
                    .markSyncedAtByTraceNumbers(traceNumbers, syncedAt);
            Log.d(TAG, "Updated local synced_at for " + traceNumbers.size() + " transactions");
        } catch (Exception e) {
            Log.w(TAG, "Failed to update local synced_at: " + e.getMessage());
        }
    }

    private String normalizeStatusForBackend(String status) {
        if (status == null) {
            return null;
        }
        String normalized = status.trim();
        // Keep compatibility with backend schemas that still define status as VARCHAR(20).
        return normalized.length() > 20 ? normalized.substring(0, 20) : normalized;
    }
}

