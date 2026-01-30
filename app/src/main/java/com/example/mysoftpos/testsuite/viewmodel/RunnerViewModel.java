package com.example.mysoftpos.testsuite.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mysoftpos.data.repository.TransactionRepository;
import com.example.mysoftpos.testsuite.model.CardProfile;
import com.example.mysoftpos.testsuite.model.TestCase;
import com.example.mysoftpos.testsuite.model.TestResult;

public class RunnerViewModel extends AndroidViewModel {

    private final TransactionRepository repository;
    private final MutableLiveData<RunnerState> state = new MutableLiveData<>();

    public RunnerViewModel(@NonNull Application application) {
        super(application);
        // In a real strict SOLID app, we would inject Repository here via Hilt/Dagger.
        // For this refactor, we instantiate it manually but kept clean.
        this.repository = new TransactionRepository(application);
    }

    public LiveData<RunnerState> getState() {
        return state;
    }

    public void runTransaction(TestCase testCase, CardProfile card, String amount) {
        state.setValue(new RunnerState.Loading());

        repository.executeTransaction(testCase, card, amount, new TransactionRepository.TransactionCallback() {
            @Override
            public void onSuccess(TestResult result) {
                state.postValue(new RunnerState.Success(result));
            }

            @Override
            public void onError(String message) {
                state.postValue(new RunnerState.Error(message));
            }
        });
    }

    // --- State Classes (Sealed Class pattern equivalent) ---
    public static abstract class RunnerState {
        public static class Loading extends RunnerState {}
        
        public static class Success extends RunnerState {
            public final TestResult result;
            public Success(TestResult result) { this.result = result; }
        }
        
        public static class Error extends RunnerState {
            public final String message;
            public Error(String message) { this.message = message; }
        }
    }
}
