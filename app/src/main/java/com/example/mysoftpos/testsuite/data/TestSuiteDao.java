package com.example.mysoftpos.testsuite.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.Transaction;

import com.example.mysoftpos.testsuite.model.TestCase;
import com.example.mysoftpos.testsuite.model.TestSuite;

import java.util.List;

@Dao
public interface TestSuiteDao {

    // Test Suites
    @Insert
    long insertTestSuite(TestSuite suite);

    @Update
    void updateTestSuite(TestSuite suite);

    @Delete
    void deleteTestSuite(TestSuite suite);

    @Query("SELECT * FROM test_suites ORDER BY created_at DESC")
    List<TestSuite> getAllTestSuites();

    @Query("SELECT * FROM test_suites WHERE id = :id")
    TestSuite getTestSuiteById(long id);

    // Test Cases
    @Insert
    void insertTestCase(TestCase testCase);

    @Update
    void updateTestCase(TestCase testCase);

    @Delete
    void deleteTestCase(TestCase testCase);

    @Query("SELECT * FROM test_cases WHERE suite_id = :suiteId")
    List<TestCase> getTestCasesForSuite(long suiteId);

    @Query("SELECT * FROM test_cases WHERE id = :id")
    TestCase getTestCaseById(String id);

    @Query("SELECT * FROM test_cases")
    List<TestCase> getAllTestCases();

    @Query("SELECT * FROM test_cases WHERE channel = :channel")
    List<TestCase> getTestCasesByChannel(String channel);

    @Query("SELECT * FROM test_cases WHERE channel = :channel AND mti = :mti AND processing_code = :procCode")
    List<TestCase> getTestCasesFiltered(String channel, String mti, String procCode);

    @Query("SELECT COUNT(*) FROM test_cases")
    int getTestCaseCount();
}
