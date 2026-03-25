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

    @ColumnInfo(name = "gender")
    public String gender;

    @ColumnInfo(name = "store_name")
    public String storeName;

    @ColumnInfo(name = "business_type")
    public String businessType;

    @ColumnInfo(name = "store_address")
    public String storeAddress;

    @ColumnInfo(name = "branch_count", defaultValue = "0")
    public int branchCount;

    @ColumnInfo(name = "branch_addresses")
    public String branchAddresses;

    @ColumnInfo(name = "account_count", defaultValue = "1")
    public int accountCount;

    @ColumnInfo(name = "phone_verified", defaultValue = "0")
    public boolean phoneVerified;

    @ColumnInfo(name = "role")
    public String role; // "ADMIN" or "USER"

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "admin_id")
    public String adminId; // username hash of admin who manages this user

    /** Backend user.id — used for API calls and admin ownership mapping */
    @ColumnInfo(name = "backend_id")
    public long backendId;

    /** Terminal ID (TID / DE 41) assigned to this user */
    @ColumnInfo(name = "terminal_id_assigned")
    public String terminalId;

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
        this.gender = "";
        this.storeName = "";
        this.businessType = "";
        this.storeAddress = "";
        this.branchCount = 0;
        this.branchAddresses = "";
        this.accountCount = 1;
        this.phoneVerified = false;
        this.createdAt = System.currentTimeMillis();
        this.terminalId = "";
        this.serverIp = "";
        this.serverPort = 0;
        this.failedLoginAttempts = 0;
        this.lockedUntil = 0;
    }
}
