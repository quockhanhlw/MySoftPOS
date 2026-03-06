package com.example.mysoftpos.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

/**
 * Utility to monitor global internet connectivity using ConnectivityManager.
 */
public class NetworkMonitor {

    public interface NetworkCallbackListener {
        void onNetworkAvailable();

        void onNetworkLost();
    }

    private final ConnectivityManager connectivityManager;
    private final NetworkCallbackListener listener;
    private final Handler mainHandler;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean isCurrentlyConnected = true;

    public NetworkMonitor(Context context, NetworkCallbackListener listener) {
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.listener = listener;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void register() {
        if (connectivityManager == null)
            return;

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                if (!isCurrentlyConnected) {
                    isCurrentlyConnected = true;
                    if (listener != null) {
                        mainHandler.post(listener::onNetworkAvailable);
                    }
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                if (isCurrentlyConnected) {
                    isCurrentlyConnected = false;
                    if (listener != null) {
                        mainHandler.post(listener::onNetworkLost);
                    }
                }
            }
        };

        // Initial check to fire instantly if starting offline
        isCurrentlyConnected = isNetworkAvailableNow();

        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    public void unregister() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
                // Ignore if it was already unregistered
            }
        }
    }

    private boolean isNetworkAvailableNow() {
        if (connectivityManager != null) {
            NetworkCapabilities capabilities = connectivityManager
                    .getNetworkCapabilities(connectivityManager.getActiveNetwork());
            return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        }
        return false;
    }
}
