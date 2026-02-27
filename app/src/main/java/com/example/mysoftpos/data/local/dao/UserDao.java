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

    @Query("SELECT * FROM users WHERE username_hash = :usernameHash LIMIT 1")
    UserEntity findByUsernameHash(String usernameHash);

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE username_hash = :usernameHash)")
    boolean existsByUsernameHash(String usernameHash);

    @Query("SELECT COUNT(*) FROM users")
    int count();

    @Query("SELECT * FROM users WHERE username_hash = :hash LIMIT 1")
    UserEntity getByUsernameHashSync(String hash);

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    UserEntity findByEmail(String email);

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    UserEntity findByPhone(String phone);

    /** Find user by any identifier: phone, email, or usernameHash (SHA256) */
    @Query("SELECT * FROM users WHERE phone = :identifier OR email = :identifier OR username_hash = :identifierHash LIMIT 1")
    UserEntity findByAnyIdentifier(String identifier, String identifierHash);

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE email = :email)")
    boolean existsByEmail(String email);

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE phone = :phone)")
    boolean existsByPhone(String phone);

    @Query("SELECT * FROM users WHERE admin_id = :adminId ORDER BY created_at DESC")
    LiveData<List<UserEntity>> getAllByAdminId(String adminId);

    @Query("SELECT * FROM users WHERE admin_id = :adminId ORDER BY created_at DESC")
    List<UserEntity> getAllByAdminIdSync(String adminId);
}
