package com.example.mysoftpos.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TransactionEntity transaction);

    @Query("UPDATE transactions SET status = :newStatus WHERE trace_number = :traceNumber")
    void updateStatus(String traceNumber, String newStatus);

    @Query("UPDATE transactions SET response_hex = :responseHex, status = :status WHERE trace_number = :traceNumber")
    void updateResponse(String traceNumber, String responseHex, String status);

    @Query("SELECT * FROM transactions WHERE trace_number = :traceNumber LIMIT 1")
    TransactionEntity getByTraceNumber(String traceNumber);

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    List<TransactionEntity> getAllTransactions();
}
