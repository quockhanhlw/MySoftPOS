package com.example.mysoftpos.testsuite.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.mysoftpos.testsuite.model.TestResult;
import java.util.List;

/**
 * Room DAO for TestResult entity.
 * Supports PENDING → SUCCESS/FAIL/TIMEOUT/ERROR flow.
 */
@Dao
public interface TestResultDao {
    
    /**
     * Insert new test result (PENDING status before network call).
     * @return The row ID of the inserted record.
     */
    @Insert
    long insert(TestResult result);
    
    /**
     * Update existing test result (after network call completes).
     * Used to update status from PENDING → SUCCESS/FAIL/TIMEOUT/ERROR.
     */
    @Update
    void update(TestResult result);
    
    @Query("SELECT * FROM test_results ORDER BY timestamp DESC LIMIT 100")
    List<TestResult> getRecentResults();
    
    @Query("SELECT * FROM test_results WHERE testCaseId = :testCaseId ORDER BY timestamp DESC LIMIT 1")
    TestResult getLastResultForTestCase(String testCaseId);
    
    @Query("SELECT * FROM test_results WHERE testCaseId = :testCaseId ORDER BY timestamp DESC")
    List<TestResult> getResultsForTestCase(String testCaseId);
    
    @Query("DELETE FROM test_results WHERE timestamp < :before")
    void deleteOldResults(long before);
    
    @Query("SELECT COUNT(*) FROM test_results WHERE testCaseId = :testCaseId AND status = 'SUCCESS'")
    int getPassCountForTestCase(String testCaseId);
    
    @Query("SELECT COUNT(*) FROM test_results WHERE testCaseId = :testCaseId")
    int getTotalCountForTestCase(String testCaseId);
    
    @Query("SELECT * FROM test_results WHERE id = :id")
    TestResult getById(long id);
}
