package com.example.mysoftpos.ui.auth;

import com.example.mysoftpos.R;
import com.example.mysoftpos.ui.dashboard.MainDashboardActivity;

import android.content.Intent;
import android.os.Bundle;

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

        // Login button
        findViewById(R.id.btnLogin).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
        });

        // Register link
        findViewById(R.id.tvRegister).setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }
}
