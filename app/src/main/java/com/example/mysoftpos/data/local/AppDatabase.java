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
}, version = 16, exportSchema = false)
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

    /**
     * Migration 15 → 16:
     * - users: add backend_id (Long) for API mapping
     * - merchants: add backend_id, admin_backend_id for backend sync
     * - terminals: add backend_id, server_ip, server_port for backend sync
     * - test_suites: add backend_id, admin_backend_id for backend sync
     */
    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // users
            db.execSQL("ALTER TABLE users ADD COLUMN backend_id INTEGER NOT NULL DEFAULT 0");
            // merchants
            db.execSQL("ALTER TABLE merchants ADD COLUMN backend_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE merchants ADD COLUMN admin_backend_id INTEGER NOT NULL DEFAULT 0");
            // terminals
            db.execSQL("ALTER TABLE terminals ADD COLUMN backend_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE terminals ADD COLUMN server_ip TEXT");
            db.execSQL("ALTER TABLE terminals ADD COLUMN server_port INTEGER NOT NULL DEFAULT 0");
            // test_suites
            db.execSQL("ALTER TABLE test_suites ADD COLUMN backend_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE test_suites ADD COLUMN admin_backend_id INTEGER NOT NULL DEFAULT 0");
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
                            .addMigrations(MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
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
