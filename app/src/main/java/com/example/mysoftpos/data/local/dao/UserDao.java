package com.example.mysoftpos.data.local.dao;

import com.example.mysoftpos.data.local.dao.UserDao;
import com.example.mysoftpos.data.local.entity.UserEntity;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/**
 * DAO for User operations.
 */
@Dao
public interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(UserEntity user);

    @androidx.room.Update
    void update(UserEntity user);

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

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE email = :email)")
    boolean existsByEmail(String email);

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE phone = :phone)")
    boolean existsByPhone(String phone);
}
