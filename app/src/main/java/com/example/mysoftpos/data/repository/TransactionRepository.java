package com.example.mysoftpos.data.repository;

import androidx.lifecycle.LiveData;
import com.example.mysoftpos.data.local.entity.TransactionEntity;
import com.example.mysoftpos.iso8583.message.IsoMessage;
import com.example.mysoftpos.domain.model.CardInputData;
import java.util.List;

public interface TransactionRepository {
    // DB Operations
    LiveData<List<TransactionEntity>> getAllTransactions();

    LiveData<com.example.mysoftpos.data.local.entity.TransactionWithDetails> getTransactionWithDetailsById(long id);

    com.example.mysoftpos.data.local.entity.TransactionWithDetails getTransactionWithDetailsByIdSync(long id);

    void saveTransaction(com.example.mysoftpos.domain.model.TransactionRecord record);

    void updateTransactionStatus(String traceNumber, String status);

    /**
     * Synchronous variant for callers already running on a background thread.
     * Helps guarantee state is persisted before triggering dependent work.
     */
    void updateTransactionStatusSync(String traceNumber, String status);

    void updateTransactionResponse(String traceNumber, String responseHex, String status);

    // ISO Operations (Future: Move from Activity to here)
    // void sendIsoMessage(IsoMessage message, Callback callback);

    void updateTransactionResponseHex(String traceNumber, String responseHex);

    /**
     * Synchronous variant for callers already running on a background thread.
     */
    void updateTransactionResponseHexSync(String traceNumber, String responseHex);

    void updateTransactionRrn(String traceNumber, String rrn);

    /**
     * Synchronous variant for callers already running on a background thread.
     */
    void updateTransactionRrnSync(String traceNumber, String rrn);
}
