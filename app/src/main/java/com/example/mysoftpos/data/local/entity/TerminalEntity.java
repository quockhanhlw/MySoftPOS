package com.example.mysoftpos.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "terminals", foreignKeys = @ForeignKey(entity = MerchantEntity.class, parentColumns = "id", childColumns = "merchant_id", onDelete = ForeignKey.CASCADE), indices = {
        @Index(value = "terminal_code", unique = true),
        @Index("merchant_id")
})
public class TerminalEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "terminal_code")
    public String terminalCode; // DE 41

    @ColumnInfo(name = "merchant_id")
    public long merchantId;

    public TerminalEntity() {
    }

    public TerminalEntity(String terminalCode, long merchantId) {
        this.terminalCode = terminalCode;
        this.merchantId = merchantId;
    }
}
