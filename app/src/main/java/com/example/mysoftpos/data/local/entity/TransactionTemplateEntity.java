package com.example.mysoftpos.data.local.entity;
import com.example.mysoftpos.data.local.entity.TransactionTemplateEntity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "transaction_templates")
public class TransactionTemplateEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "name")
    public String name; // e.g. "Napas Purchase", "Visa Balance"

    @ColumnInfo(name = "mti")
    public String mti; // e.g. "0200"

    /**
     * JSON definition of field rules.
     * Example:
     * {
     * "3": {"type": "FIXED", "value": "000000"},
     * "4": {"type": "INPUT"},
     * "11": {"type": "AUTO"}
     * }
     */
    @ColumnInfo(name = "field_config_json")
    public String fieldConfigJson;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public TransactionTemplateEntity() {
    }
}






