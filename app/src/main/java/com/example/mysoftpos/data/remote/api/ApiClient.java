package com.example.mysoftpos.data.remote.api;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.mysoftpos.BuildConfig;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * Singleton Retrofit client for MySoftPOS Backend API.
 *
 * Security notes:
 * - HTTP logging set to HEADERS in debug, disabled in release — never BODY.
 * - Tokens stored alongside other session data in private SharedPreferences.
 * For PCI-DSS production, upgrade to EncryptedSharedPreferences when Tink
 * is confirmed working on all target devices.
 */
public final class ApiClient {

    private static final String PREF_NAME = "mysoftpos_api";

    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_ROLE = "role";
    private static final String KEY_USERNAME = "username";

    // Default backend URL
    private static final String DEFAULT_BASE_URL = "https://mysoftpos-backend.onrender.com/";

    private static final long DEFAULT_CONNECT_TIMEOUT_SECONDS = 20;
    private static final long DEFAULT_READ_TIMEOUT_SECONDS = 30;
    private static final long DEFAULT_WRITE_TIMEOUT_SECONDS = 30;
    private static final long AUTH_CONNECT_TIMEOUT_SECONDS = 8;
    private static final long AUTH_READ_TIMEOUT_SECONDS = 12;
    private static final long AUTH_WRITE_TIMEOUT_SECONDS = 12;
    private static final long AUTH_CALL_TIMEOUT_SECONDS = 15;
    private static final long FORGOT_CONNECT_TIMEOUT_SECONDS = 25;
    private static final long FORGOT_READ_TIMEOUT_SECONDS = 110;
    private static final long FORGOT_WRITE_TIMEOUT_SECONDS = 30;
    private static final long FORGOT_CALL_TIMEOUT_SECONDS = 120;

    private static volatile ApiService apiService;
    private static volatile ApiService authApiService;
    private static volatile ApiService forgotPasswordApiService;
    private static volatile Retrofit retrofit;
    private static volatile Retrofit authRetrofit;
    private static volatile Retrofit forgotPasswordRetrofit;

    private ApiClient() {
    }

    public static ApiService getService(Context context) {
        if (apiService == null) {
            synchronized (ApiClient.class) {
                if (apiService == null) {
                    String baseUrl = getBaseUrl(context);
                    retrofit = new Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(buildClient(
                                    DEFAULT_CONNECT_TIMEOUT_SECONDS,
                                    DEFAULT_READ_TIMEOUT_SECONDS,
                                    DEFAULT_WRITE_TIMEOUT_SECONDS,
                                    0))
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();

                    apiService = retrofit.create(ApiService.class);
                }
            }
        }
        return apiService;
    }

    /**
     * Dedicated auth client with shorter timeout so login can fall back faster
     * when the backend is sleeping or temporarily unreachable.
     */
    public static ApiService getAuthService(Context context) {
        if (authApiService == null) {
            synchronized (ApiClient.class) {
                if (authApiService == null) {
                    String baseUrl = getBaseUrl(context);
                    authRetrofit = new Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(buildClient(
                                    AUTH_CONNECT_TIMEOUT_SECONDS,
                                    AUTH_READ_TIMEOUT_SECONDS,
                                    AUTH_WRITE_TIMEOUT_SECONDS,
                                    AUTH_CALL_TIMEOUT_SECONDS))
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();

                    authApiService = authRetrofit.create(ApiService.class);
                }
            }
        }
        return authApiService;
    }

    /**
     * Forgot-password request can include SMTP latency on backend side,
     * so use a longer call timeout than regular API calls.
     */
    public static ApiService getForgotPasswordService(Context context) {
        if (forgotPasswordApiService == null) {
            synchronized (ApiClient.class) {
                if (forgotPasswordApiService == null) {
                    String baseUrl = getBaseUrl(context);
                    forgotPasswordRetrofit = new Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(buildClient(
                                    FORGOT_CONNECT_TIMEOUT_SECONDS,
                                    FORGOT_READ_TIMEOUT_SECONDS,
                                    FORGOT_WRITE_TIMEOUT_SECONDS,
                                    FORGOT_CALL_TIMEOUT_SECONDS))
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();

                    forgotPasswordApiService = forgotPasswordRetrofit.create(ApiService.class);
                }
            }
        }
        return forgotPasswordApiService;
    }

    /** Force re-create the Retrofit instance (e.g. when base URL changed) */
    public static void reset() {
        synchronized (ApiClient.class) {
            apiService = null;
            retrofit = null;
            authApiService = null;
            authRetrofit = null;
            forgotPasswordApiService = null;
            forgotPasswordRetrofit = null;
        }
    }

    // ==================== Token Management ====================

    public static void saveTokens(Context ctx, String accessToken, String refreshToken) {
        getPrefs(ctx).edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply();
    }

    public static String getAccessToken(Context ctx) {
        return getPrefs(ctx).getString(KEY_ACCESS_TOKEN, null);
    }

    public static String getRefreshToken(Context ctx) {
        return getPrefs(ctx).getString(KEY_REFRESH_TOKEN, null);
    }

    /** Returns "Bearer <token>" for Authorization header */
    public static String bearerToken(Context ctx) {
        String token = getAccessToken(ctx);
        return token != null ? "Bearer " + token : "";
    }

    // ==================== User Session ====================

    public static void saveUserSession(Context ctx, ApiService.LoginResponse resp) {
        SharedPreferences.Editor editor = getPrefs(ctx).edit();
        editor.putString(KEY_ACCESS_TOKEN, resp.accessToken);
        editor.putString(KEY_REFRESH_TOKEN, resp.refreshToken);
        if (resp.user != null) {
            editor.putLong(KEY_USER_ID, resp.user.id);
            editor.putString(KEY_ROLE, resp.user.role);
            editor.putString(KEY_USERNAME, resp.user.phone);
        }
        editor.apply();
    }

    public static long getUserId(Context ctx) {
        return getPrefs(ctx).getLong(KEY_USER_ID, -1);
    }

    public static String getRole(Context ctx) {
        return getPrefs(ctx).getString(KEY_ROLE, "");
    }

    public static String getUsername(Context ctx) {
        return getPrefs(ctx).getString(KEY_USERNAME, "");
    }

    public static void clearSession(Context ctx) {
        getPrefs(ctx).edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_ROLE)
                .remove(KEY_USERNAME)
                .apply();
    }

    public static boolean isLoggedIn(Context ctx) {
        return getAccessToken(ctx) != null;
    }

    // ==================== Base URL ====================

    public static void setBaseUrl(Context ctx, String url) {
        getPrefs(ctx).edit().putString(KEY_BASE_URL, url).apply();
        reset();
    }

    public static String getBaseUrl(Context ctx) {
        return getPrefs(ctx).getString(KEY_BASE_URL, DEFAULT_BASE_URL);
    }

    // ==================== SharedPreferences ====================

    private static SharedPreferences getPrefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static OkHttpClient buildClient(long connectTimeoutSeconds,
                                            long readTimeoutSeconds,
                                            long writeTimeoutSeconds,
                                            long callTimeoutSeconds) {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS);

        if (callTimeoutSeconds > 0) {
            clientBuilder.callTimeout(callTimeoutSeconds, TimeUnit.SECONDS);
        }

        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);
            clientBuilder.addInterceptor(logging);
        }

        return clientBuilder.build();
    }
}
