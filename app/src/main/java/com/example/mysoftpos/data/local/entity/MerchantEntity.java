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

    /** Backend user.id that owns this merchant profile */
    @ColumnInfo(name = "owner_user_backend_id")
    public long ownerUserBackendId;

    @ColumnInfo(name = "full_name")
    public String fullName;

    @ColumnInfo(name = "phone")
    public String phone;

    @ColumnInfo(name = "email")
    public String email;

    @ColumnInfo(name = "dob")
    public String dob;

    @ColumnInfo(name = "gender")
    public String gender;

    @ColumnInfo(name = "business_type")
    public String businessType;

    @ColumnInfo(name = "store_address")
    public String storeAddress;

    @ColumnInfo(name = "bank_name")
    public String bankName;

    @Ignore
    public int branchCount;

    @Ignore
    public String branchAddresses;

    @Ignore
    public int accountCount;

    @ColumnInfo(name = "merchant_code")
    public String merchantCode; // DE 42

    @ColumnInfo(name = "merchant_name")
    public String merchantName; // DE 43

    public MerchantEntity() {
    }

    @Ignore
    public MerchantEntity(String merchantCode, String merchantName) {
        this.merchantCode = merchantCode;
        this.merchantName = merchantName;
        this.fullName = "";
        this.phone = "";
        this.email = "";
        this.dob = "";
        this.gender = "";
        this.businessType = "";
        this.storeAddress = "";
        this.bankName = "";
        this.branchCount = 0;
        this.branchAddresses = "";
        this.accountCount = 1;
    }
}
