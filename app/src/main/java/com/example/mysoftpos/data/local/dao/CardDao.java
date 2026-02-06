package com.example.mysoftpos.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.example.mysoftpos.data.local.entity.CardEntity;

@Dao
public interface CardDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(CardEntity card);

    @Query("SELECT * FROM cards WHERE pan_masked = :panMasked LIMIT 1")
    CardEntity getByPanMasked(String panMasked);
}
