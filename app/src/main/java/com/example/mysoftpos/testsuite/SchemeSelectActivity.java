package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mysoftpos.R;

public class SchemeSelectActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scheme_select); // Assuming existing layout with IDs

        // Setup Listeners
        if (findViewById(R.id.btnNapas) != null) {
            findViewById(R.id.btnNapas).setOnClickListener(v -> navigateToChannel());
        }

        if (findViewById(R.id.btnVisa) != null) {
            findViewById(R.id.btnVisa).setOnClickListener(v -> showComingSoon("Visa"));
        }

        if (findViewById(R.id.btnMastercard) != null) {
            findViewById(R.id.btnMastercard).setOnClickListener(v -> showComingSoon("Mastercard"));
        }

        if (findViewById(R.id.btnBack) != null) {
            findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        }
    }

    private void navigateToChannel() {
        Intent intent = new Intent(this, ChannelSelectActivity.class);
        startActivity(intent);
    }

    private void showComingSoon(String scheme) {
        Toast.makeText(this, scheme + " coming soon...", Toast.LENGTH_SHORT).show();
    }
}
