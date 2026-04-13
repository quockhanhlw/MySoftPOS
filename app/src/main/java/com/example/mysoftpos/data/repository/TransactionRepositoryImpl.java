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
    private final SensitiveDataMaskingService maskingService = new SensitiveDataMaskingService();

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
        dispatchers.io().execute(() -> saveTransactionInternal(record));
    }

    @Override
    public void saveTransactionSync(com.example.mysoftpos.domain.model.TransactionRecord record) {
        saveTransactionInternal(record);
    }

    private void saveTransactionInternal(com.example.mysoftpos.domain.model.TransactionRecord record) {
        // 1. Merchant — IGNORE returns -1 if already exists
        com.example.mysoftpos.data.local.entity.MerchantEntity merchant = db.merchantDao()
                .getByCode(record.merchantCode);
        long merchantId;
        if (merchant != null) {
            merchantId = merchant.id;
        } else {
            merchantId = db.merchantDao()
                    .insert(new com.example.mysoftpos.data.local.entity.MerchantEntity(record.merchantCode,
                            record.merchantName));
            if (merchantId <= 0) {
                merchant = db.merchantDao().getByCode(record.merchantCode);
                merchantId = (merchant != null) ? merchant.id : 0;
            }
        }

        // 2. Terminal — IGNORE returns -1 if already exists
        com.example.mysoftpos.data.local.entity.TerminalEntity terminal = db.terminalDao()
                .getByCode(record.terminalCode);
        long terminalId;
        if (terminal != null) {
            terminalId = terminal.id;
        } else {
            terminalId = db.terminalDao()
                    .insert(new com.example.mysoftpos.data.local.entity.TerminalEntity(record.terminalCode,
                            merchantId));
            if (terminalId <= 0) {
                terminal = db.terminalDao().getByCode(record.terminalCode);
                terminalId = (terminal != null) ? terminal.id : 0;
            }
        }

        // 3. Card — IGNORE returns -1 if already exists, so always lookup first
        com.example.mysoftpos.data.local.entity.CardEntity card = db.cardDao().getByPanMasked(record.panMasked);
        long cardId;
        if (card != null) {
            cardId = card.id;
        } else {
            cardId = db.cardDao()
                    .insert(new com.example.mysoftpos.data.local.entity.CardEntity(record.panMasked, record.bin,
                            record.last4, record.scheme));
            // Double check: if insert returned -1 (race condition), lookup again
            if (cardId <= 0) {
                card = db.cardDao().getByPanMasked(record.panMasked);
                cardId = (card != null) ? card.id : 0;
            }
        }

        // 4. User — use explicit local id when available, otherwise resolve by username/hash
        Long userId;
        com.example.mysoftpos.data.local.entity.PosAccountEntity user = null;
        if (record.userId > 0) {
            userId = record.userId;
            user = db.posAccountDao().getByIdSync(record.userId);
        } else {
            String username = record.username;
            if (username != null && !username.isEmpty()) {
                user = db.posAccountDao().findByUsername(username);
                if (user == null) {
                    String hash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(username);
                    user = db.posAccountDao().getByUsernameHashSync(hash);
                }
            }
            userId = (user != null) ? user.id : null;
        }

        // 5. Transaction — IGNORE if trace_number already exists (no overwrite)
        TransactionEntity txn = new TransactionEntity();
        txn.traceNumber = record.traceNumber;
        txn.amount = record.amount;
        txn.status = record.status;
        // Keep raw ISO locally so void-from-history can rebuild original reversal context.
        // Sensitive masking is applied at sync/log boundaries instead.
        txn.requestHex = record.reqHex;
        txn.responseHex = record.respHex;
        txn.timestamp = record.timestamp;
        txn.terminalId = terminalId > 0 ? terminalId : null;
        txn.cardId = cardId > 0 ? cardId : null;
        txn.userId = userId;
        txn.processingCode = record.processingCode;
        txn.currencyCode = record.currencyCode;
        txn.rrn = record.rrn;
        txn.backendUserId = (user != null && user.backendId > 0) ? user.backendId : null;
        txn.backendUsername = record.username;

        long insertedId = db.transactionDao().insert(txn);
        if (insertedId <= 0) {
            android.util.Log.w("TxnRepo", "Transaction " + record.traceNumber
                    + " already exists, skipping insert");
        }
    }

    @Override
    public void updateTransactionStatus(String traceNumber, String status) {
        dispatchers.io().execute(() -> {
            db.transactionDao().updateStatus(traceNumber, status);
        });
    }

    @Override
    public void updateTransactionStatusSync(String traceNumber, String status) {
        db.transactionDao().updateStatus(traceNumber, status);
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

    @Override
    public void updateTransactionResponseHexSync(String traceNumber, String responseHex) {
        db.transactionDao().updateResponseHex(traceNumber, responseHex);
    }

    @Override
    public void updateTransactionRrn(String traceNumber, String rrn) {
        dispatchers.io().execute(() -> {
            db.transactionDao().updateRrn(traceNumber, rrn);
        });
    }

    @Override
    public void updateTransactionRrnSync(String traceNumber, String rrn) {
        db.transactionDao().updateRrn(traceNumber, rrn);
    }
}
