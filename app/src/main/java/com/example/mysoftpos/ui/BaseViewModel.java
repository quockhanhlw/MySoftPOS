package com.example.mysoftpos.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import com.example.mysoftpos.utils.DispatcherProvider;

public abstract class BaseViewModel extends AndroidViewModel {
    private final DispatcherProvider dispatchers;

    public BaseViewModel(Application application, DispatcherProvider dispatchers) {
        super(application);
        this.dispatchers = dispatchers;
    }

    protected DispatcherProvider getDispatchers() {
        return dispatchers;
    }

    protected void launchIo(Runnable runnable) {
        dispatchers.io().execute(runnable);
    }

    protected void launchUi(Runnable runnable) {
        dispatchers.ui().execute(runnable);
    }
}
