package com.example.mysoftpos.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.example.mysoftpos.data.local.entity.MerchantEntity;

@Dao
public interface MerchantDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(MerchantEntity merchant);

    @Query("SELECT * FROM merchants WHERE merchant_code = :code LIMIT 1")
    MerchantEntity getByCode(String code);
}
