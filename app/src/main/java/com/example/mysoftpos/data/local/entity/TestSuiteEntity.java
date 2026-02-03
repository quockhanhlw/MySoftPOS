package com.example.mysoftpos.data.local.entity;
import com.example.mysoftpos.data.local.entity.TestSuiteEntity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "test_suites")
public class TestSuiteEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "name")
    public String name; // e.g. "Full Regression", "Purchase Sanity"

    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public TestSuiteEntity() {
    }
}






