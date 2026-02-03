package com.example.mysoftpos.di;

import android.content.Context;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.repository.TransactionRepository;
import com.example.mysoftpos.data.repository.TransactionRepositoryImpl;
import com.example.mysoftpos.utils.threading.DefaultDispatcherProvider;
import com.example.mysoftpos.utils.threading.DispatcherProvider;

public class ServiceLocator {

    private static ServiceLocator instance = null;
    private final DispatcherProvider dispatcherProvider;
    private final AppDatabase appDatabase;
    private TransactionRepository transactionRepository;
    private final android.app.Application application;

    private ServiceLocator(Context context) {
        this.application = (android.app.Application) context.getApplicationContext();
        this.dispatcherProvider = new DefaultDispatcherProvider();
        this.appDatabase = AppDatabase.getInstance(context);
    }

    public android.app.Application getApplication() {
        return application;
    }

    public static ServiceLocator getInstance(Context context) {
        if (instance == null) {
            synchronized (ServiceLocator.class) {
                if (instance == null) {
                    instance = new ServiceLocator(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public DispatcherProvider getDispatcherProvider() {
        return dispatcherProvider;
    }

    public AppDatabase getAppDatabase() {
        return appDatabase;
    }

    public TransactionRepository getTransactionRepository() {
        if (transactionRepository == null) {
            transactionRepository = new TransactionRepositoryImpl(appDatabase, dispatcherProvider);
        }
        return transactionRepository;
    }
}





