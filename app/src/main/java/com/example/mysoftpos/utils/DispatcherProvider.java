package com.example.mysoftpos.utils;

import java.util.concurrent.Executor;

public interface DispatcherProvider {
    Executor io();

    Executor ui();

    Executor computation();
}
