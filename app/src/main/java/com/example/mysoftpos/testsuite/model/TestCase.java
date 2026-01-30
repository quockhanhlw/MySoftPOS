package com.example.mysoftpos.testsuite.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Ignore;

import com.example.mysoftpos.data.local.entity.TransactionType;

@Entity(tableName = "test_cases", foreignKeys = {
        @ForeignKey(entity = TestSuite.class, parentColumns = "id", childColumns = "suite_id", onDelete = ForeignKey.SET_NULL),
        @ForeignKey(entity = TransactionType.class, parentColumns = "id", childColumns = "transaction_type_id", onDelete = ForeignKey.SET_NULL)
}, indices = {
        @Index("suite_id"),
        @Index("transaction_type_id"),
        @Index("scenario_name"),
        @Index("de22_mode")
})
public class TestCase {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;

    @ColumnInfo(name = "suite_id")
    private Long suiteId;

    @ColumnInfo(name = "transaction_type_id")
    private Long transactionTypeId;

    @ColumnInfo(name = "scenario_name") // Mapped from name
    private String name;

    @ColumnInfo(name = "description")
    private String description;

    @ColumnInfo(name = "mti")
    private String mti;

    @ColumnInfo(name = "processing_code")
    private String processingCode;

    @ColumnInfo(name = "de22_mode") // Mapped from pos_entry_mode
    private String posEntryMode;

    @ColumnInfo(name = "channel")
    private String channel;

    @ColumnInfo(name = "requires_amount")
    private boolean requiresAmount;

    @ColumnInfo(name = "requires_pin")
    private boolean requiresPIN;

    @ColumnInfo(name = "request_template_path")
    private String requestTemplatePath;

    @ColumnInfo(name = "expected_response_path")
    private String expectedResponsePath;

    public TestCase(String id, String name, String description, String mti,
            String processingCode, String posEntryMode, String channel,
            boolean requiresAmount, boolean requiresPIN, Long suiteId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.mti = mti;
        this.processingCode = processingCode;
        this.posEntryMode = posEntryMode;
        this.channel = channel;
        this.requiresAmount = requiresAmount;
        this.requiresPIN = requiresPIN;
        this.suiteId = suiteId;
    }

    @Ignore
    public TestCase(String id, String name, String description, String mti,
            String processingCode, String posEntryMode, String channel,
            boolean requiresAmount, boolean requiresPIN) {
        this(id, name, description, mti, processingCode, posEntryMode, channel, requiresAmount, requiresPIN, null);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getSuiteId() {
        return suiteId;
    }

    public void setSuiteId(Long suiteId) {
        this.suiteId = suiteId;
    }

    public Long getTransactionTypeId() {
        return transactionTypeId;
    }

    public void setTransactionTypeId(Long transactionTypeId) {
        this.transactionTypeId = transactionTypeId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getMti() {
        return mti;
    }

    public String getProcessingCode() {
        return processingCode;
    }

    public String getPosEntryMode() {
        return posEntryMode;
    }

    public String getChannel() {
        return channel;
    }

    public boolean isRequiresAmount() {
        return requiresAmount;
    }

    public boolean isRequiresPIN() {
        return requiresPIN;
    }

    public String getRequestTemplatePath() {
        return requestTemplatePath;
    }

    public void setRequestTemplatePath(String path) {
        this.requestTemplatePath = path;
    }

    public String getExpectedResponsePath() {
        return expectedResponsePath;
    }

    public void setExpectedResponsePath(String path) {
        this.expectedResponsePath = path;
    }

    public String getDisplayName() {
        return channel + " - " + name;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
