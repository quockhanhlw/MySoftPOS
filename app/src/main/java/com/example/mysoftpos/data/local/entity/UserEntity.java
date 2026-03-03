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
@Entity(tableName = "users", indices = {
        @Index(value = { "username_hash" }, unique = true),
        @Index(value = { "phone" }, unique = true)
})
public class UserEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "username_hash")
    public String usernameHash; // SHA-256 of username

    @ColumnInfo(name = "password_hash")
    public String passwordHash; // SHA-256 of password

    @ColumnInfo(name = "display_name")
    public String displayName; // Plain text display name

    @ColumnInfo(name = "email")
    public String email;

    @ColumnInfo(name = "phone")
    public String phone;

    @ColumnInfo(name = "dob")
    public String dob;

    @ColumnInfo(name = "role")
    public String role; // "ADMIN" or "USER"

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "admin_id")
    public String adminId; // username hash of admin who manages this user

    @ColumnInfo(name = "server_ip")
    public String serverIp;

    @ColumnInfo(name = "server_port")
    public int serverPort;

    // PA-DSS 3.x: Account lockout after failed login attempts
    @ColumnInfo(name = "failed_login_attempts", defaultValue = "0")
    public int failedLoginAttempts;

    @ColumnInfo(name = "locked_until", defaultValue = "0")
    public long lockedUntil; // epoch ms — locked until this time

    public UserEntity() {
    }

    @Ignore
    public UserEntity(String usernameHash, String passwordHash, String displayName, String role, String email,
            String phone, String dob) {
        this.usernameHash = usernameHash;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.email = email;
        this.phone = phone;
        this.dob = dob;
        this.createdAt = System.currentTimeMillis();
        this.serverIp = "";
        this.serverPort = 0;
        this.failedLoginAttempts = 0;
        this.lockedUntil = 0;
    }
}
