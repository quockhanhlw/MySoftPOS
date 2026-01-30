package com.example.mysoftpos.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Delete;
import androidx.room.Update;

import com.example.mysoftpos.data.local.entity.FieldConfiguration;
import com.example.mysoftpos.data.local.entity.TransactionType;

import java.util.List;

@Dao
public interface ConfigurationDao {

    // Transaction Types
    @Insert
    long insertTransactionType(TransactionType type);

    @Update
    void updateTransactionType(TransactionType type);

    @Delete
    void deleteTransactionType(TransactionType type);

    @Query("SELECT * FROM transaction_types")
    List<TransactionType> getAllTransactionTypes();

    @Query("SELECT * FROM transaction_types WHERE id = :id")
    TransactionType getTransactionTypeById(long id);

    @Query("SELECT * FROM transaction_types WHERE name = :name")
    TransactionType getTransactionTypeByName(String name);

    // Field Configurations
    @Insert
    void insertFieldConfig(FieldConfiguration config);

    @Insert
    void insertFieldConfigs(List<FieldConfiguration> configs);

    @Update
    void updateFieldConfig(FieldConfiguration config);

    @Delete
    void deleteFieldConfig(FieldConfiguration config);

    @Query("SELECT * FROM field_configurations WHERE transaction_type_id = :typeId")
    List<FieldConfiguration> getFieldConfigsForType(long typeId);

    @Query("DELETE FROM field_configurations WHERE transaction_type_id = :typeId")
    void deleteFieldConfigsForType(long typeId);
}
