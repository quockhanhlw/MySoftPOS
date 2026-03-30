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
@Entity(tableName = "pos_accounts", indices = {
        @Index(value = { "username" }, unique = true),
        @Index(value = { "username_hash" }, unique = true)
})
public class PosAccountEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "username_hash")
    public String usernameHash; // SHA-256 of username

    @ColumnInfo(name = "username")
    public String username; // Plain username for backend contract parity

    @Ignore
    public String passwordHash; // Deprecated local cache field (not persisted)

    @ColumnInfo(name = "merchant_backend_id", defaultValue = "0")
    public long merchantBackendId;

    @ColumnInfo(name = "branch_backend_id", defaultValue = "0")
    public long branchBackendId;

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

    @Ignore
    public String serverIp; // Deprecated in pos_accounts (moved to terminals)

    @Ignore
    public int serverPort; // Deprecated in pos_accounts (moved to terminals)

    // PA-DSS 3.x: Account lockout after failed login attempts
    @ColumnInfo(name = "failed_login_attempts", defaultValue = "0")
    public int failedLoginAttempts;

    @ColumnInfo(name = "locked_until", defaultValue = "0")
    public long lockedUntil; // epoch ms — locked until this time

    public PosAccountEntity() {
    }

    @Ignore
    public PosAccountEntity(String usernameHash, String role) {
        this.usernameHash = usernameHash;
        this.username = "";
        this.passwordHash = null;
        this.role = role;
        this.merchantBackendId = 0L;
        this.branchBackendId = 0L;
        this.phoneVerified = false;
        this.createdAt = System.currentTimeMillis();
        this.terminalId = "";
        this.serverIp = "";
        this.serverPort = 0;
        this.failedLoginAttempts = 0;
        this.lockedUntil = 0;
    }

    @Ignore
    public PosAccountEntity(String usernameHash, String passwordHash, String role) {
        this(usernameHash, role);
        this.passwordHash = null;
    }
}
