package com.example.mysoftpos.data.local.dao;

import com.example.mysoftpos.data.local.entity.TestCaseEntity;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TestCaseDao {
    @Query("SELECT * FROM test_cases WHERE suite_id = :suiteId ORDER BY created_at DESC")
    LiveData<List<TestCaseEntity>> getCasesBySuite(long suiteId);

    @Query("SELECT * FROM test_cases WHERE suite_id = :suiteId ORDER BY created_at DESC")
    List<TestCaseEntity> getCasesBySuiteSync(long suiteId);

    @Query("SELECT * FROM test_cases WHERE backend_id = :backendId LIMIT 1")
    TestCaseEntity findByBackendIdSync(long backendId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TestCaseEntity testCase);

    @Update
    void update(TestCaseEntity testCase);

    @Delete
    void delete(TestCaseEntity testCase);

    @Query("SELECT * FROM test_cases WHERE transaction_type = :type AND suite_id = -1 ORDER BY created_at DESC")
    LiveData<List<TestCaseEntity>> getCustomCasesByType(String type);

    @Query("SELECT * FROM test_cases WHERE scheme = :scheme AND transaction_type = :type AND suite_id = -1 ORDER BY created_at DESC")
    LiveData<List<TestCaseEntity>> getCustomCasesBySchemeAndType(String scheme, String type);

    @Query("SELECT * FROM test_cases WHERE scheme = :scheme AND suite_id = -1 ORDER BY created_at DESC")
    LiveData<List<TestCaseEntity>> getCustomCasesByScheme(String scheme);

    @Query("DELETE FROM test_cases WHERE suite_id = :suiteId")
    void deleteBySuiteId(long suiteId);

    @Query("SELECT COUNT(*) FROM test_cases")
    int getCount();
}
