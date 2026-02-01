package com.example.mysoftpos.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TestSuiteDao {
    @Query("SELECT * FROM test_suites ORDER BY created_at DESC")
    LiveData<List<TestSuiteEntity>> getAllSuites();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TestSuiteEntity suite);

    @Update
    void update(TestSuiteEntity suite);

    @Delete
    void delete(TestSuiteEntity suite);

    // For concurrency check pattern
    @Query("SELECT COUNT(*) FROM test_suites")
    int getCount();
}
