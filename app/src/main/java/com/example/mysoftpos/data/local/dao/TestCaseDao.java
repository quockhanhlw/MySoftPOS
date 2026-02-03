package com.example.mysoftpos.data.local.dao;
import com.example.mysoftpos.data.local.entity.TestCaseEntity;
import com.example.mysoftpos.data.local.dao.TestCaseDao;

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
    @Query("SELECT * FROM test_cases WHERE suite_id = :suiteId ORDER BY timestamp DESC")
    LiveData<List<TestCaseEntity>> getCasesBySuite(long suiteId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TestCaseEntity testCase);

    @Update
    void update(TestCaseEntity testCase);

    @Delete
    void delete(TestCaseEntity testCase);
}






