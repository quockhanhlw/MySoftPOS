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

import com.example.mysoftpos.ui.BaseActivity;

public class WelcomeActivity extends BaseActivity {

    // TODO: Set to false to re-enable login/register flow
    private static final boolean SKIP_LOGIN = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (SKIP_LOGIN) {
            // Bypass login - go directly to Dashboard as USER
            Intent intent = new Intent(this, MainDashboardActivity.class);
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USER_ROLE, "USER");
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME, "Guest");
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_welcome);

        // Bind Views

        View btnLogin = findViewById(R.id.btnLogin);
        View btnRegister = findViewById(R.id.btnRegister);

        // Click Listeners
        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
        });

        btnRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }
}
