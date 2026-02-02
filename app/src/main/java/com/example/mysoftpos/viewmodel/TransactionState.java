package com.example.mysoftpos.viewmodel;

public class TransactionState {
    public final boolean isLoading;
    public final boolean isSuccess;
    public final String message;
    public final String isoResponse;
    public final String isoRequest;

    private TransactionState(boolean isLoading, boolean isSuccess, String message, String isoResponse,
            String isoRequest) {
        this.isLoading = isLoading;
        this.isSuccess = isSuccess;
        this.message = message;
        this.isoResponse = isoResponse;
        this.isoRequest = isoRequest;
    }

    public static TransactionState loading() {
        return new TransactionState(true, false, null, null, null);
    }

    public static TransactionState success(String message, String isoResponse, String isoRequest) {
        return new TransactionState(false, true, message, isoResponse, isoRequest);
    }

    public static TransactionState error(String message) {
        return new TransactionState(false, false, message, null, null);
    }

    public static TransactionState failed(String message, String isoResponse, String isoRequest) {
        return new TransactionState(false, false, message, isoResponse, isoRequest);
    }
}
