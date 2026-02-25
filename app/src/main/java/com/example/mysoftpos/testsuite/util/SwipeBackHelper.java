package com.example.mysoftpos.testsuite.util;

import android.app.Activity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * Adds swipe-left-to-go-back gesture to any Activity.
 * Usage: SwipeBackHelper.attach(this) in onCreate().
 */
public class SwipeBackHelper {

    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    public static void attach(Activity activity) {
        View rootView = activity.getWindow().getDecorView().getRootView();
        GestureDetector detector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null)
                    return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                // Swipe right (finger moves left to right) = go back
                if (Math.abs(diffX) > Math.abs(diffY)
                        && diffX > SWIPE_THRESHOLD
                        && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    activity.finish();
                    activity.overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                    return true;
                }
                return false;
            }
        });

        rootView.setOnTouchListener((v, event) -> {
            detector.onTouchEvent(event);
            return false; // let other views handle touch too
        });
    }
}
