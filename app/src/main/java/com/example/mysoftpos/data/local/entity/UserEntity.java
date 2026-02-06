package com.example.mysoftpos.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entity representing a registered user.
 * Username and password are stored as SHA-256 hashes.
 */
@Entity(tableName = "users", indices = { @Index(value = { "username_hash" }, unique = true) })
public class UserEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "username_hash")
    public String usernameHash; // SHA-256 of username

    @ColumnInfo(name = "password_hash")
    public String passwordHash; // SHA-256 of password

    @ColumnInfo(name = "display_name")
    public String displayName; // Plain text display name

    @ColumnInfo(name = "role")
    public String role; // "ADMIN" or "USER"

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public UserEntity() {
    }

    @Ignore
    public UserEntity(String usernameHash, String passwordHash, String displayName, String role) {
        this.usernameHash = usernameHash;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.createdAt = System.currentTimeMillis();
    }
}
