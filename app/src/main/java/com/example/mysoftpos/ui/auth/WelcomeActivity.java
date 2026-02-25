package com.example.mysoftpos.ui.auth;

import com.example.mysoftpos.R;
import com.example.mysoftpos.ui.dashboard.MainDashboardActivity;

import android.content.Intent;
import android.os.Bundle;

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.example.mysoftpos.ui.BaseActivity;

public class WelcomeActivity extends BaseActivity {

    private static final boolean SKIP_LOGIN = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (SKIP_LOGIN) {
            Intent intent = new Intent(this, MainDashboardActivity.class);
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USER_ROLE, "USER");
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME, "Guest");
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_welcome);

        View btnLogin = findViewById(R.id.btnLogin);
        View btnRegister = findViewById(R.id.btnRegister);

        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
        });

        btnRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });

        // Money icon rotation
        View moneyIcon = findViewById(R.id.ivMoneyIcon);
        if (moneyIcon != null) {
            Animation rotate = AnimationUtils.loadAnimation(this, R.anim.rotate_360);
            moneyIcon.startAnimation(rotate);
        }

        // Floating circle animations
        View circle1 = findViewById(R.id.circleDecor1);
        View circle2 = findViewById(R.id.circleDecor2);
        View circle3 = findViewById(R.id.circleDecor3);

        if (circle1 != null) {
            Animation floatAnim1 = AnimationUtils.loadAnimation(this, R.anim.float_up_down);
            circle1.startAnimation(floatAnim1);
        }
        if (circle2 != null) {
            Animation floatAnim2 = AnimationUtils.loadAnimation(this, R.anim.float_diagonal);
            circle2.startAnimation(floatAnim2);
        }
        if (circle3 != null) {
            Animation floatAnim3 = AnimationUtils.loadAnimation(this, R.anim.float_up_down);
            floatAnim3.setStartOffset(1500);
            circle3.startAnimation(floatAnim3);
        }

        // Button entrance animations
        View btnContainer = findViewById(R.id.btnGetStartedContainer);
        if (btnContainer != null) {
            Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in);
            slideUp.setStartOffset(300);
            btnContainer.startAnimation(slideUp);
        }
        View loginContainer = findViewById(R.id.btnLoginContainer);
        if (loginContainer != null) {
            Animation slideUp2 = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in);
            slideUp2.setStartOffset(500);
            loginContainer.startAnimation(slideUp2);
        }
    }
}
