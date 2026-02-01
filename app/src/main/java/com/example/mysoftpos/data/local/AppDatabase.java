package com.example.mysoftpos.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {
        TransactionEntity.class,
        TransactionTemplateEntity.class,
        TestSuiteEntity.class,
        TestCaseEntity.class
}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract TransactionDao transactionDao();

    public abstract TransactionTemplateDao transactionTemplateDao(); // New

    public abstract TestSuiteDao testSuiteDao(); // New

    public abstract TestCaseDao testCaseDao(); // New

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
                            .fallbackToDestructiveMigration() // Dev only: Wipes DB on version change
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
