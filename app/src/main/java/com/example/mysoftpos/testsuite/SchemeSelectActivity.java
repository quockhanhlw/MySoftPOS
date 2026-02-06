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
        if (findViewById(R.id.cardNapas) != null) {
            findViewById(R.id.cardNapas).setOnClickListener(v -> navigateToChannel());
        }

        if (findViewById(R.id.cardVisa) != null) {
            findViewById(R.id.cardVisa).setOnClickListener(v -> showComingSoon("Visa"));
        }

        if (findViewById(R.id.cardMaster) != null) {
            findViewById(R.id.cardMaster).setOnClickListener(v -> showComingSoon("Mastercard"));
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
