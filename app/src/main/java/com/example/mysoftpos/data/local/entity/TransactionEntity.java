package com.example.mysoftpos.data.local.entity;
import com.example.mysoftpos.data.local.entity.TransactionEntity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions")
public class TransactionEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "trace_number")
    public String traceNumber;
    
    @ColumnInfo(name = "amount")
    public String amount;
    
    @ColumnInfo(name = "pan")
    public String pan;
    
    @ColumnInfo(name = "status")
    public String status;
    
    @ColumnInfo(name = "request_hex")
    public String requestHex;
    
    @ColumnInfo(name = "response_hex")
    public String responseHex;
    
    @ColumnInfo(name = "timestamp")
    public long timestamp;

    public TransactionEntity() {
    }

    @Ignore
    public TransactionEntity(String traceNumber, String amount, String pan, String status, String requestHex, long timestamp) {
        this.traceNumber = traceNumber;
        this.amount = amount;
        this.pan = pan;
        this.status = status;
        this.requestHex = requestHex;
        this.timestamp = timestamp;
    }

    public void setRequestHex(String hex) { this.requestHex = hex; }
    public void setResponseHex(String hex) { this.responseHex = hex; }
    public void setStatus(String status) { this.status = status; }
}






