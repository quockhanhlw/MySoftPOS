package com.example.mysoftpos.data.local.dao;
import com.example.mysoftpos.data.local.dao.TransactionTemplateDao;
import com.example.mysoftpos.data.local.entity.TransactionTemplateEntity;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TransactionTemplateDao {
    @Query("SELECT * FROM transaction_templates ORDER BY created_at DESC")
    LiveData<List<TransactionTemplateEntity>> getAllTemplates();

    @Query("SELECT * FROM transaction_templates WHERE id = :id LIMIT 1")
    TransactionTemplateEntity getTemplateById(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TransactionTemplateEntity template);

    @Update
    void update(TransactionTemplateEntity template);

    @Delete
    void delete(TransactionTemplateEntity template);
}






