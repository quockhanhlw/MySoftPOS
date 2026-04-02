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
        PosAccountEntity.class,
        MerchantEntity.class,
        TerminalEntity.class,
        BranchEntity.class,
        CardEntity.class
}, version = 28, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    public abstract TransactionDao transactionDao();

    public abstract TestSuiteDao testSuiteDao();

    public abstract TestCaseDao testCaseDao();

    public abstract PosAccountDao posAccountDao();

    public abstract MerchantDao merchantDao();

    public abstract TerminalDao terminalDao();

    public abstract BranchDao branchDao();

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

    /**
     * Migration 16 → 17:
     * - users: add terminal_id_assigned (String) — the TID assigned to this user by admin
     */
    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE users ADD COLUMN terminal_id_assigned TEXT");
        }
    };

    /**
     * Migration 17 → 18:
     * Performance: Denormalize ISO fields into transactions table so the UI
     * never needs to hex-unpack requestHex/responseHex for list rendering.
     * - processing_code (DE 3): "000000" = Purchase, "300000" = Balance
     * - currency_code  (DE 49): "704" = VND, "840" = USD
     * - rrn            (DE 37): Retrieval Reference Number
     */
    static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN processing_code TEXT");
            db.execSQL("ALTER TABLE transactions ADD COLUMN currency_code TEXT");
            db.execSQL("ALTER TABLE transactions ADD COLUMN rrn TEXT");
        }
    };

    /**
     * Migration 18 → 19:
     * User self-registration profile fields + OTP verification state.
     */
    static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE users ADD COLUMN gender TEXT");
            db.execSQL("ALTER TABLE users ADD COLUMN store_name TEXT");
            db.execSQL("ALTER TABLE users ADD COLUMN business_type TEXT");
            db.execSQL("ALTER TABLE users ADD COLUMN store_address TEXT");
            db.execSQL("ALTER TABLE users ADD COLUMN phone_verified INTEGER NOT NULL DEFAULT 0");
        }
    };

    /**
     * Migration 19 -> 20:
     * Merchant profile ownership and registration data moved to merchants table.
     */
    static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE merchants ADD COLUMN owner_user_backend_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE merchants ADD COLUMN business_type TEXT");
            db.execSQL("ALTER TABLE merchants ADD COLUMN store_address TEXT");
        }
    };

    /**
     * Migration 20 -> 21:
     * User merchant profile extended with branch and account sizing.
     */
    static final Migration MIGRATION_20_21 = new Migration(20, 21) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE users ADD COLUMN branch_count INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE users ADD COLUMN branch_addresses TEXT");
            db.execSQL("ALTER TABLE users ADD COLUMN account_count INTEGER NOT NULL DEFAULT 1");
        }
    };

    /**
     * Migration 21 -> 22:
     * - Rename local users table to pos_accounts semantics.
     * - Move merchant-owned profile columns from users to merchants.
     */
    static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE merchants ADD COLUMN bank_name TEXT");
            db.execSQL("ALTER TABLE merchants ADD COLUMN branch_count INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE merchants ADD COLUMN branch_addresses TEXT");
            db.execSQL("ALTER TABLE merchants ADD COLUMN account_count INTEGER NOT NULL DEFAULT 1");

            db.execSQL("CREATE TABLE IF NOT EXISTS transactions_new ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "trace_number TEXT,"
                    + "amount TEXT,"
                    + "status TEXT,"
                    + "request_hex TEXT,"
                    + "response_hex TEXT,"
                    + "timestamp INTEGER NOT NULL,"
                    + "user_id INTEGER,"
                    + "owner_username TEXT,"
                    + "processing_code TEXT,"
                    + "currency_code TEXT,"
                    + "rrn TEXT,"
                    + "terminal_id INTEGER,"
                    + "card_id INTEGER,"
                    + "FOREIGN KEY(user_id) REFERENCES pos_accounts(id) ON UPDATE NO ACTION ON DELETE SET NULL,"
                    + "FOREIGN KEY(card_id) REFERENCES cards(id) ON UPDATE NO ACTION ON DELETE SET NULL)");

            db.execSQL("INSERT INTO transactions_new ("
                    + "id, trace_number, amount, status, request_hex, response_hex, timestamp,"
                    + "user_id, owner_username, processing_code, currency_code, rrn, terminal_id, card_id"
                    + ") SELECT "
                    + "id, trace_number, amount, status, request_hex, response_hex, timestamp,"
                    + "user_id, owner_username, processing_code, currency_code, rrn, terminal_id, card_id "
                    + "FROM transactions");

            db.execSQL("CREATE TABLE IF NOT EXISTS pos_accounts ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "username_hash TEXT,"
                    + "password_hash TEXT,"
                    + "display_name TEXT,"
                    + "email TEXT,"
                    + "phone TEXT,"
                    + "dob TEXT,"
                    + "gender TEXT,"
                    + "merchant_backend_id INTEGER NOT NULL DEFAULT 0,"
                    + "branch_backend_id INTEGER NOT NULL DEFAULT 0,"
                    + "phone_verified INTEGER NOT NULL DEFAULT 0,"
                    + "role TEXT,"
                    + "created_at INTEGER NOT NULL,"
                    + "admin_id TEXT,"
                    + "backend_id INTEGER NOT NULL,"
                    + "terminal_id_assigned TEXT,"
                    + "server_ip TEXT,"
                    + "server_port INTEGER NOT NULL,"
                    + "failed_login_attempts INTEGER NOT NULL DEFAULT 0,"
                    + "locked_until INTEGER NOT NULL DEFAULT 0)");

            db.execSQL("INSERT INTO pos_accounts ("
                    + "id, username_hash, password_hash, display_name, email, phone, dob, gender,"
                    + "merchant_backend_id, branch_backend_id, phone_verified, role, created_at, admin_id, backend_id,"
                    + "terminal_id_assigned, server_ip, server_port, failed_login_attempts, locked_until"
                    + ") "
                    + "SELECT id, username_hash, password_hash, display_name, email, phone, dob, gender,"
                    + "0, 0, phone_verified, role, created_at, admin_id, backend_id,"
                    + "terminal_id_assigned, server_ip, server_port, failed_login_attempts, locked_until "
                    + "FROM users");

            db.execSQL("DROP TABLE transactions");
            db.execSQL("ALTER TABLE transactions_new RENAME TO transactions");
            db.execSQL("DROP TABLE users");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_trace_number ON transactions(trace_number)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_user_id ON transactions(user_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_terminal_id ON transactions(terminal_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_card_id ON transactions(card_id)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pos_accounts_username_hash ON pos_accounts(username_hash)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pos_accounts_phone ON pos_accounts(phone)");
        }
    };

    /**
     * Migration 22 -> 23:
     * - pos_accounts: add plain-text username for backend parity.
     * - branches: add local branch cache table mapped by backend IDs.
     * - terminals: add branch_backend_id and pos_account_backend_id references from backend.
     * - transactions: add backend summary columns while keeping local hex payload.
     */
    static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE pos_accounts ADD COLUMN username TEXT");
            db.execSQL("UPDATE pos_accounts SET username = phone "
                    + "WHERE (username IS NULL OR TRIM(username)='') "
                    + "AND phone IS NOT NULL");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pos_accounts_username ON pos_accounts(username)");

            db.execSQL("CREATE TABLE IF NOT EXISTS branches ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "backend_id INTEGER NOT NULL,"
                    + "merchant_backend_id INTEGER NOT NULL,"
                    + "branch_code TEXT,"
                    + "branch_name TEXT,"
                    + "branch_address TEXT,"
                    + "created_at INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_branches_backend_id ON branches(backend_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_branches_merchant_backend_id "
                    + "ON branches(merchant_backend_id)");

            db.execSQL("ALTER TABLE terminals ADD COLUMN branch_backend_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE terminals ADD COLUMN pos_account_backend_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_terminals_branch_backend_id ON terminals(branch_backend_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_terminals_pos_account_backend_id ON terminals(pos_account_backend_id)");

            db.execSQL("ALTER TABLE transactions ADD COLUMN masked_pan TEXT");
            db.execSQL("ALTER TABLE transactions ADD COLUMN card_scheme TEXT");
            db.execSQL("ALTER TABLE transactions ADD COLUMN terminal_code TEXT");
            db.execSQL("ALTER TABLE transactions ADD COLUMN device_id TEXT");
            db.execSQL("ALTER TABLE transactions ADD COLUMN synced_at TEXT");
            db.execSQL("ALTER TABLE transactions ADD COLUMN backend_user_id INTEGER");
            db.execSQL("ALTER TABLE transactions ADD COLUMN backend_username TEXT");
        }
    };

    /**
     * Migration 23 -> 24:
     * Security + normalization cleanup.
     * - test_cases: drop pan/track2, keep masked_pan.
     * - transactions: drop owner_username/terminal_code.
     * - pos_accounts: drop password_hash/server_ip/server_port.
     * - merchants: drop branch_count/branch_addresses/account_count.
     */
    static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS test_cases_new ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "suite_id INTEGER NOT NULL,"
                    + "name TEXT,"
                    + "transaction_type TEXT,"
                    + "status TEXT,"
                    + "req_file_path TEXT,"
                    + "res_file_path TEXT,"
                    + "amount TEXT,"
                    + "de22 TEXT,"
                    + "masked_pan TEXT,"
                    + "expiry TEXT,"
                    + "scheme TEXT,"
                    + "timestamp INTEGER NOT NULL,"
                    + "field_config_json TEXT)");
            db.execSQL("INSERT INTO test_cases_new (id,suite_id,name,transaction_type,status,req_file_path,res_file_path,amount,de22,masked_pan,expiry,scheme,timestamp,field_config_json) "
                    + "SELECT id,suite_id,name,transaction_type,status,req_file_path,res_file_path,amount,de22,pan,expiry,scheme,timestamp,field_config_json FROM test_cases");
            db.execSQL("DROP TABLE test_cases");
            db.execSQL("ALTER TABLE test_cases_new RENAME TO test_cases");

            db.execSQL("CREATE TABLE IF NOT EXISTS transactions_new ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "trace_number TEXT,"
                    + "amount TEXT,"
                    + "status TEXT,"
                    + "request_hex TEXT,"
                    + "response_hex TEXT,"
                    + "timestamp INTEGER NOT NULL,"
                    + "user_id INTEGER,"
                    + "processing_code TEXT,"
                    + "currency_code TEXT,"
                    + "rrn TEXT,"
                    + "terminal_id INTEGER,"
                    + "card_id INTEGER,"
                    + "masked_pan TEXT,"
                    + "card_scheme TEXT,"
                    + "device_id TEXT,"
                    + "synced_at TEXT,"
                    + "backend_user_id INTEGER,"
                    + "backend_username TEXT,"
                    + "FOREIGN KEY(user_id) REFERENCES pos_accounts(id) ON UPDATE NO ACTION ON DELETE SET NULL,"
                    + "FOREIGN KEY(card_id) REFERENCES cards(id) ON UPDATE NO ACTION ON DELETE SET NULL)");
            db.execSQL("INSERT INTO transactions_new (id,trace_number,amount,status,request_hex,response_hex,timestamp,user_id,processing_code,currency_code,rrn,terminal_id,card_id,masked_pan,card_scheme,device_id,synced_at,backend_user_id,backend_username) "
                    + "SELECT id,trace_number,amount,status,request_hex,response_hex,timestamp,user_id,processing_code,currency_code,rrn,terminal_id,card_id,masked_pan,card_scheme,device_id,synced_at,backend_user_id,backend_username FROM transactions");
            db.execSQL("DROP TABLE transactions");
            db.execSQL("ALTER TABLE transactions_new RENAME TO transactions");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_trace_number ON transactions(trace_number)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_user_id ON transactions(user_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_terminal_id ON transactions(terminal_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_card_id ON transactions(card_id)");

            db.execSQL("CREATE TABLE IF NOT EXISTS pos_accounts_new ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "username_hash TEXT,"
                    + "username TEXT,"
                    + "display_name TEXT,"
                    + "email TEXT,"
                    + "phone TEXT,"
                    + "dob TEXT,"
                    + "gender TEXT,"
                    + "merchant_backend_id INTEGER NOT NULL DEFAULT 0,"
                    + "branch_backend_id INTEGER NOT NULL DEFAULT 0,"
                    + "phone_verified INTEGER NOT NULL DEFAULT 0,"
                    + "role TEXT,"
                    + "created_at INTEGER NOT NULL,"
                    + "admin_id TEXT,"
                    + "backend_id INTEGER NOT NULL,"
                    + "terminal_id_assigned TEXT,"
                    + "failed_login_attempts INTEGER NOT NULL DEFAULT 0,"
                    + "locked_until INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("INSERT INTO pos_accounts_new (id,username_hash,username,display_name,email,phone,dob,gender,merchant_backend_id,branch_backend_id,phone_verified,role,created_at,admin_id,backend_id,terminal_id_assigned,failed_login_attempts,locked_until) "
                    + "SELECT id,username_hash,username,display_name,email,phone,dob,gender,merchant_backend_id,branch_backend_id,phone_verified,role,created_at,admin_id,backend_id,terminal_id_assigned,failed_login_attempts,locked_until FROM pos_accounts");
            db.execSQL("DROP TABLE pos_accounts");
            db.execSQL("ALTER TABLE pos_accounts_new RENAME TO pos_accounts");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pos_accounts_username ON pos_accounts(username)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pos_accounts_username_hash ON pos_accounts(username_hash)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pos_accounts_phone ON pos_accounts(phone)");

            db.execSQL("CREATE TABLE IF NOT EXISTS merchants_new ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "backend_id INTEGER NOT NULL,"
                    + "admin_backend_id INTEGER NOT NULL,"
                    + "owner_user_backend_id INTEGER NOT NULL,"
                    + "business_type TEXT,"
                    + "store_address TEXT,"
                    + "bank_name TEXT,"
                    + "merchant_code TEXT,"
                    + "merchant_name_location TEXT)");
            db.execSQL("INSERT INTO merchants_new (id,backend_id,admin_backend_id,owner_user_backend_id,business_type,store_address,bank_name,merchant_code,merchant_name_location) "
                    + "SELECT id,backend_id,admin_backend_id,owner_user_backend_id,business_type,store_address,bank_name,merchant_code,merchant_name_location FROM merchants");
            db.execSQL("DROP TABLE merchants");
            db.execSQL("ALTER TABLE merchants_new RENAME TO merchants");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_merchants_merchant_code ON merchants(merchant_code)");
        }
    };

    /**
     * Migration 24 -> 25:
     * Column name alignment with backend schema.
     * - pos_accounts.display_name -> full_name
     * - merchants.merchant_name_location -> merchant_name
     * - transactions.timestamp -> txn_timestamp
     * - test_cases.timestamp -> created_at
     */
    static final Migration MIGRATION_24_25 = new Migration(24, 25) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS pos_accounts_new ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "username_hash TEXT,"
                    + "username TEXT,"
                    + "full_name TEXT,"
                    + "email TEXT,"
                    + "phone TEXT,"
                    + "dob TEXT,"
                    + "gender TEXT,"
                    + "merchant_backend_id INTEGER NOT NULL DEFAULT 0,"
                    + "branch_backend_id INTEGER NOT NULL DEFAULT 0,"
                    + "phone_verified INTEGER NOT NULL DEFAULT 0,"
                    + "role TEXT,"
                    + "created_at INTEGER NOT NULL,"
                    + "admin_id TEXT,"
                    + "backend_id INTEGER NOT NULL,"
                    + "terminal_id_assigned TEXT,"
                    + "failed_login_attempts INTEGER NOT NULL DEFAULT 0,"
                    + "locked_until INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("INSERT INTO pos_accounts_new (id,username_hash,username,full_name,email,phone,dob,gender,merchant_backend_id,branch_backend_id,phone_verified,role,created_at,admin_id,backend_id,terminal_id_assigned,failed_login_attempts,locked_until) "
                    + "SELECT id,username_hash,username,display_name,email,phone,dob,gender,merchant_backend_id,branch_backend_id,phone_verified,role,created_at,admin_id,backend_id,terminal_id_assigned,failed_login_attempts,locked_until FROM pos_accounts");
            db.execSQL("DROP TABLE pos_accounts");
            db.execSQL("ALTER TABLE pos_accounts_new RENAME TO pos_accounts");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pos_accounts_username ON pos_accounts(username)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pos_accounts_username_hash ON pos_accounts(username_hash)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pos_accounts_phone ON pos_accounts(phone)");

            db.execSQL("CREATE TABLE IF NOT EXISTS merchants_new ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "backend_id INTEGER NOT NULL,"
                    + "admin_backend_id INTEGER NOT NULL,"
                    + "owner_user_backend_id INTEGER NOT NULL,"
                    + "business_type TEXT,"
                    + "store_address TEXT,"
                    + "bank_name TEXT,"
                    + "merchant_code TEXT,"
                    + "merchant_name TEXT)");
            db.execSQL("INSERT INTO merchants_new (id,backend_id,admin_backend_id,owner_user_backend_id,business_type,store_address,bank_name,merchant_code,merchant_name) "
                    + "SELECT id,backend_id,admin_backend_id,owner_user_backend_id,business_type,store_address,bank_name,merchant_code,merchant_name_location FROM merchants");
            db.execSQL("DROP TABLE merchants");
            db.execSQL("ALTER TABLE merchants_new RENAME TO merchants");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_merchants_merchant_code ON merchants(merchant_code)");

            db.execSQL("CREATE TABLE IF NOT EXISTS transactions_new ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "trace_number TEXT,"
                    + "amount TEXT,"
                    + "status TEXT,"
                    + "request_hex TEXT,"
                    + "response_hex TEXT,"
                    + "txn_timestamp INTEGER NOT NULL,"
                    + "user_id INTEGER,"
                    + "processing_code TEXT,"
                    + "currency_code TEXT,"
                    + "rrn TEXT,"
                    + "terminal_id INTEGER,"
                    + "card_id INTEGER,"
                    + "masked_pan TEXT,"
                    + "card_scheme TEXT,"
                    + "device_id TEXT,"
                    + "synced_at TEXT,"
                    + "backend_user_id INTEGER,"
                    + "backend_username TEXT,"
                    + "FOREIGN KEY(user_id) REFERENCES pos_accounts(id) ON UPDATE NO ACTION ON DELETE SET NULL,"
                    + "FOREIGN KEY(card_id) REFERENCES cards(id) ON UPDATE NO ACTION ON DELETE SET NULL)");
            db.execSQL("INSERT INTO transactions_new (id,trace_number,amount,status,request_hex,response_hex,txn_timestamp,user_id,processing_code,currency_code,rrn,terminal_id,card_id,masked_pan,card_scheme,device_id,synced_at,backend_user_id,backend_username) "
                    + "SELECT id,trace_number,amount,status,request_hex,response_hex,timestamp,user_id,processing_code,currency_code,rrn,terminal_id,card_id,masked_pan,card_scheme,device_id,synced_at,backend_user_id,backend_username FROM transactions");
            db.execSQL("DROP TABLE transactions");
            db.execSQL("ALTER TABLE transactions_new RENAME TO transactions");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_trace_number ON transactions(trace_number)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_user_id ON transactions(user_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_terminal_id ON transactions(terminal_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_card_id ON transactions(card_id)");

            db.execSQL("CREATE TABLE IF NOT EXISTS test_cases_new ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "suite_id INTEGER NOT NULL,"
                    + "name TEXT,"
                    + "transaction_type TEXT,"
                    + "status TEXT,"
                    + "req_file_path TEXT,"
                    + "res_file_path TEXT,"
                    + "amount TEXT,"
                    + "de22 TEXT,"
                    + "masked_pan TEXT,"
                    + "expiry TEXT,"
                    + "scheme TEXT,"
                    + "created_at INTEGER NOT NULL,"
                    + "field_config_json TEXT)");
            db.execSQL("INSERT INTO test_cases_new (id,suite_id,name,transaction_type,status,req_file_path,res_file_path,amount,de22,masked_pan,expiry,scheme,created_at,field_config_json) "
                    + "SELECT id,suite_id,name,transaction_type,status,req_file_path,res_file_path,amount,de22,masked_pan,expiry,scheme,timestamp,field_config_json FROM test_cases");
            db.execSQL("DROP TABLE test_cases");
            db.execSQL("ALTER TABLE test_cases_new RENAME TO test_cases");
        }
    };

    /**
     * Migration 25 -> 26:
     * - pos_accounts keeps account-only fields (username, role, links, lock status...)
     * - merchant profile/contact fields move to merchants table.
     */
    static final Migration MIGRATION_25_26 = new Migration(25, 26) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS merchants_new ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "backend_id INTEGER NOT NULL,"
                    + "admin_backend_id INTEGER NOT NULL,"
                    + "owner_user_backend_id INTEGER NOT NULL,"
                    + "full_name TEXT,"
                    + "phone TEXT,"
                    + "email TEXT,"
                    + "dob TEXT,"
                    + "gender TEXT,"
                    + "business_type TEXT,"
                    + "store_address TEXT,"
                    + "bank_name TEXT,"
                    + "merchant_code TEXT,"
                    + "merchant_name TEXT)");
            db.execSQL("INSERT INTO merchants_new (id,backend_id,admin_backend_id,owner_user_backend_id,full_name,phone,email,dob,gender,business_type,store_address,bank_name,merchant_code,merchant_name) "
                    + "SELECT id,backend_id,admin_backend_id,owner_user_backend_id,NULL,NULL,NULL,NULL,NULL,business_type,store_address,bank_name,merchant_code,merchant_name FROM merchants");
            db.execSQL("DROP TABLE merchants");
            db.execSQL("ALTER TABLE merchants_new RENAME TO merchants");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_merchants_merchant_code ON merchants(merchant_code)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_merchants_owner_user_backend_id ON merchants(owner_user_backend_id)");

            db.execSQL("UPDATE merchants SET full_name = (SELECT full_name FROM pos_accounts pa WHERE pa.backend_id = merchants.owner_user_backend_id LIMIT 1) "
                    + "WHERE full_name IS NULL OR TRIM(full_name)=''");
            db.execSQL("UPDATE merchants SET phone = (SELECT SUBSTR(username,1,LENGTH(username)-1) FROM pos_accounts pa WHERE pa.backend_id = merchants.owner_user_backend_id LIMIT 1) "
                    + "WHERE (phone IS NULL OR TRIM(phone)='') AND owner_user_backend_id > 0");
            db.execSQL("UPDATE merchants SET email = (SELECT email FROM pos_accounts pa WHERE pa.backend_id = merchants.owner_user_backend_id LIMIT 1) "
                    + "WHERE email IS NULL OR TRIM(email)=''");
            db.execSQL("UPDATE merchants SET dob = (SELECT dob FROM pos_accounts pa WHERE pa.backend_id = merchants.owner_user_backend_id LIMIT 1) "
                    + "WHERE dob IS NULL OR TRIM(dob)=''");
            db.execSQL("UPDATE merchants SET gender = (SELECT gender FROM pos_accounts pa WHERE pa.backend_id = merchants.owner_user_backend_id LIMIT 1) "
                    + "WHERE gender IS NULL OR TRIM(gender)=''");

            db.execSQL("CREATE TABLE IF NOT EXISTS pos_accounts_new ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "username_hash TEXT,"
                    + "username TEXT,"
                    + "merchant_backend_id INTEGER NOT NULL DEFAULT 0,"
                    + "branch_backend_id INTEGER NOT NULL DEFAULT 0,"
                    + "phone_verified INTEGER NOT NULL DEFAULT 0,"
                    + "role TEXT,"
                    + "created_at INTEGER NOT NULL,"
                    + "admin_id TEXT,"
                    + "backend_id INTEGER NOT NULL,"
                    + "terminal_id_assigned TEXT,"
                    + "failed_login_attempts INTEGER NOT NULL DEFAULT 0,"
                    + "locked_until INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("INSERT INTO pos_accounts_new (id,username_hash,username,merchant_backend_id,branch_backend_id,phone_verified,role,created_at,admin_id,backend_id,terminal_id_assigned,failed_login_attempts,locked_until) "
                    + "SELECT id,username_hash,username,merchant_backend_id,branch_backend_id,phone_verified,role,created_at,admin_id,backend_id,terminal_id_assigned,failed_login_attempts,locked_until FROM pos_accounts");
            db.execSQL("DROP TABLE pos_accounts");
            db.execSQL("ALTER TABLE pos_accounts_new RENAME TO pos_accounts");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pos_accounts_username ON pos_accounts(username)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pos_accounts_username_hash ON pos_accounts(username_hash)");
        }
    };

    /**
     * Migration 26 -> 27:
     * Restore local credential cache column for offline login reliability.
     */
    static final Migration MIGRATION_26_27 = new Migration(26, 27) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE pos_accounts ADD COLUMN password_hash TEXT");
            db.execSQL("UPDATE pos_accounts SET password_hash = '' WHERE password_hash IS NULL");
        }
    };

    /**
     * Migration 27 -> 28:
     * - test_cases: add backend_id, is_default for API sync + immutable default marking.
     * - cards: add backend/owner mapping columns for backend card catalog sync.
     */
    static final Migration MIGRATION_27_28 = new Migration(27, 28) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE test_cases ADD COLUMN backend_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE test_cases ADD COLUMN is_default INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE cards ADD COLUMN backend_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE cards ADD COLUMN admin_backend_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE cards ADD COLUMN pos_account_backend_id INTEGER NOT NULL DEFAULT 0");
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
                            .addMigrations(MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                                    MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
                                    MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25,
                                    MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28)
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
