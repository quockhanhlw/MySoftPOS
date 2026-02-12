package com.example.mysoftpos.utils;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.mysoftpos.R;

public class NotificationHelper {

    public static void showNotification(Activity activity, String title, String message, boolean isError) {
        ViewGroup rootView = activity.findViewById(android.R.id.content);
        if (rootView == null)
            return;

        LayoutInflater inflater = LayoutInflater.from(activity);
        View notificationView = inflater.inflate(R.layout.layout_modern_notification, rootView, false);

        ImageView icon = notificationView.findViewById(R.id.imgNotificationIcon);
        TextView tvTitle = notificationView.findViewById(R.id.tvNotificationTitle);
        TextView tvMessage = notificationView.findViewById(R.id.tvNotificationMessage);
        View btnClose = notificationView.findViewById(R.id.btnClose);

        // Content
        tvTitle.setText(title);
        tvMessage.setText(message);

        // Styling based on type
        if (isError) {
            icon.setImageResource(R.drawable.ic_error); // Ensure this drawable exists
            icon.setBackgroundResource(R.drawable.bg_circle_light_red); // Needs to create
            if (icon.getBackground() != null)
                icon.getBackground().setTint(0xFFFFEBEE); // Light Red
            icon.setColorFilter(0xFFD32F2F); // Red 700
        } else {
            icon.setImageResource(R.drawable.ic_check_circle); // Ensure exists
            icon.setBackgroundResource(R.drawable.bg_circle_light_green);
            icon.setColorFilter(0xFF388E3C); // Green 700
        }

        // Initial State
        notificationView.setAlpha(0f);
        notificationView.setTranslationY(-200f);

        rootView.addView(notificationView);

        // Animation In
        notificationView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();

        // Close Logic
        Runnable dismissRunnable = () -> {
            if (notificationView.getParent() != null) {
                notificationView.animate()
                        .alpha(0f)
                        .translationY(-200f)
                        .setDuration(300)
                        .withEndAction(() -> rootView.removeView(notificationView))
                        .start();
            }
        };

        btnClose.setOnClickListener(v -> dismissRunnable.run());

        // Auto Dismiss
        notificationView.postDelayed(dismissRunnable, 4000);
    }

    // Overload for backward compatibility
    public static void showNotification(Activity activity, String message, int iconRes) {
        // Guess type only by iconRes is flaky, default to Success (false error)
        // or strictly 'Info'.
        boolean isError = (iconRes == R.drawable.ic_error); // Heuristic
        showNotification(activity, isError ? "Error" : "Success", message, isError);
    }
}
