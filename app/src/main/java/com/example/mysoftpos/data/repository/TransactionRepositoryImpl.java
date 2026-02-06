package com.example.mysoftpos.data.repository;

import com.example.mysoftpos.data.local.dao.TransactionDao;

import androidx.lifecycle.LiveData;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.entity.TransactionEntity;
import com.example.mysoftpos.utils.threading.DispatcherProvider;
import java.util.List;

public class TransactionRepositoryImpl implements TransactionRepository {

    private final AppDatabase db;
    private final DispatcherProvider dispatchers;

    public TransactionRepositoryImpl(AppDatabase db, DispatcherProvider dispatchers) {
        this.db = db;
        this.dispatchers = dispatchers;
    }

    @Override
    public LiveData<List<TransactionEntity>> getAllTransactions() {
        return db.transactionDao().getAllTransactionsLive();
    }

    @Override
    public void saveTransaction(
            String traceNumber, String amount, String status, String reqHex, String respHex, long timestamp,
            String merchantCode, String merchantName,
            String terminalCode,
            String panMasked, String bin, String last4, String scheme,
            String username) {
        dispatchers.io().execute(() -> {
            // 1. Merchant
            com.example.mysoftpos.data.local.entity.MerchantEntity merchant = db.merchantDao().getByCode(merchantCode);
            long merchantId;
            if (merchant == null) {
                merchantId = db.merchantDao()
                        .insert(new com.example.mysoftpos.data.local.entity.MerchantEntity(merchantCode, merchantName));
            } else {
                merchantId = merchant.id;
            }

            // 2. Terminal
            com.example.mysoftpos.data.local.entity.TerminalEntity terminal = db.terminalDao().getByCode(terminalCode);
            long terminalId;
            if (terminal == null) {
                terminalId = db.terminalDao()
                        .insert(new com.example.mysoftpos.data.local.entity.TerminalEntity(terminalCode, merchantId));
            } else {
                terminalId = terminal.id;
            }

            // 3. Card
            com.example.mysoftpos.data.local.entity.CardEntity card = db.cardDao().getByPanMasked(panMasked);
            long cardId;
            if (card == null) {
                cardId = db.cardDao()
                        .insert(new com.example.mysoftpos.data.local.entity.CardEntity(panMasked, bin, last4, scheme));
            } else {
                cardId = card.id;
            }

            // 4. User
            String userIdHash = (username != null)
                    ? com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(username)
                    : null;
            com.example.mysoftpos.data.local.entity.UserEntity user = (userIdHash != null)
                    ? db.userDao().getByUsernameHashSync(userIdHash)
                    : null;
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

            db.transactionDao().insert(txn);
        });
    }

    @Override
    public void updateTransactionStatus(String traceNumber, String status) {
        dispatchers.io().execute(() -> {
            db.transactionDao().updateStatus(traceNumber, status);
        });
    }

    @Override
    public void updateTransactionResponse(String traceNumber, String responseHex, String status) {
        dispatchers.io().execute(() -> {
            db.transactionDao().updateResponse(traceNumber, responseHex, status);
        });
    }

    @Override
    public void updateTransactionResponseHex(String traceNumber, String responseHex) {
        dispatchers.io().execute(() -> {
            db.transactionDao().updateResponseHex(traceNumber, responseHex);
        });
    }
}
