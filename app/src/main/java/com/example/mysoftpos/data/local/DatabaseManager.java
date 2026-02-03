package com.example.mysoftpos.data.local;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.mysoftpos.data.local.dao.TransactionDao;
import com.example.mysoftpos.data.local.entity.TransactionEntity;

public class DatabaseManager {

    private final TransactionDao transactionDao;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public DatabaseManager(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        this.transactionDao = db.transactionDao();
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public interface Callback<T> {
        void onResult(T result);
    }

    public void insertTransaction(TransactionEntity txn) {
        executorService.execute(() -> transactionDao.insert(txn));
    }

    public void updateStatus(String traceNumber, String status) {
        executorService.execute(() -> transactionDao.updateStatus(traceNumber, status));
    }

    public void getTransaction(String traceNumber, Callback<TransactionEntity> callback) {
        executorService.execute(() -> {
            TransactionEntity result = transactionDao.getByTraceNumber(traceNumber);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    public void getAllTransactions(Callback<List<TransactionEntity>> callback) {
        executorService.execute(() -> {
            List<TransactionEntity> results = transactionDao.getAllTransactions();
            mainHandler.post(() -> callback.onResult(results));
        });
    }
}
