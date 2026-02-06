package com.example.mysoftpos.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions", foreignKeys = {
        @ForeignKey(entity = UserEntity.class, parentColumns = "id", childColumns = "user_id", onDelete = ForeignKey.SET_NULL),
        @ForeignKey(entity = CardEntity.class, parentColumns = "id", childColumns = "card_id", onDelete = ForeignKey.SET_NULL)
}, indices = {
        @Index(value = "trace_number", unique = true),
        @Index("user_id"),
        @Index("terminal_id"),
        @Index("card_id")
})
public class TransactionEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "trace_number")
    public String traceNumber;

    @ColumnInfo(name = "amount")
    public String amount;

    @ColumnInfo(name = "status")
    public String status;

    @ColumnInfo(name = "request_hex")
    public String requestHex;

    @ColumnInfo(name = "response_hex")
    public String responseHex;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "user_id")
    public Long userId;

    @ColumnInfo(name = "terminal_id")
    public Long terminalId;

    @ColumnInfo(name = "card_id")
    public Long cardId;

    public TransactionEntity() {
    }

    public void setRequestHex(String hex) {
        this.requestHex = hex;
    }

    public void setResponseHex(String hex) {
        this.responseHex = hex;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
