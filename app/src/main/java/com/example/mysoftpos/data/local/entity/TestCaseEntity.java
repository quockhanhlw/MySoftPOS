package com.example.mysoftpos.data.local.entity;
import com.example.mysoftpos.data.local.entity.TestCaseEntity;
import com.example.mysoftpos.data.local.entity.TestSuiteEntity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "test_cases", foreignKeys = @ForeignKey(entity = TestSuiteEntity.class, parentColumns = "id", childColumns = "suite_id", onDelete = ForeignKey.CASCADE), indices = {
        @Index("suite_id") })
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

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    public TestCaseEntity() {
    }
}






