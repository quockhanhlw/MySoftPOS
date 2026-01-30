package com.example.mysoftpos.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "transaction_types")
public class TransactionType {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "name")
    public String name; // e.g. "Purchase", "Balance Inquiry"

    @ColumnInfo(name = "mti")
    public String mti; // e.g. "0200"

    @ColumnInfo(name = "processing_code")
    public String processingCode; // e.g. "000000"

    public TransactionType(String name, String mti, String processingCode) {
        this.name = name;
        this.mti = mti;
        this.processingCode = processingCode;
    }
}
