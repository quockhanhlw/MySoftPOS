package com.example.mysoftpos.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.mysoftpos.data.local.entity.TerminalEntity;

import java.util.List;

@Dao
public interface TerminalDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(TerminalEntity terminal);

    @Update
    void update(TerminalEntity terminal);

    @Query("SELECT * FROM terminals WHERE terminal_code = :code LIMIT 1")
    TerminalEntity getByCode(String code);

    @Query("SELECT * FROM terminals WHERE backend_id = :backendId LIMIT 1")
    TerminalEntity getByBackendId(long backendId);

    @Query("SELECT * FROM terminals ORDER BY id")
    List<TerminalEntity> getAll();

    @Query("DELETE FROM terminals")
    void deleteAll();
}
