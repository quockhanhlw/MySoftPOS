package com.example.mysoftpos.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.example.mysoftpos.data.local.entity.TerminalEntity;

@Dao
public interface TerminalDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(TerminalEntity terminal);

    @Query("SELECT * FROM terminals WHERE terminal_code = :code LIMIT 1")
    TerminalEntity getByCode(String code);
}
