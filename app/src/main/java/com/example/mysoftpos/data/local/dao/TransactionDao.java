package com.example.mysoftpos.data.local.dao;
import com.example.mysoftpos.data.local.dao.TransactionDao;
import com.example.mysoftpos.data.local.entity.TransactionEntity;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TransactionEntity transaction);

    @Update
    void update(TransactionEntity transaction);

    @Query("UPDATE transactions SET status = :newStatus WHERE trace_number = :traceNumber")
    void updateStatus(String traceNumber, String newStatus);

    @Query("UPDATE transactions SET response_hex = :responseHex, status = :status WHERE trace_number = :traceNumber")
    void updateResponse(String traceNumber, String responseHex, String status);

    @Query("UPDATE transactions SET response_hex = :responseHex WHERE trace_number = :traceNumber")
    void updateResponseHex(String traceNumber, String responseHex);

    @Query("SELECT * FROM transactions WHERE trace_number = :traceNumber LIMIT 1")
    TransactionEntity getByTraceNumber(String traceNumber);

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    List<TransactionEntity> getAllTransactions();

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    androidx.lifecycle.LiveData<List<TransactionEntity>> getAllTransactionsLive();
}






