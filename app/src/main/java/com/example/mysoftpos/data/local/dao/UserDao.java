package com.example.mysoftpos.data.local.dao;

import com.example.mysoftpos.data.local.entity.UserEntity;

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
public interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(UserEntity user);

    @androidx.room.Update
    void update(UserEntity user);

    @Delete
    void delete(UserEntity user);

    @Query("SELECT * FROM pos_accounts WHERE username_hash = :usernameHash LIMIT 1")
    UserEntity findByUsernameHash(String usernameHash);

    @Query("SELECT * FROM pos_accounts WHERE username = :username LIMIT 1")
    UserEntity findByUsername(String username);

    @Query("SELECT EXISTS(SELECT 1 FROM pos_accounts WHERE username_hash = :usernameHash)")
    boolean existsByUsernameHash(String usernameHash);

    @Query("SELECT EXISTS(SELECT 1 FROM pos_accounts WHERE username = :username)")
    boolean existsByUsername(String username);

    @Query("SELECT COUNT(*) FROM pos_accounts")
    int count();

    @Query("SELECT * FROM pos_accounts WHERE username_hash = :hash LIMIT 1")
    UserEntity getByUsernameHashSync(String hash);

    @Query("SELECT * FROM pos_accounts WHERE email = :email LIMIT 1")
    UserEntity findByEmail(String email);

    @Query("SELECT * FROM pos_accounts WHERE phone = :phone LIMIT 1")
    UserEntity findByPhone(String phone);

    /** Find user by any identifier: username (plain), usernameHash (legacy) or email. */
    @Query("SELECT * FROM pos_accounts WHERE username = :identifier OR email = :identifier OR username_hash = :identifierHash LIMIT 1")
    UserEntity findByAnyIdentifier(String identifier, String identifierHash);

    @Query("SELECT EXISTS(SELECT 1 FROM pos_accounts WHERE email = :email)")
    boolean existsByEmail(String email);

    @Query("SELECT EXISTS(SELECT 1 FROM pos_accounts WHERE phone = :phone)")
    boolean existsByPhone(String phone);

    @Query("SELECT * FROM pos_accounts WHERE admin_id = :adminId ORDER BY created_at DESC")
    LiveData<List<UserEntity>> getAllByAdminId(String adminId);

    @Query("SELECT * FROM pos_accounts WHERE admin_id = :adminId ORDER BY created_at DESC")
    List<UserEntity> getAllByAdminIdSync(String adminId);

    @Query("SELECT * FROM pos_accounts WHERE backend_id = :backendId LIMIT 1")
    UserEntity findByBackendId(long backendId);
}
