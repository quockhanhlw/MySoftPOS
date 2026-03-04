package com.example.mysoftpos.data.remote;

import android.content.Context;
import android.util.Log;

import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.entity.TransactionEntity;
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Syncs local transactions to the backend API.
 * Call {@link #syncUnsynced()} after each transaction completes,
 * or periodically.
 */
public class TransactionSyncManager {

    private static final String TAG = "TxnSyncManager";
    private final Context context;

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

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                List<TransactionEntity> allTxns = db.transactionDao().getAllTransactions();

                if (allTxns == null || allTxns.isEmpty()) {
                    Log.d(TAG, "No transactions to sync");
                    return;
                }

                List<ApiService.TxnItem> items = new ArrayList<>();
                for (TransactionEntity txn : allTxns) {
                    // Skip transactions without a final status
                    if (txn.status == null || "PENDING".equals(txn.status)) continue;

                    ApiService.TxnItem item = new ApiService.TxnItem();
                    item.traceNumber = txn.traceNumber;
                    item.amount = txn.amount;
                    item.status = txn.status;
                    item.deviceId = android.os.Build.MODEL;
                    item.txnTimestamp = txn.timestamp;

                    // Get card and terminal info from transaction details
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

                    // Fallback terminal code
                    if (item.terminalCode == null || item.terminalCode.isEmpty()) {
                        item.terminalCode = "AUTO0001";
                    }

                    items.add(item);
                }

                if (items.isEmpty()) {
                    Log.d(TAG, "No completed transactions to sync");
                    return;
                }

                String token = ApiClient.bearerToken(context);
                ApiService.TransactionSyncRequest request = new ApiService.TransactionSyncRequest(items);

                ApiClient.getService(context).syncTransactions(token, request)
                        .enqueue(new Callback<Map<String, Integer>>() {
                            @Override
                            public void onResponse(Call<Map<String, Integer>> call,
                                    Response<Map<String, Integer>> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    Integer count = response.body().get("syncedCount");
                                    Log.i(TAG, "Synced " + count + " transactions to backend");
                                } else {
                                    Log.w(TAG, "Sync failed: HTTP " + response.code());
                                }
                            }

                            @Override
                            public void onFailure(Call<Map<String, Integer>> call, Throwable t) {
                                Log.w(TAG, "Sync network error: " + t.getMessage());
                            }
                        });

            } catch (Exception e) {
                Log.e(TAG, "Sync error: " + e.getMessage(), e);
            }
        }).start();
    }
}

