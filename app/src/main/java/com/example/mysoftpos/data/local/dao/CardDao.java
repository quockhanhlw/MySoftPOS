package com.example.mysoftpos.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.example.mysoftpos.data.local.entity.CardEntity;

@Dao
public interface CardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(CardEntity card);

    @Query("SELECT * FROM cards WHERE pan_masked = :panMasked LIMIT 1")
    CardEntity getByPanMasked(String panMasked);

    @Query("SELECT * FROM cards WHERE backend_id = :backendId LIMIT 1")
    CardEntity findByBackendId(long backendId);

    @Query("SELECT * FROM cards ORDER BY id DESC")
    java.util.List<CardEntity> getAllSync();

    @Query("SELECT COUNT(*) FROM cards")
    int getCount();
}
