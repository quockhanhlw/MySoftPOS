package com.example.mysoftpos.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.mysoftpos.data.local.dao.ConfigurationDao;
import com.example.mysoftpos.data.local.entity.FieldConfiguration;
import com.example.mysoftpos.data.local.entity.TransactionType;
import com.example.mysoftpos.testsuite.data.TestResultDao;
import com.example.mysoftpos.testsuite.data.TestSuiteDao;
import com.example.mysoftpos.testsuite.model.TestCase;
import com.example.mysoftpos.testsuite.model.TestResult;
import com.example.mysoftpos.testsuite.model.TestSuite;

@Database(entities = {
        TransactionEntity.class,
        TestResult.class,
        TransactionType.class,
        FieldConfiguration.class,
        TestSuite.class,
        TestCase.class
}, version = 5, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract TransactionDao transactionDao();

    public abstract TestResultDao testResultDao();

    public abstract ConfigurationDao configurationDao();

    public abstract TestSuiteDao testSuiteDao();

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
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
