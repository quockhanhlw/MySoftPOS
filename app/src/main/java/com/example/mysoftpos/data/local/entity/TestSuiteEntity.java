package com.example.mysoftpos.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "test_suites")
public class TestSuiteEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Backend TestSuite.id for API sync */
    @ColumnInfo(name = "backend_id")
    public long backendId;

    /** Backend admin owner ID */
    @ColumnInfo(name = "admin_backend_id")
    public long adminBackendId;

    @ColumnInfo(name = "name")
    public String name; // e.g. "Full Regression", "Purchase Sanity"

    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public TestSuiteEntity() {
    }
}






