package com.example.mysoftpos.utils.threading;
import com.example.mysoftpos.utils.threading.DispatcherProvider;
import com.example.mysoftpos.utils.threading.DefaultDispatcherProvider;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DefaultDispatcherProvider implements DispatcherProvider {
    private final Executor ioExecutor;
    private final Executor uiExecutor;
    private final Executor computationExecutor;

    public DefaultDispatcherProvider() {
        this.ioExecutor = Executors.newCachedThreadPool();
        this.computationExecutor = Executors.newFixedThreadPool(4);
        this.uiExecutor = new MainThreadExecutor();
    }

    @Override
    public Executor io() {
        return ioExecutor;
    }

    @Override
    public Executor ui() {
        return uiExecutor;
    }

    @Override
    public Executor computation() {
        return computationExecutor;
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable command) {
            mainThreadHandler.post(command);
        }
    }
}






