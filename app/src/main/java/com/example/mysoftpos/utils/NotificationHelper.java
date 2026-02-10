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

    public static void showNotification(Activity activity, String message, int iconRes) {
        ViewGroup rootView = activity.findViewById(android.R.id.content);
        if (rootView == null)
            return;

        LayoutInflater inflater = LayoutInflater.from(activity);
        View notificationView = inflater.inflate(R.layout.layout_custom_notification, rootView, false);

        ImageView icon = notificationView.findViewById(R.id.imgNotificationIcon);
        TextView tvMessage = notificationView.findViewById(R.id.tvNotificationMessage);

        icon.setImageResource(iconRes);
        tvMessage.setText(message);

        // Initial State
        notificationView.setAlpha(0f);
        notificationView.setTranslationY(-100f);

        rootView.addView(notificationView);

        // Animation
        notificationView.animate()
                .alpha(1f)
                .translationY(50f) // Add some top margin visually
                .setDuration(300)
                .setInterpolator(new OvershootInterpolator())
                .start();

        // Interactions
        tvMessage.setOnClickListener(v -> {
            // Expand logic
            if (tvMessage.getMaxLines() == 2) {
                tvMessage.setMaxLines(Integer.MAX_VALUE);
            } else {
                tvMessage.setMaxLines(2);
            }
        });

        // Swipe/Drag to dismiss logic
        notificationView.setOnTouchListener(new View.OnTouchListener() {
            private float startX;
            private float translationX;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        translationX = v.getTranslationX();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - startX;
                        v.setTranslationX(translationX + deltaX);
                        v.setAlpha(1 - Math.abs(deltaX) / v.getWidth());
                        return true;
                    case MotionEvent.ACTION_UP:
                        float finalDelta = event.getRawX() - startX;
                        if (Math.abs(finalDelta) > v.getWidth() / 3) {
                            // Dismiss
                            v.animate()
                                    .translationX(finalDelta > 0 ? v.getWidth() : -v.getWidth())
                                    .alpha(0f)
                                    .setDuration(200)
                                    .withEndAction(() -> rootView.removeView(v))
                                    .start();
                        } else {
                            // Reset
                            v.animate().translationX(0).alpha(1f).setDuration(200).start();
                        }
                        return true;
                }
                return false;
            }
        });

        // Auto Dismiss after 5 seconds
        notificationView.postDelayed(() -> {
            if (notificationView.getParent() != null) {
                notificationView.animate()
                        .alpha(0f)
                        .translationY(-100f)
                        .setDuration(300)
                        .withEndAction(() -> rootView.removeView(notificationView))
                        .start();
            }
        }, 5000);
    }
}
