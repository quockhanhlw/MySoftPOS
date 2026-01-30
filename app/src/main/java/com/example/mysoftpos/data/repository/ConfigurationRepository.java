package com.example.mysoftpos.data.repository;

import android.content.Context;

import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.dao.ConfigurationDao;
import com.example.mysoftpos.data.local.entity.FieldConfiguration;
import com.example.mysoftpos.data.local.entity.TransactionType;

import java.util.List;
import com.example.mysoftpos.utils.AppExecutors;
import java.util.concurrent.Executor;

public class ConfigurationRepository {

    private final ConfigurationDao configDao;
    private final Executor executor;

    public ConfigurationRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.configDao = db.configurationDao();
        this.executor = AppExecutors.getInstance().diskIO();
    }

    // Transaction Types
    public void getAllTransactionTypes(DataCallback<List<TransactionType>> callback) {
        executor.execute(() -> {
            try {
                List<TransactionType> types = configDao.getAllTransactionTypes();
                callback.onSuccess(types);
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    public void insertTransactionType(TransactionType type, DataCallback<Long> callback) {
        executor.execute(() -> {
            try {
                long id = configDao.insertTransactionType(type);
                callback.onSuccess(id);
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    // Field Configurations
    public void getFieldConfigsForType(long typeId, DataCallback<List<FieldConfiguration>> callback) {
        executor.execute(() -> {
            try {
                List<FieldConfiguration> configs = configDao.getFieldConfigsForType(typeId);
                callback.onSuccess(configs);
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    public void saveFieldConfigs(List<FieldConfiguration> configs, DataCallback<Boolean> callback) {
        executor.execute(() -> {
            try {
                if (!configs.isEmpty()) {
                    long typeId = configs.get(0).transactionTypeId;
                    configDao.deleteFieldConfigsForType(typeId); // Clear old
                    configDao.insertFieldConfigs(configs);
                }
                callback.onSuccess(true);
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    // Synchronous fetch for Builder logic (run on background thread)
    public List<FieldConfiguration> getFieldConfigsForTypeSync(long typeId) {
        return configDao.getFieldConfigsForType(typeId);
    }

    public TransactionType getTransactionTypeByNameSync(String name) {
        return configDao.getTransactionTypeByName(name);
    }

    public interface DataCallback<T> {
        void onSuccess(T data);

        void onError(String error);
    }
}
