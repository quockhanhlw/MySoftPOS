package com.example.mysoftpos.utils.threading;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

public class DefaultDispatcherProvider implements DispatcherProvider {
    private final Executor ioExecutor;
    private final Executor uiExecutor;
    private final Executor computationExecutor;

    public DefaultDispatcherProvider() {
        // Bounded IO pool — prevents resource exhaustion under concurrent NFC+network load (H-4).
        // Core=4, max=8, queue capacity=64, CallerRunsPolicy for back-pressure.
        this.ioExecutor = new ThreadPoolExecutor(
                4, 8,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(64),
                new ThreadPoolExecutor.CallerRunsPolicy());
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
