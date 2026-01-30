package com.example.mysoftpos.testsuite.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "test_suites")
public class TestSuite {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public TestSuite(String name, String description, long createdAt) {
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }
}
