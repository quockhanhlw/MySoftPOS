package com.example.mysoftpos.data.local.dao;

import com.example.mysoftpos.data.local.entity.PosAccountEntity;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * DAO for User operations.
 */
@Dao
public interface PosAccountDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(PosAccountEntity user);

    @androidx.room.Update
    void update(PosAccountEntity user);

    @Delete
    void delete(PosAccountEntity user);

    @Query("SELECT * FROM pos_accounts WHERE username_hash = :usernameHash LIMIT 1")
    PosAccountEntity findByUsernameHash(String usernameHash);

    @Query("SELECT * FROM pos_accounts WHERE username = :username LIMIT 1")
    PosAccountEntity findByUsername(String username);

    @Query("SELECT EXISTS(SELECT 1 FROM pos_accounts WHERE username_hash = :usernameHash)")
    boolean existsByUsernameHash(String usernameHash);

    @Query("SELECT EXISTS(SELECT 1 FROM pos_accounts WHERE username = :username)")
    boolean existsByUsername(String username);

    @Query("SELECT COUNT(*) FROM pos_accounts")
    int count();

    @Query("SELECT * FROM pos_accounts WHERE username_hash = :hash LIMIT 1")
    PosAccountEntity getByUsernameHashSync(String hash);

    /** Find user by any identifier: username (plain) or usernameHash (legacy). */
    @Query("SELECT * FROM pos_accounts WHERE username = :identifier OR username_hash = :identifierHash LIMIT 1")
    PosAccountEntity findByAnyIdentifier(String identifier, String identifierHash);


    @Query("SELECT * FROM pos_accounts WHERE admin_id = :adminId ORDER BY created_at DESC")
    LiveData<List<PosAccountEntity>> getAllByAdminId(String adminId);

    @Query("SELECT * FROM pos_accounts WHERE admin_id = :adminId ORDER BY created_at DESC")
    List<PosAccountEntity> getAllByAdminIdSync(String adminId);

    @Query("SELECT * FROM pos_accounts WHERE backend_id = :backendId LIMIT 1")
    PosAccountEntity findByBackendId(long backendId);

    @Query("SELECT * FROM pos_accounts WHERE id = :id LIMIT 1")
    PosAccountEntity getByIdSync(long id);
}
