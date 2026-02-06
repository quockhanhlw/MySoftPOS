package com.example.mysoftpos.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;

@Entity(tableName = "cards", indices = { @Index(value = "pan_masked", unique = true) })
public class CardEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "pan_masked")
    public String panMasked;

    @ColumnInfo(name = "bin")
    public String bin;

    @ColumnInfo(name = "last4")
    public String last4;

    @ColumnInfo(name = "scheme")
    public String scheme;

    public CardEntity() {
    }

    public CardEntity(String panMasked, String bin, String last4, String scheme) {
        this.panMasked = panMasked;
        this.bin = bin;
        this.last4 = last4;
        this.scheme = scheme;
    }
}
