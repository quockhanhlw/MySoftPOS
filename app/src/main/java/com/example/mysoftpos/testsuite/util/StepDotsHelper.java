package com.example.mysoftpos.testsuite.util;

import android.app.Activity;
import android.view.View;

import com.example.mysoftpos.R;

/**
 * Sets the active dot in the step indicator.
 * Usage: StepDotsHelper.setActiveStep(activity, 2); // for Channel Select (step
 * 2)
 */
public class StepDotsHelper {

    private static final int[] DOT_IDS = { R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4 };

    public static void setActiveStep(Activity activity, int activeStep) {
        for (int i = 0; i < DOT_IDS.length; i++) {
            View dot = activity.findViewById(DOT_IDS[i]);
            if (dot == null)
                continue;

            if (i == activeStep - 1) {
                dot.setBackgroundResource(R.drawable.bg_dot_active);
                dot.getLayoutParams().width = dpToPx(activity, 8);
                dot.getLayoutParams().height = dpToPx(activity, 8);
            } else {
                dot.setBackgroundResource(R.drawable.bg_dot_inactive);
                dot.getLayoutParams().width = dpToPx(activity, 6);
                dot.getLayoutParams().height = dpToPx(activity, 6);
            }
            dot.requestLayout();
        }
    }

    private static int dpToPx(Activity activity, int dp) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density);
    }
}
