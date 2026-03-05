package com.example.mysoftpos.data.remote.api;

import android.content.Context;
import android.content.SharedPreferences;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * Singleton Retrofit client for MySoftPOS Backend API.
 */
public final class ApiClient {

    private static final String PREF_NAME = "mysoftpos_api";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_ROLE = "role";
    private static final String KEY_USERNAME = "username";

    // Default backend URL (localhost for emulator, change for real device)
    private static final String DEFAULT_BASE_URL = "https://mysoftpos-backend.onrender.com/";

    private static volatile ApiService apiService;
    private static volatile Retrofit retrofit;

    private ApiClient() {
    }

    public static ApiService getService(Context context) {
        if (apiService == null) {
            synchronized (ApiClient.class) {
                if (apiService == null) {
                    String baseUrl = getBaseUrl(context);

                    HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                    logging.setLevel(HttpLoggingInterceptor.Level.BODY);

                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .writeTimeout(15, TimeUnit.SECONDS)
                            .addInterceptor(logging)
                            .build();

                    retrofit = new Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();

                    apiService = retrofit.create(ApiService.class);
                }
            }
        }
        return apiService;
    }

    /** Force re-create the Retrofit instance (e.g. when base URL changed) */
    public static void reset() {
        synchronized (ApiClient.class) {
            apiService = null;
            retrofit = null;
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
        getPrefs(ctx).edit().clear().apply();
    }

    public static boolean isLoggedIn(Context ctx) {
        return getAccessToken(ctx) != null;
    }

    // ==================== Base URL ====================

    public static void setBaseUrl(Context ctx, String url) {
        getPrefs(ctx).edit().putString(KEY_BASE_URL, url).apply();
        reset(); // Force recreate Retrofit with new URL
    }

    public static String getBaseUrl(Context ctx) {
        return getPrefs(ctx).getString(KEY_BASE_URL, DEFAULT_BASE_URL);
    }

    private static SharedPreferences getPrefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}
