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

    private com.example.mysoftpos.data.remote.IsoNetworkClient isoNetworkClient;
    private com.example.mysoftpos.domain.service.TransactionExecutor transactionExecutor;

    public com.example.mysoftpos.data.remote.IsoNetworkClient getIsoNetworkClient() {
        if (isoNetworkClient == null) {
            isoNetworkClient = new com.example.mysoftpos.data.remote.IsoNetworkClient();
        }
        return isoNetworkClient;
    }

    public com.example.mysoftpos.domain.service.TransactionExecutor getTransactionExecutor() {
        if (transactionExecutor == null) {
            transactionExecutor = new com.example.mysoftpos.domain.service.TransactionExecutor(getIsoNetworkClient());
        }
        return transactionExecutor;
    }

    public TransactionRepository getTransactionRepository() {
        if (transactionRepository == null) {
            transactionRepository = new TransactionRepositoryImpl(appDatabase, dispatcherProvider);
        }
        return transactionRepository;
    }
}
