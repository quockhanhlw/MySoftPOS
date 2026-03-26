package com.example.mysoftpos.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.mysoftpos.data.local.entity.BranchEntity;

import java.util.List;

@Dao
public interface BranchDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(BranchEntity branch);

    @Update
    void update(BranchEntity branch);

    @Query("SELECT * FROM branches WHERE backend_id = :backendId LIMIT 1")
    BranchEntity getByBackendId(long backendId);

    @Query("SELECT * FROM branches WHERE merchant_backend_id = :merchantBackendId AND branch_code = :branchCode LIMIT 1")
    BranchEntity getByMerchantBackendIdAndCode(long merchantBackendId, String branchCode);

    @Query("SELECT * FROM branches WHERE merchant_backend_id = :merchantBackendId ORDER BY id")
    List<BranchEntity> getByMerchantBackendId(long merchantBackendId);

    @Query("DELETE FROM branches")
    void deleteAll();
}

