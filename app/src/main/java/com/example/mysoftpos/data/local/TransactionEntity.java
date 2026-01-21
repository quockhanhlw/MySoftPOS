package com.example.mysoftpos.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions")
public class TransactionEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "trace_number")
    public String traceNumber; // Matches DE 11 (STAN)

    @ColumnInfo(name = "amount")
    public String amount; // DE 4

    @ColumnInfo(name = "pan")
    public String pan; // Masked PAN (DE 2)

    @ColumnInfo(name = "status")
    public String status; // PENDING, APPROVED, FAILED, VOIDED, REVERSED

    @ColumnInfo(name = "request_hex")
    public String requestHex; // ISO Request

    @ColumnInfo(name = "response_hex")
    public String responseHex; // ISO Response (Nullable)

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    public TransactionEntity(@NonNull String traceNumber, String amount, String pan, String status, String requestHex, long timestamp) {
        this.traceNumber = traceNumber;
        this.amount = amount;
        this.pan = pan;
        this.status = status;
        this.requestHex = requestHex;
        this.timestamp = timestamp;
    }
}
