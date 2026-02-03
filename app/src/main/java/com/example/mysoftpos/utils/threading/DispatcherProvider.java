package com.example.mysoftpos.utils.threading;
import com.example.mysoftpos.utils.threading.DispatcherProvider;

import java.util.concurrent.Executor;

public interface DispatcherProvider {
    Executor io();

    Executor ui();

    Executor computation();
}






