package com.example.mysoftpos.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.mysoftpos.data.local.entity.*;
import com.example.mysoftpos.data.local.dao.*;

@Database(entities = {
        TransactionEntity.class,
        TestSuiteEntity.class,
        TestCaseEntity.class,
        UserEntity.class,
        MerchantEntity.class,
        TerminalEntity.class,
        CardEntity.class
}, version = 15, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract TransactionDao transactionDao();

    public abstract TestSuiteDao testSuiteDao();

    public abstract TestCaseDao testCaseDao();

    public abstract UserDao userDao();

    public abstract MerchantDao merchantDao();

    public abstract TerminalDao terminalDao();

    public abstract CardDao cardDao();

    // ──────────────────────────────────────────────────────────────────────────
    // MIGRATIONS – Thêm migration mới vào đây mỗi khi thay đổi schema.
    // KHÔNG bao giờ dùng fallbackToDestructiveMigration() trong app production.
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Migration 13 → 14:
     * Thêm cột field_config_json vào bảng test_cases
     * (cho phép mỗi test-case lưu cấu hình ISO field riêng).
     */
    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                    "ALTER TABLE test_cases ADD COLUMN field_config_json TEXT");
        }
    };

    /**
     * Migration 14 → 15:
     * PA-DSS 3.x: Add account lockout columns to users table.
     */
    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE users ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE users ADD COLUMN locked_until INTEGER NOT NULL DEFAULT 0");
        }
    };

    // ──────────────────────────────────────────────────────────────────────────
    // Singleton
    // ──────────────────────────────────────────────────────────────────────────

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        return getDatabase(context);
    }

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "mysoftpos_db")
                            // Liệt kê toàn bộ migration để Room nâng cấp schema
                            // mà KHÔNG xoá dữ liệu cũ.
                            .addMigrations(MIGRATION_13_14, MIGRATION_14_15)
                            // WAL (Write-Ahead Logging): cải thiện hiệu năng đọc/ghi
                            // đồng thời, thay thế TRUNCATE.
                            .setJournalMode(RoomDatabase.JournalMode.AUTOMATIC)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
