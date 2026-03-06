package com.example.mysoftpos.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mysoftpos.data.local.entity.UserEntity;
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;
import com.example.mysoftpos.data.repository.UserRepository;
import com.example.mysoftpos.ui.base.BaseViewModel;
import com.example.mysoftpos.utils.security.AuditLogger;
import com.example.mysoftpos.utils.security.PasswordUtils;
import com.example.mysoftpos.utils.security.SessionManager;
import com.example.mysoftpos.utils.threading.DispatcherProvider;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * MVVM ViewModel for the Login screen.
 * Encapsulates the local-first + API login flow that was previously
 * embedded in LoginActivity (~500 lines → extracted here).
 *
 * The Activity now only observes {@link #getLoginState()} and renders UI.
 */
public class LoginViewModel extends BaseViewModel {

    private static final String TAG = "LoginVM";

    private final UserRepository userRepository;
    private final MutableLiveData<LoginState> loginState = new MutableLiveData<>(LoginState.idle());

    public LoginViewModel(Application application, UserRepository userRepository,
                          DispatcherProvider dispatchers) {
        super(application, dispatchers);
        this.userRepository = userRepository;
    }

    public LiveData<LoginState> getLoginState() {
        return loginState;
    }

    /**
     * Main entry point: local-first login strategy.
     * 1. Check SQLite cache → instant login + background API sync
     * 2. If not found locally → online API login
     */
    public void login(String username, String password) {
        loginState.setValue(LoginState.loading());

        launchIo(() -> {
            try {
                // Step 1: Try local cache first
                UserEntity user = userRepository.findUser(username);

                if (user != null) {
                    // Check account lockout
                    long lockRemaining = userRepository.getLockRemainingMillis(user);
                    if (lockRemaining > 0) {
                        int min = (int) (lockRemaining / 60000) + 1;
                        launchUi(() -> loginState.setValue(LoginState.locked(min)));
                        return;
                    }

                    // Verify password against local cache
                    if (PasswordUtils.verifyPassword(password, user.passwordHash)) {
                        // ✅ LOCAL LOGIN SUCCESS
                        userRepository.resetFailedAttempts(user);
                        SessionManager.startSession();
                        AuditLogger.log(getApplication(), username, "LOGIN",
                                true, TAG, "Local-first login: " + user.role);

                        final UserEntity cachedUser = user;

                        // Background: sync with API to refresh JWT token
                        syncWithBackendInBackground(username, password, () -> {
                            launchUi(() -> loginState.setValue(LoginState.success(
                                    cachedUser.id, cachedUser.role,
                                    cachedUser.displayName != null ? cachedUser.displayName : "User",
                                    cachedUser.phone, cachedUser.email)));
                        });
                        return;
                    } else {
                        // Wrong password — increment failed attempts
                        userRepository.incrementFailedAttempts(user);
                    }
                }

                // Step 2: Not found locally or wrong password → try API
                launchUi(() -> loginViaApi(username, password));
            } catch (Exception e) {
                // SQLite error → fallback to API
                Log.w(TAG, "Local DB error, falling back to API: " + e.getMessage());
                launchUi(() -> loginViaApi(username, password));
            }
        });
    }

    /**
     * Online API login via Retrofit.
     */
    private void loginViaApi(String username, String password) {
        try {
            ApiService api = ApiClient.getService(getApplication());

            api.login(new ApiService.LoginRequest(username, password))
                    .enqueue(new Callback<ApiService.LoginResponse>() {
                        @Override
                        public void onResponse(Call<ApiService.LoginResponse> call,
                                               Response<ApiService.LoginResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                ApiService.LoginResponse resp = response.body();
                                ApiClient.saveUserSession(getApplication(), resp);
                                SessionManager.startSession();
                                AuditLogger.log(getApplication(), username, "LOGIN",
                                        true, TAG, "API login: " + resp.user.role);

                                // Cache + resolve local ID in background
                                launchIo(() -> {
                                    userRepository.cacheUser(username, password, resp.user);
                                    long localUserId = userRepository.resolveLocalUserId(
                                            username, resp.user.id);
                                    triggerBackendSync(resp.user.role);

                                    launchUi(() -> loginState.setValue(LoginState.success(
                                            localUserId, resp.user.role,
                                            resp.user.fullName != null ? resp.user.fullName : "User",
                                            resp.user.phone, resp.user.email)));
                                });
                            } else {
                                handleApiError(username, response);
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiService.LoginResponse> call, Throwable t) {
                            // Network unreachable → offline fallback
                            Log.w(TAG, "API unreachable, falling back to offline: " + t.getMessage());
                            loginViaLocalRoom(username, password);
                        }
                    });
        } catch (Exception e) {
            loginState.setValue(LoginState.error("Login Error: " + e.getMessage()));
        }
    }

    /**
     * Offline-only Room DB login (API unreachable).
     */
    private void loginViaLocalRoom(String username, String password) {
        launchIo(() -> {
            try {
                UserEntity user = userRepository.findUser(username);

                if (user != null) {
                    long lockRemaining = userRepository.getLockRemainingMillis(user);
                    if (lockRemaining > 0) {
                        int min = (int) (lockRemaining / 60000) + 1;
                        launchUi(() -> loginState.setValue(LoginState.locked(min)));
                        return;
                    }

                    if (PasswordUtils.verifyPassword(password, user.passwordHash)) {
                        userRepository.resetFailedAttempts(user);
                        SessionManager.startSession();
                        AuditLogger.log(getApplication(), username, "LOGIN",
                                true, TAG, "Offline login: " + user.role);

                        final UserEntity finalUser = user;
                        // Background: try to sync JWT token
                        syncWithBackendInBackground(username, password, () -> {
                            launchUi(() -> loginState.setValue(LoginState.success(
                                    finalUser.id, finalUser.role,
                                    finalUser.displayName != null ? finalUser.displayName : "User",
                                    finalUser.phone, finalUser.email)));
                        });
                        return;
                    } else {
                        userRepository.incrementFailedAttempts(user);
                    }
                }

                launchUi(() -> loginState.setValue(LoginState.error(
                        "Server unavailable and no offline account found.")));
            } catch (Exception e) {
                launchUi(() -> loginState.setValue(
                        LoginState.error("Login Error: " + e.getMessage())));
            }
        });
    }

    /**
     * Background sync with backend — doesn't block login flow.
     */
    private void syncWithBackendInBackground(String username, String password, Runnable onComplete) {
        try {
            ApiService api = ApiClient.getService(getApplication());
            api.login(new ApiService.LoginRequest(username, password))
                    .enqueue(new Callback<ApiService.LoginResponse>() {
                        @Override
                        public void onResponse(Call<ApiService.LoginResponse> call,
                                               Response<ApiService.LoginResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                ApiService.LoginResponse resp = response.body();
                                ApiClient.saveUserSession(getApplication(), resp);
                                launchIo(() -> {
                                    userRepository.cacheUser(username, password, resp.user);
                                    triggerBackendSync(resp.user.role);
                                });
                            }
                            if (onComplete != null) onComplete.run();
                        }

                        @Override
                        public void onFailure(Call<ApiService.LoginResponse> call, Throwable t) {
                            Log.d(TAG, "Background sync skipped: " + t.getMessage());
                            if (onComplete != null) onComplete.run();
                        }
                    });
        } catch (Exception e) {
            Log.w(TAG, "Background sync error: " + e.getMessage());
            if (onComplete != null) onComplete.run();
        }
    }

    private void triggerBackendSync(String role) {
        if ("ADMIN".equals(role)) {
            new com.example.mysoftpos.data.remote.ConfigSyncManager(getApplication()).sync();
            new com.example.mysoftpos.data.remote.TestSuiteSyncManager(getApplication()).pull();
        }
        // Use WorkManager for reliable transaction sync
        com.example.mysoftpos.data.remote.SyncWorker.enqueueOneTime(getApplication());
        com.example.mysoftpos.data.remote.SyncWorker.schedulePeriodicSync(getApplication());
    }

    private void handleApiError(String username, Response<ApiService.LoginResponse> response) {
        String errorMsg = "Invalid username or password!";
        try {
            if (response.errorBody() != null) {
                String body = response.errorBody().string();
                if (body.contains("locked"))
                    errorMsg = "Account locked. Try again later.";
            }
        } catch (Exception ignored) {
        }

        AuditLogger.log(getApplication(), username, "LOGIN_FAILED",
                false, TAG, "API: " + response.code());

        final String finalMsg = errorMsg;
        loginState.setValue(LoginState.error(finalMsg));
    }
}

