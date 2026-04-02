package com.example.mysoftpos.data.local.dao;
import com.example.mysoftpos.data.local.dao.TestSuiteDao;
import com.example.mysoftpos.data.local.entity.TestSuiteEntity;

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

    @Query("SELECT * FROM test_suites ORDER BY created_at DESC")
    List<TestSuiteEntity> getAllSuitesSync();

    @Query("SELECT * FROM test_suites WHERE backend_id = :backendId LIMIT 1")
    TestSuiteEntity findByBackendIdSync(long backendId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TestSuiteEntity suite);

    @Query("SELECT * FROM test_suites WHERE name = :name LIMIT 1")
    TestSuiteEntity findByNameSync(String name);

    @Update
    void update(TestSuiteEntity suite);

    @Delete
    void delete(TestSuiteEntity suite);

    // For concurrency check pattern
    @Query("SELECT COUNT(*) FROM test_suites")
    int getCount();
}






