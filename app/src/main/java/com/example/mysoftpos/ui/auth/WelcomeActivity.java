package com.example.mysoftpos.ui.auth;

import com.example.mysoftpos.R;
import com.example.mysoftpos.ui.dashboard.MainDashboardActivity;

import android.content.Intent;
import android.os.Bundle;

import android.view.View;
import android.widget.ImageView;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {

    // TODO: Set to false to re-enable login/register flow
    private static final boolean SKIP_LOGIN = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (SKIP_LOGIN) {
            // Bypass login - go directly to Dashboard as USER
            Intent intent = new Intent(this, MainDashboardActivity.class);
            intent.putExtra("USER_ROLE", "USER");
            intent.putExtra("USERNAME", "Guest");
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_welcome);

        // Bind Views
        ImageView ivHero = findViewById(R.id.ivHero);
        View btnLogin = findViewById(R.id.btnLogin);
        View tvRegister = findViewById(R.id.tvRegister);

        // Start Pulse Animation
        Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse);
        ivHero.startAnimation(pulse);

        // Click Listeners
        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
        });

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }
}
