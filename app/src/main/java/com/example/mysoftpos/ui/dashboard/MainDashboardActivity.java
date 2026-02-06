package com.example.mysoftpos.ui.dashboard;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.dao.TransactionDao;
import com.example.mysoftpos.ui.purchase.PurchaseAmountActivity;
import com.example.mysoftpos.ui.balance.BalanceInquiryActivity;
import com.example.mysoftpos.ui.settings.SettingsActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.Observer;

import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.entity.TransactionEntity;
import com.example.mysoftpos.utils.config.ConfigManager;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Main Dashboard - Redesigned based on User Sketch.
 * Features:
 * - Hero Image (home1.jpg)
 * - Welcome Text
 * - Purchase / Balance Buttons
 * - Recent Transaction History (Live from DB)
 */
public class MainDashboardActivity extends AppCompatActivity {

    private LinearLayout historyListContainer;
    private TextView tvMerchantName;
    private View hiddenAdminTrigger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_dashboard);

        // Get user info
        String userRoleArg = getIntent().getStringExtra("USER_ROLE");
        String usernameArg = getIntent().getStringExtra("USERNAME");
        if (userRoleArg == null)
            userRoleArg = "USER";

        final String userRole = userRoleArg;
        final String username = (usernameArg != null) ? usernameArg : "Guest";

        // Bind Views
        tvMerchantName = findViewById(R.id.tvMerchantName);
        historyListContainer = findViewById(R.id.historyListContainer);
        hiddenAdminTrigger = findViewById(R.id.hiddenAdminTrigger);

        View btnPurchase = findViewById(R.id.btnPurchase);
        View btnBalance = findViewById(R.id.btnBalance);
        View btnTestSuite = findViewById(R.id.btnTestSuite);
        ImageView btnSettings = findViewById(R.id.btnSettings);
        ImageView btnLogout = findViewById(R.id.btnLogout);
        ImageView btnHome = findViewById(R.id.btnHome);

        // Set Welcome Name
        tvMerchantName.setText(username);

        // --- ROLE BASED UI ---
        boolean isAdmin = "ADMIN".equals(userRole);
        if (isAdmin) {
            btnPurchase.setVisibility(View.GONE);
            btnBalance.setVisibility(View.GONE);
            if (btnTestSuite != null) {
                btnTestSuite.setVisibility(View.VISIBLE);
            }
        } else {
            btnPurchase.setVisibility(View.VISIBLE);
            btnBalance.setVisibility(View.VISIBLE);
            if (btnTestSuite != null) {
                btnTestSuite.setVisibility(View.GONE);
            }
        }

        // Purchase Action
        btnPurchase.setOnClickListener(v -> {
            Intent intent = new Intent(this, PurchaseAmountActivity.class);
            intent.putExtra("USERNAME", username);
            startActivity(intent);
        });

        // Balance Action
        btnBalance.setOnClickListener(v -> {
            Intent intent = new Intent(this, BalanceInquiryActivity.class);
            intent.putExtra("USERNAME", username);
            startActivity(intent);
        });

        // Test Suite Action (Admin Only)
        if (btnTestSuite != null) {
            btnTestSuite.setOnClickListener(v -> {
                Intent intent = new Intent(this, com.example.mysoftpos.testsuite.SchemeSelectActivity.class);
                startActivity(intent);
            });
        }

        // Settings Action
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        // Logout Action
        btnLogout.setOnClickListener(v -> {
            // Clear any session info if stored (optional)
            // Navigate back to Welcome Activity
            Intent intent = new Intent(this, com.example.mysoftpos.ui.auth.WelcomeActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // Home Action (Just refresh/toast for now)
        btnHome.setOnClickListener(v -> {
            // Already on home
        });

        // Hidden Admin Trigger (Double tap or long press)
        // Keep as fallback for dev
        final String finalUserRole = userRole;
        hiddenAdminTrigger.setOnLongClickListener(v -> {
            if ("ADMIN".equals(finalUserRole) || true) { // Always allow logic for now
                Intent intent = new Intent(this, com.example.mysoftpos.testsuite.SchemeSelectActivity.class);
                startActivity(intent);
                return true;
            }
            return false;
        });

        // Background Video
        setupVideoBackground();

        // Load History
        setupHistoryObserver();
    }

    private void setupVideoBackground() {
        android.widget.VideoView videoView = findViewById(R.id.videoViewBg);
        if (videoView != null) {
            String path = "android.resource://" + getPackageName() + "/" + R.raw.fuji_home;
            videoView.setVideoURI(android.net.Uri.parse(path));
            videoView.setOnPreparedListener(mp -> {
                mp.setLooping(true);

                // Force Center Crop Logic
                float videoRatio = mp.getVideoWidth() / (float) mp.getVideoHeight();
                float screenRatio = videoView.getWidth() / (float) videoView.getHeight();
                float scaleX = 1f;
                float scaleY = 1f;

                if (videoRatio >= screenRatio) {
                    scaleX = videoRatio / screenRatio;
                } else {
                    scaleY = screenRatio / videoRatio;
                }

                android.view.ViewGroup.LayoutParams layoutParams = videoView.getLayoutParams();
                layoutParams.width = (int) (videoView.getWidth() * scaleX);
                layoutParams.height = (int) (videoView.getHeight() * scaleY);
                videoView.setLayoutParams(layoutParams);

                mp.start();
            });
            videoView.start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Restart video if needed
        android.widget.VideoView videoView = findViewById(R.id.videoViewBg);
        if (videoView != null && !videoView.isPlaying()) {
            videoView.start();
        }
    }

    private void setupHistoryObserver() {
        AppDatabase db = AppDatabase.getInstance(this);
        // Get username from Intent (already retrieved in onCreate)
        String currentUsername = getIntent().getStringExtra("USERNAME");
        if (currentUsername == null)
            currentUsername = "Guest";

        String usernameHash = com.example.mysoftpos.utils.security.PasswordUtils.hashSHA256(currentUsername);
        db.transactionDao().getTransactionsByUsernameHashLive(usernameHash).observe(this,
                new Observer<List<TransactionEntity>>() {
                    @Override
                    public void onChanged(List<TransactionEntity> transactions) {
                        updateHistoryList(transactions);
                    }
                });
    }

    // --- History Logic Refactored for Expansion --- //
    private boolean isHistoryExpanded = false;
    private List<TransactionEntity> currentTransactions;

    private void updateHistoryList(List<TransactionEntity> transactions) {
        currentTransactions = transactions;
        renderHistoryList();
    }

    private void renderHistoryList() {
        historyListContainer.removeAllViews();

        if (currentTransactions == null || currentTransactions.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("No recent transactions");
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(0, 32, 0, 32);
            emptyView.setTextColor(Color.parseColor("#999999"));
            historyListContainer.addView(emptyView);
            return;
        }

        // Limit items based on expansion state (1 vs 50)
        int limit = isHistoryExpanded ? 50 : 1;

        int count = 0;
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm dd/MM", Locale.getDefault());
        java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator(',');
        java.text.DecimalFormat currencyFmt = new java.text.DecimalFormat("#,###", symbols);

        for (TransactionEntity txn : currentTransactions) {
            if (count >= limit)
                break;

            // Create Row View
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 16, 0, 16);
            row.setGravity(Gravity.CENTER_VERTICAL);

            // Icon
            ImageView icon = new ImageView(this);
            icon.setImageResource(R.drawable.ic_card);
            icon.setColorFilter(Color.parseColor("#0A2463")); // Neo Primary
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(40, 40);
            iconParams.setMargins(0, 0, 24, 0);
            row.addView(icon, iconParams);

            // Info (Time + Status)
            LinearLayout infoLayout = new LinearLayout(this);
            infoLayout.setOrientation(LinearLayout.VERTICAL);
            infoLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvTime = new TextView(this);
            tvTime.setText(timeFmt.format(new Date(txn.timestamp)));
            tvTime.setTextSize(14f);
            tvTime.setTextColor(Color.BLACK);
            tvTime.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvStatus = new TextView(this);
            String statusUpper = txn.status != null ? txn.status.toUpperCase(Locale.ROOT) : "UNKNOWN";
            tvStatus.setText(statusUpper);
            tvStatus.setTextSize(12f);
            if ("APPROVED".equals(statusUpper) || "SUCCESS".equals(statusUpper)) {
                tvStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
            } else {
                tvStatus.setTextColor(Color.parseColor("#F44336")); // Red
            }

            infoLayout.addView(tvTime);
            infoLayout.addView(tvStatus);
            row.addView(infoLayout);

            // Amount
            TextView tvAmount = new TextView(this);
            String amtStr = txn.amount != null ? txn.amount : "0";
            try {
                long amtVal = Long.parseLong(amtStr);
                tvAmount.setText(currencyFmt.format(amtVal) + " VND");
            } catch (NumberFormatException e) {
                tvAmount.setText(amtStr);
            }
            tvAmount.setTextSize(15f);
            tvAmount.setTextColor(Color.parseColor("#0A2463"));
            tvAmount.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(tvAmount);

            historyListContainer.addView(row);

            // Divider
            if (count < Math.min(currentTransactions.size(), limit) - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(Color.parseColor("#EEEEEE"));
                historyListContainer.addView(divider);
            }

            count++;
        }

        // Handle Arrow Visibility
        View btnSeeMore = findViewById(R.id.btnSeeMoreHistory);
        if (btnSeeMore != null) {
            if (currentTransactions.size() > 1 && !isHistoryExpanded) {
                btnSeeMore.setVisibility(View.VISIBLE);
                btnSeeMore.setOnClickListener(v -> {
                    isHistoryExpanded = true;
                    renderHistoryList();
                    btnSeeMore.setVisibility(View.GONE); // Hide after expanding
                });
            } else {
                btnSeeMore.setVisibility(View.GONE);
            }
        }
    }
}
