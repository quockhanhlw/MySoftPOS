package com.example.mysoftpos.data.local;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.mysoftpos.data.local.dao.*;
import com.example.mysoftpos.data.local.entity.*;

public class DatabaseManager {

    private final TransactionDao transactionDao;
    private final MerchantDao merchantDao;
    private final TerminalDao terminalDao;
    private final CardDao cardDao;
    private final UserDao userDao;

    private final ExecutorService executorService;
    private final Handler mainHandler;

    public DatabaseManager(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        this.transactionDao = db.transactionDao();
        this.merchantDao = db.merchantDao();
        this.terminalDao = db.terminalDao();
        this.cardDao = db.cardDao();
        this.userDao = db.userDao();

        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public interface Callback<T> {
        void onResult(T result);
    }

    // 3NF Insert Logic
    public void saveTransaction(
            String traceNumber, String amount, String status, String reqHex, String respHex, long timestamp,
            String merchantCode, String merchantName,
            String terminalCode,
            String panMasked, String bin, String last4, String scheme,
            String username) {
        executorService.execute(() -> {
            // 1. Merchant
            MerchantEntity merchant = merchantDao.getByCode(merchantCode);
            long merchantId;
            if (merchant == null) {
                merchantId = merchantDao.insert(new MerchantEntity(merchantCode, merchantName));
            } else {
                merchantId = merchant.id;
            }

            // 2. Terminal
            TerminalEntity terminal = terminalDao.getByCode(terminalCode);
            long terminalId;
            if (terminal == null) {
                terminalId = terminalDao.insert(new TerminalEntity(terminalCode, merchantId));
            } else {
                terminalId = terminal.id;
            }

            // 3. Card
            CardEntity card = cardDao.getByPanMasked(panMasked);
            long cardId;
            if (card == null) {
                cardId = cardDao.insert(new CardEntity(panMasked, bin, last4, scheme));
            } else {
                cardId = card.id;
            }

            // 4. User
            String userIdHash = (username != null)
                    ? com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(username)
                    : null;
            UserEntity user = (userIdHash != null) ? userDao.getByUsernameHashSync(userIdHash) : null;
            Long userId = (user != null) ? user.id : null;

            // 5. Transaction
            TransactionEntity txn = new TransactionEntity();
            txn.traceNumber = traceNumber;
            txn.amount = amount;
            txn.status = status;
            txn.requestHex = reqHex;
            txn.responseHex = respHex;
            txn.timestamp = timestamp;
            txn.terminalId = terminalId;
            txn.cardId = cardId;
            txn.userId = userId;

            transactionDao.insert(txn);
        });
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
