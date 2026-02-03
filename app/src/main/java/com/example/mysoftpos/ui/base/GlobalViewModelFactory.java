package com.example.mysoftpos.ui.base;
import com.example.mysoftpos.utils.config.ConfigManager;
import com.example.mysoftpos.ui.base.GlobalViewModelFactory;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.mysoftpos.di.ServiceLocator;
import com.example.mysoftpos.testsuite.viewmodel.RunnerViewModel;

// We will add more ViewModels here as we refactor
public class GlobalViewModelFactory implements ViewModelProvider.Factory {

    private final ServiceLocator serviceLocator;

    public GlobalViewModelFactory(ServiceLocator serviceLocator) {
        this.serviceLocator = serviceLocator;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(com.example.mysoftpos.viewmodel.PurchaseViewModel.class)) {
            return (T) new com.example.mysoftpos.viewmodel.PurchaseViewModel(
                    serviceLocator.getApplication(),
                    serviceLocator.getTransactionRepository(),
                    com.example.mysoftpos.utils.config.ConfigManager.getInstance(serviceLocator.getApplication()),
                    serviceLocator.getDispatcherProvider());
        }

        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}






