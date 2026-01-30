package com.example.mysoftpos.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "field_configurations", foreignKeys = @ForeignKey(entity = TransactionType.class, parentColumns = "id", childColumns = "transaction_type_id", onDelete = ForeignKey.CASCADE), indices = {
        @Index("transaction_type_id") })
public class FieldConfiguration {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "transaction_type_id")
    public long transactionTypeId;

    @ColumnInfo(name = "field_id")
    public int fieldId; // e.g. 3, 4, 11, 22

    @ColumnInfo(name = "source_type")
    public String sourceType; // FIXED, AUTO, INPUT

    @ColumnInfo(name = "value")
    public String value; // Default value or template

    public FieldConfiguration(long transactionTypeId, int fieldId, String sourceType, String value) {
        this.transactionTypeId = transactionTypeId;
        this.fieldId = fieldId;
        this.sourceType = sourceType;
        this.value = value;
    }
}
