package com.example.mysoftpos.data.local.dao;

import com.example.mysoftpos.data.local.entity.TransactionEntity;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import java.util.List;

@Dao
public interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(TransactionEntity transaction);

    @Update
    void update(TransactionEntity transaction);

    @Query("UPDATE transactions SET status = :newStatus WHERE trace_number = :traceNumber")
    void updateStatus(String traceNumber, String newStatus);

    @Query("UPDATE transactions SET response_hex = :responseHex, status = :status WHERE trace_number = :traceNumber")
    void updateResponse(String traceNumber, String responseHex, String status);

    @Query("UPDATE transactions SET response_hex = :responseHex WHERE trace_number = :traceNumber")
    void updateResponseHex(String traceNumber, String responseHex);

    /** Update the denormalized RRN column after a response is parsed. */
    @Query("UPDATE transactions SET rrn = :rrn WHERE trace_number = :traceNumber")
    void updateRrn(String traceNumber, String rrn);

    @Query("SELECT * FROM transactions WHERE trace_number = :traceNumber LIMIT 1")
    TransactionEntity getByTraceNumber(String traceNumber);

    @Query("SELECT * FROM transactions ORDER BY txn_timestamp DESC")
    List<TransactionEntity> getAllTransactions();

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND status IS NOT NULL AND status != 'PENDING' ORDER BY txn_timestamp DESC")
    List<TransactionEntity> getCompletedTransactionsByUserIdSync(long userId);

    @Query("SELECT * FROM transactions ORDER BY txn_timestamp DESC")
    androidx.lifecycle.LiveData<List<TransactionEntity>> getAllTransactionsLive();

    @Query("SELECT t.* FROM transactions t INNER JOIN pos_accounts u ON t.user_id = u.id WHERE u.username = :usernameHash OR u.username_hash = :usernameHash ORDER BY t.txn_timestamp DESC")
    androidx.lifecycle.LiveData<List<TransactionEntity>> getTransactionsByUsernameHashLive(String usernameHash);

    // Kept for direct ID access if needed
    @Query("SELECT * FROM transactions WHERE user_id = :userId ORDER BY txn_timestamp DESC")
    androidx.lifecycle.LiveData<List<TransactionEntity>> getTransactionsByUserIdLive(long userId);

    /** Backward-compatible method name; now keyed by user_id. */
    @Query("SELECT * FROM transactions WHERE user_id = :userId ORDER BY txn_timestamp DESC")
    androidx.lifecycle.LiveData<List<TransactionEntity>> getTransactionsByOwnerLive(long userId);

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    androidx.lifecycle.LiveData<com.example.mysoftpos.data.local.entity.TransactionWithDetails> getTransactionWithDetailsById(
            long id);

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    com.example.mysoftpos.data.local.entity.TransactionWithDetails getTransactionWithDetailsByIdSync(long id);
}
