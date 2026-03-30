package com.example.mysoftpos.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.mysoftpos.data.local.entity.MerchantEntity;

import java.util.List;

@Dao
public interface MerchantDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(MerchantEntity merchant);

    @Update
    void update(MerchantEntity merchant);

    @Query("SELECT * FROM merchants WHERE merchant_code = :code LIMIT 1")
    MerchantEntity getByCode(String code);

    @Query("SELECT * FROM merchants WHERE backend_id = :backendId LIMIT 1")
    MerchantEntity getByBackendId(long backendId);

    @Query("SELECT * FROM merchants WHERE owner_user_backend_id = :ownerBackendId LIMIT 1")
    MerchantEntity getByOwnerUserBackendId(long ownerBackendId);

    @Query("SELECT * FROM merchants WHERE phone = :phone LIMIT 1")
    MerchantEntity getByPhone(String phone);

    @Query("SELECT * FROM merchants WHERE email = :email LIMIT 1")
    MerchantEntity getByEmail(String email);

    @Query("SELECT * FROM merchants ORDER BY id")
    List<MerchantEntity> getAll();

    @Query("DELETE FROM merchants")
    void deleteAll();
}
