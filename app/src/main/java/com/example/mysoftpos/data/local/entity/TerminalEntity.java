package com.example.mysoftpos.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "terminals", foreignKeys = @ForeignKey(entity = MerchantEntity.class, parentColumns = "id", childColumns = "merchant_id", onDelete = ForeignKey.CASCADE), indices = {
        @Index(value = "terminal_code", unique = true),
        @Index("merchant_id"),
        @Index("branch_backend_id"),
        @Index("pos_account_backend_id")
})
public class TerminalEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Backend Terminal.id for API sync */
    @ColumnInfo(name = "backend_id")
    public long backendId;

    @ColumnInfo(name = "terminal_code")
    public String terminalCode; // DE 41

    @ColumnInfo(name = "merchant_id")
    public long merchantId;

    @ColumnInfo(name = "server_ip")
    public String serverIp;

    @ColumnInfo(name = "server_port")
    public int serverPort;

    @ColumnInfo(name = "branch_backend_id", defaultValue = "0")
    public long branchBackendId;

    @ColumnInfo(name = "pos_account_backend_id", defaultValue = "0")
    public long posAccountBackendId;

    public TerminalEntity() {
    }

    @Ignore
    public TerminalEntity(String terminalCode, long merchantId) {
        this.terminalCode = terminalCode;
        this.merchantId = merchantId;
    }
}
