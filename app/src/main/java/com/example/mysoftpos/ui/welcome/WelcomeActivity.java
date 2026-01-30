package com.example.mysoftpos.ui.welcome;

import com.example.mysoftpos.R;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.example.mysoftpos.ui.auth.LoginActivity;
import com.example.mysoftpos.ui.auth.RegisterActivity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class WelcomeActivity extends AppCompatActivity {

    private CardView btnLogin;
    private View tvRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // Initialize views
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);

        // Set up click listeners
        btnLogin.setOnClickListener(v -> {
            // Navigate to login screen
            Intent intent = new Intent(WelcomeActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        tvRegister.setOnClickListener(v -> {
            // Navigate to register screen
            Intent intent = new Intent(WelcomeActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }
}
