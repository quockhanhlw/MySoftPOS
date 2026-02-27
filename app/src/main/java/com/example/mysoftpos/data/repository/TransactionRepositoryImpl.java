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
    public LiveData<com.example.mysoftpos.data.local.entity.TransactionWithDetails> getTransactionWithDetailsById(
            long id) {
        return db.transactionDao().getTransactionWithDetailsById(id);
    }

    @Override
    public com.example.mysoftpos.data.local.entity.TransactionWithDetails getTransactionWithDetailsByIdSync(long id) {
        return db.transactionDao().getTransactionWithDetailsByIdSync(id);
    }

    @Override
    public void saveTransaction(com.example.mysoftpos.domain.model.TransactionRecord record) {
        dispatchers.io().execute(() -> {
            // 1. Merchant
            com.example.mysoftpos.data.local.entity.MerchantEntity merchant = db.merchantDao()
                    .getByCode(record.merchantCode);
            long merchantId;
            if (merchant == null) {
                merchantId = db.merchantDao()
                        .insert(new com.example.mysoftpos.data.local.entity.MerchantEntity(record.merchantCode,
                                record.merchantName));
            } else {
                merchantId = merchant.id;
            }

            // 2. Terminal
            com.example.mysoftpos.data.local.entity.TerminalEntity terminal = db.terminalDao()
                    .getByCode(record.terminalCode);
            long terminalId;
            if (terminal == null) {
                terminalId = db.terminalDao()
                        .insert(new com.example.mysoftpos.data.local.entity.TerminalEntity(record.terminalCode,
                                merchantId));
            } else {
                terminalId = terminal.id;
            }

            // 3. Card
            com.example.mysoftpos.data.local.entity.CardEntity card = db.cardDao().getByPanMasked(record.panMasked);
            long cardId;
            if (card == null) {
                cardId = db.cardDao()
                        .insert(new com.example.mysoftpos.data.local.entity.CardEntity(record.panMasked, record.bin,
                                record.last4, record.scheme));
            } else {
                cardId = card.id;
            }

            // 4. User — find by multiple methods (phone, email, usernameHash)
            // Username can be phone, email, or raw username depending on login method
            String username = record.username;
            com.example.mysoftpos.data.local.entity.UserEntity user = null;
            if (username != null && !username.isEmpty()) {
                // Try by phone first (most common login method)
                user = db.userDao().findByPhone(username);
                // Then by email
                if (user == null) {
                    user = db.userDao().findByEmail(username);
                }
                // Then by usernameHash = SHA256(username)
                if (user == null) {
                    String hash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(username);
                    user = db.userDao().getByUsernameHashSync(hash);
                }
            }
            Long userId = (user != null) ? user.id : null;

            // 5. Transaction
            TransactionEntity txn = new TransactionEntity();
            txn.traceNumber = record.traceNumber;
            txn.amount = record.amount;
            txn.status = record.status;
            txn.requestHex = record.reqHex;
            txn.responseHex = record.respHex;
            txn.timestamp = record.timestamp;
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
