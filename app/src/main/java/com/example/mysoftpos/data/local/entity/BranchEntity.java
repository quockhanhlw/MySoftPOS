package com.example.mysoftpos.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "branches", indices = {
        @Index(value = { "backend_id" }, unique = true),
        @Index(value = { "merchant_backend_id" })
})
public class BranchEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "backend_id")
    public long backendId;

    @ColumnInfo(name = "merchant_backend_id")
    public long merchantBackendId;

    @ColumnInfo(name = "branch_code")
    public String branchCode;

    @ColumnInfo(name = "branch_name")
    public String branchName;

    @ColumnInfo(name = "branch_address")
    public String branchAddress;

    @ColumnInfo(name = "created_at", defaultValue = "0")
    public long createdAt;
}
