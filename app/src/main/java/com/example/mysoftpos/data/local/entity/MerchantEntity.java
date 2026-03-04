package com.example.mysoftpos.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.Index;

@Entity(tableName = "merchants", indices = { @Index(value = "merchant_code", unique = true) })
public class MerchantEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Backend Merchant.id for API sync */
    @ColumnInfo(name = "backend_id")
    public long backendId;

    /** Backend admin owner ID */
    @ColumnInfo(name = "admin_backend_id")
    public long adminBackendId;

    @ColumnInfo(name = "merchant_code")
    public String merchantCode; // DE 42

    @ColumnInfo(name = "merchant_name_location")
    public String merchantNameLocation; // DE 43

    public MerchantEntity() {
    }

    @Ignore
    public MerchantEntity(String merchantCode, String merchantNameLocation) {
        this.merchantCode = merchantCode;
        this.merchantNameLocation = merchantNameLocation;
    }
}
