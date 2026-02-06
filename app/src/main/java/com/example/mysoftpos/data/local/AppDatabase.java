package com.example.mysoftpos.data.local;

import com.example.mysoftpos.data.local.dao.UserDao;
import com.example.mysoftpos.data.local.entity.UserEntity;
import com.example.mysoftpos.data.local.entity.TestCaseEntity;
import com.example.mysoftpos.data.local.dao.TransactionDao;
import com.example.mysoftpos.data.local.dao.TestCaseDao;
import com.example.mysoftpos.data.local.dao.TransactionTemplateDao;
import com.example.mysoftpos.data.local.dao.TestSuiteDao;
import com.example.mysoftpos.data.local.entity.TransactionTemplateEntity;
import com.example.mysoftpos.data.local.entity.TestSuiteEntity;
import com.example.mysoftpos.data.local.entity.TransactionEntity;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.mysoftpos.data.local.entity.*;
import com.example.mysoftpos.data.local.dao.*;

@Database(entities = {
        TransactionEntity.class,
        TransactionTemplateEntity.class,
        TestSuiteEntity.class,
        TestCaseEntity.class,
        UserEntity.class,
        MerchantEntity.class,
        TerminalEntity.class,
        CardEntity.class
}, version = 5, exportSchema = false) // Bumped version to 5 and destructive migration enabled below
public abstract class AppDatabase extends RoomDatabase {

    public abstract TransactionDao transactionDao();

    public abstract TransactionTemplateDao transactionTemplateDao();

    public abstract TestSuiteDao testSuiteDao();

    public abstract TestCaseDao testCaseDao();

    public abstract UserDao userDao();

    public abstract MerchantDao merchantDao();

    public abstract TerminalDao terminalDao();

    public abstract CardDao cardDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        return getDatabase(context);
    }

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "mysoftpos_db")
                            .fallbackToDestructiveMigration()
                            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
