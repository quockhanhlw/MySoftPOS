package com.example.mysoftpos.data.remote.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

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
 * - Tokens encrypted at rest via EncryptedSharedPreferences (AES256-SIV + AES256-GCM).
 * - Single OkHttpClient shared across all Retrofit instances for optimal memory usage.
 */
public final class ApiClient {

    private static final String TAG = "ApiClient";
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
    private static final long REGISTER_CONNECT_TIMEOUT_SECONDS = 20;
    private static final long REGISTER_READ_TIMEOUT_SECONDS = 70;
    private static final long REGISTER_WRITE_TIMEOUT_SECONDS = 30;
    private static final long REGISTER_CALL_TIMEOUT_SECONDS = 75;
    private static volatile ApiService apiService;
    private static volatile ApiService authApiService;
    private static volatile ApiService registerApiService;

    /** Single master OkHttpClient — all variants share its connection pool & dispatcher */
    private static volatile OkHttpClient masterClient;

    private ApiClient() {
    }

    /**
     * Lazily creates and caches the master OkHttpClient with default timeouts.
     * All Retrofit-specific clients derive from this via newBuilder().
     */
    private static OkHttpClient getMasterClient() {
        if (masterClient == null) {
            synchronized (ApiClient.class) {
                if (masterClient == null) {
                    OkHttpClient.Builder builder = new OkHttpClient.Builder()
                            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .writeTimeout(DEFAULT_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                    // Attach app version for backend observability (e.g., legacy /api/users sunset dashboard).
                    builder.addInterceptor(chain -> chain.proceed(
                            chain.request().newBuilder()
                                    .header("X-App-Version", BuildConfig.VERSION_NAME)
                                    .build()));

                    if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                        logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);
                        builder.addInterceptor(logging);
                    }

                    masterClient = builder.build();
                }
            }
        }
        return masterClient;
    }

    /**
     * Derives a client from masterClient, overriding only the given timeouts.
     * Shares the same connection pool, dispatcher, and interceptors.
     */
    private static OkHttpClient deriveClient(long connectSec, long readSec, long writeSec, long callSec) {
        OkHttpClient.Builder b = getMasterClient().newBuilder()
                .connectTimeout(connectSec, TimeUnit.SECONDS)
                .readTimeout(readSec, TimeUnit.SECONDS)
                .writeTimeout(writeSec, TimeUnit.SECONDS);
        if (callSec > 0) {
            b.callTimeout(callSec, TimeUnit.SECONDS);
        }
        return b.build();
    }

    private static Retrofit buildRetrofit(String baseUrl, OkHttpClient client) {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public static ApiService getService(Context context) {
        if (apiService == null) {
            synchronized (ApiClient.class) {
                if (apiService == null) {
                    apiService = buildRetrofit(getBaseUrl(context), getMasterClient())
                            .create(ApiService.class);
                }
            }
        }
        return apiService;
    }

    public static ApiService getAuthService(Context context) {
        if (authApiService == null) {
            synchronized (ApiClient.class) {
                if (authApiService == null) {
                    OkHttpClient client = deriveClient(
                            AUTH_CONNECT_TIMEOUT_SECONDS,
                            AUTH_READ_TIMEOUT_SECONDS,
                            AUTH_WRITE_TIMEOUT_SECONDS,
                            AUTH_CALL_TIMEOUT_SECONDS);
                    authApiService = buildRetrofit(getBaseUrl(context), client)
                            .create(ApiService.class);
                }
            }
        }
        return authApiService;
    }

    public static ApiService getRegisterService(Context context) {
        if (registerApiService == null) {
            synchronized (ApiClient.class) {
                if (registerApiService == null) {
                    OkHttpClient client = deriveClient(
                            REGISTER_CONNECT_TIMEOUT_SECONDS,
                            REGISTER_READ_TIMEOUT_SECONDS,
                            REGISTER_WRITE_TIMEOUT_SECONDS,
                            REGISTER_CALL_TIMEOUT_SECONDS);
                    registerApiService = buildRetrofit(getBaseUrl(context), client)
                            .create(ApiService.class);
                }
            }
        }
        return registerApiService;
    }

    /** Force re-create all Retrofit instances (e.g. when base URL changed) */
    public static void reset() {
        synchronized (ApiClient.class) {
            apiService = null;
            authApiService = null;
            registerApiService = null;
            // masterClient is NOT reset — its pool remains valid for any base URL
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
        ApiService.PosAccountDto sessionUser = resp.user != null ? resp.user : resp.posAccount;
        if (sessionUser != null) {
            editor.putLong(KEY_USER_ID, sessionUser.id);
            editor.putString(KEY_ROLE, sessionUser.role);
            editor.putString(KEY_USERNAME, sessionUser.phone);
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
        String normalized = normalizeBaseUrl(url);
        getPrefs(ctx).edit().putString(KEY_BASE_URL, normalized).apply();
        reset();
    }

    public static String getBaseUrl(Context ctx) {
        String saved = getPrefs(ctx).getString(KEY_BASE_URL, DEFAULT_BASE_URL);
        String normalized = normalizeBaseUrl(saved);
        if (!normalized.equals(saved)) {
            getPrefs(ctx).edit().putString(KEY_BASE_URL, normalized).apply();
        }
        return normalized;
    }

    // ==================== Encrypted SharedPreferences ====================

    private static volatile SharedPreferences encryptedPrefs;

    /**
     * Returns EncryptedSharedPreferences backed by AES256-SIV (key encryption)
     * and AES256-GCM (value encryption). Falls back to plain SharedPreferences
     * only if the device's keystore is fundamentally broken (extremely rare on API 23+).
     */
    private static SharedPreferences getPrefs(Context ctx) {
        if (encryptedPrefs == null) {
            synchronized (ApiClient.class) {
                if (encryptedPrefs == null) {
                    try {
                        String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
                        encryptedPrefs = EncryptedSharedPreferences.create(
                                PREF_NAME,
                                masterKeyAlias,
                                ctx.getApplicationContext(),
                                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
                    } catch (Exception e) {
                        // Fallback: device keystore unavailable (very rare)
                        Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to plain prefs", e);
                        encryptedPrefs = ctx.getApplicationContext()
                                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                    }
                }
            }
        }
        return encryptedPrefs;
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null) {
            return DEFAULT_BASE_URL;
        }
        String normalized = url.trim();
        if (normalized.isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        if (!(normalized.startsWith("https://") || normalized.startsWith("http://"))) {
            return DEFAULT_BASE_URL;
        }
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized;
    }
}
