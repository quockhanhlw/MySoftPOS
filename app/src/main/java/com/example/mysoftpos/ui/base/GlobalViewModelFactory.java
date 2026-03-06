package com.example.mysoftpos.ui.base;

import com.example.mysoftpos.utils.config.ConfigManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.mysoftpos.di.ServiceLocator;

// We will add more ViewModels here as we refactor
public class GlobalViewModelFactory implements ViewModelProvider.Factory {

    private final ServiceLocator serviceLocator;

    public GlobalViewModelFactory(ServiceLocator serviceLocator) {
        this.serviceLocator = serviceLocator;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(com.example.mysoftpos.viewmodel.LoginViewModel.class)) {
            return (T) new com.example.mysoftpos.viewmodel.LoginViewModel(
                    serviceLocator.getApplication(),
                    serviceLocator.getUserRepository(),
                    serviceLocator.getDispatcherProvider());
        }

        if (modelClass.isAssignableFrom(com.example.mysoftpos.viewmodel.PurchaseViewModel.class)) {
            return (T) new com.example.mysoftpos.viewmodel.PurchaseViewModel(
                    serviceLocator.getApplication(),
                    serviceLocator.getTransactionRepository(),
                    ConfigManager.getInstance(serviceLocator.getApplication()),
                    serviceLocator.getDispatcherProvider(),
                    serviceLocator.getIsoNetworkClient());
        }

        if (modelClass.isAssignableFrom(com.example.mysoftpos.viewmodel.TransactionDetailViewModel.class)) {
            return (T) new com.example.mysoftpos.viewmodel.TransactionDetailViewModel(
                    serviceLocator.getApplication(),
                    serviceLocator.getTransactionRepository(),
                    ConfigManager.getInstance(serviceLocator.getApplication()),
                    serviceLocator.getDispatcherProvider(),
                    serviceLocator.getIsoNetworkClient(),
                    serviceLocator.getSchemeRepository());
        }

        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
