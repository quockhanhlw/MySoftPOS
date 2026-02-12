package com.example.mysoftpos.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "test_cases")
public class TestCaseEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "suite_id")
    public long suiteId;

    @ColumnInfo(name = "name")
    public String name; // e.g. "Case 1: Valid Purchase"

    @ColumnInfo(name = "transaction_type")
    public String transactionType; // "PURCHASE", "BALANCE"

    @ColumnInfo(name = "status")
    public String status; // "PASS", "FAIL", "PENDING"

    @ColumnInfo(name = "req_file_path")
    public String requestFilePath; // Path to saved .bin/.iso file

    @ColumnInfo(name = "res_file_path")
    public String responseFilePath; // Path to saved .bin/.iso file

    @ColumnInfo(name = "amount")
    public String amount;

    @ColumnInfo(name = "de22")
    public String de22;

    @ColumnInfo(name = "pan")
    public String pan;

    @ColumnInfo(name = "expiry")
    public String expiry;

    @ColumnInfo(name = "track2")
    public String track2;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    public TestCaseEntity() {
    }
}
