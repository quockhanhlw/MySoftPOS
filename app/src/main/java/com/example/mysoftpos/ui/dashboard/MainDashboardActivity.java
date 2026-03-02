package com.example.mysoftpos.ui.dashboard;

import com.example.mysoftpos.R;
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

import androidx.lifecycle.Observer;

import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.entity.TransactionEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.example.mysoftpos.ui.BaseActivity;

/**
 * Main Dashboard - Redesigned based on User Sketch.
 * Features:
 * - Hero Image (home1.jpg)
 * - Welcome Text
 * - Purchase / Balance Buttons
 * import com.example.mysoftpos.ui.BaseActivity;
 * 
 * /**
 * Main Dashboard - Redesigned based on User Sketch.
 * Features:
 * - Hero Image (home1.jpg)
 * - Welcome Text
 * - Purchase / Balance Buttons
 * - Recent Transaction History (Live from DB)
 */
public class MainDashboardActivity extends BaseActivity {

    private LinearLayout historyListContainer;
    private TextView tvMerchantName;
    private View hiddenAdminTrigger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_dashboard);

        // Get user info
        String userRoleArg = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.USER_ROLE);
        String usernameArg = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME);
        String displayNameArg = getIntent().getStringExtra("DISPLAY_NAME");
        if (userRoleArg == null)
            userRoleArg = "USER";

        final String userRole = userRoleArg;
        final String username = (usernameArg != null) ? usernameArg : getString(R.string.guest_user);
        final String displayName = (displayNameArg != null) ? displayNameArg : username;

        // Bind Views
        tvMerchantName = findViewById(R.id.tvMerchantName);
        historyListContainer = findViewById(R.id.historyListContainer);
        hiddenAdminTrigger = findViewById(R.id.hiddenAdminTrigger);

        View btnPurchase = findViewById(R.id.btnPurchase);
        View btnBalance = findViewById(R.id.btnBalance);
        View btnTestSuite = findViewById(R.id.btnTestSuite);
        View btnUserManagement = findViewById(R.id.btnUserManagement);
        ImageView btnSettings = findViewById(R.id.btnSettings);
        // ImageView btnLogout = findViewById(R.id.btnLogout); // Removed
        ImageView btnHome = findViewById(R.id.btnHome);

        // Set Welcome Name (display name, not email)
        tvMerchantName.setText(displayName);

        // --- ROLE BASED UI ---
        boolean isAdmin = "ADMIN".equals(userRole);
        if (isAdmin) {
            btnPurchase.setVisibility(View.GONE);
            btnBalance.setVisibility(View.GONE);
            if (btnTestSuite != null) {
                btnTestSuite.setVisibility(View.VISIBLE);
            }
            if (btnUserManagement != null) {
                btnUserManagement.setVisibility(View.VISIBLE);
            }
        } else {
            btnPurchase.setVisibility(View.VISIBLE);
            btnBalance.setVisibility(View.VISIBLE);
            if (btnTestSuite != null) {
                btnTestSuite.setVisibility(View.GONE);
            }
            if (btnUserManagement != null) {
                btnUserManagement.setVisibility(View.GONE);
            }
        }

        // Purchase Action
        final long currentUserId = getIntent().getLongExtra(com.example.mysoftpos.utils.IntentKeys.USER_ID, -1);
        btnPurchase.setOnClickListener(v -> {
            Intent intent = new Intent(this, PurchaseAmountActivity.class);
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME, username);
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USER_ID, currentUserId);
            startActivity(intent);
        });

        // Balance Action
        btnBalance.setOnClickListener(v -> {
            Intent intent = new Intent(this, BalanceInquiryActivity.class);
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME, username);
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USER_ID, currentUserId);
            startActivity(intent);
        });

        // Test Suite Action (Admin Only)
        if (btnTestSuite != null) {
            btnTestSuite.setOnClickListener(v -> {
                Intent intent = new Intent(this, com.example.mysoftpos.testsuite.SchemeSelectActivity.class);
                startActivity(intent);
            });
        }

        // User Management Action (Admin Only)
        if (btnUserManagement != null) {
            btnUserManagement.setOnClickListener(v -> {
                Intent intent = new Intent(this, com.example.mysoftpos.ui.admin.UserManagementActivity.class);
                // Pass email for admin features (adminId = SHA256(email) in existing DB)
                String userEmail = getIntent().getStringExtra("USER_EMAIL");
                intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME,
                        userEmail != null ? userEmail : username);
                startActivity(intent);
            });
        }

        // Settings Action
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USER_ROLE, userRole);
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME, username);
            startActivity(intent);
        });

        // Logout Action Removed from Home Screen (Only in Settings now)

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

        // Load History (User only — Admin sees history inside each Scheme)
        if (isAdmin) {
            View cardHistory = findViewById(R.id.cardHistory);
            if (cardHistory != null) cardHistory.setVisibility(View.GONE);
        } else {
            setupHistoryObserver(false);
        }
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

    private void setupHistoryObserver(boolean isAdmin) {
        AppDatabase db = AppDatabase.getInstance(this);

        // Use the unique user_id (DB primary key) — guarantees no cross-user leakage
        long userId = getIntent().getLongExtra(com.example.mysoftpos.utils.IntentKeys.USER_ID, -1);
        if (userId <= 0) return;

        db.transactionDao().getTransactionsByUserIdLive(userId).observe(
                this, this::updateHistoryList);
    }

    // --- History Logic Refactored for Expansion --- //
    private boolean isHistoryExpanded = false;
    private List<TransactionEntity> currentTransactions;

    private void updateHistoryList(List<TransactionEntity> transactions) {
        // Filter: Show only Purchases (Amount > 0 and Status isn't just arbitrary
        // string)
        // Or simply exclude Balance Inquiries (Amount "0" or null)
        java.util.List<TransactionEntity> filtered = new java.util.ArrayList<>();
        if (transactions != null) {
            for (TransactionEntity t : transactions) {
                // Only show Purchase transactions with non-zero amount
                if (t.amount != null && !t.amount.equals("0") && isPurchaseTransaction(t)) {
                    filtered.add(t);
                }
            }
        }
        currentTransactions = filtered;
        renderHistoryList();
    }

    /** Check if transaction is a Purchase (DE 3 starts with 00) */
    private boolean isPurchaseTransaction(TransactionEntity txn) {
        try {
            if (txn.requestHex == null) return false;
            com.example.mysoftpos.iso8583.message.IsoMessage req =
                    new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                            .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker
                                    .hexToBytes(txn.requestHex));
            if (req.hasField(3)) {
                return req.getField(3).startsWith("00");
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void renderHistoryList() {
        historyListContainer.removeAllViews();

        if (currentTransactions == null || currentTransactions.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText(R.string.history_empty);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(0, 32, 0, 32);
            emptyView.setTextColor(Color.parseColor("#999999"));
            historyListContainer.addView(emptyView);
            return;
        }

        // Limit items based on expansion state (1 vs 50)
        int limit = isHistoryExpanded ? 50 : 1;

        int count = 0;
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
            // User requested: "ngày tháng năm, giờ" -> dd/MM/yyyy HH:mm
            SimpleDateFormat fullTimeFmt = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());
            tvTime.setText(fullTimeFmt.format(new Date(txn.timestamp)));
            tvTime.setTextSize(14f);
            tvTime.setTextColor(Color.BLACK);
            tvTime.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvStatus = new TextView(this);
            String statusUpper = txn.status != null ? txn.status.toUpperCase(Locale.ROOT) : "UNKNOWN";
            String displayStatus = statusUpper;
            if ("APPROVED".equals(statusUpper) || "SUCCESS".equals(statusUpper)) {
                displayStatus = getString(R.string.status_approved);
            } else if (statusUpper.startsWith("DECLINED")) {
                displayStatus = getString(R.string.status_declined) + " " + statusUpper.substring(8).trim();
            } else if ("PENDING".equals(statusUpper)) {
                displayStatus = getString(R.string.status_pending);
            } else if (statusUpper.startsWith("TIMEOUT")) {
                displayStatus = getString(R.string.status_timeout);
            } else if ("REVERSED".equals(statusUpper)) {
                displayStatus = "REVERSED";
            }
            tvStatus.setText(displayStatus);
            tvStatus.setTextSize(12f);
            if ("APPROVED".equals(statusUpper) || "SUCCESS".equals(statusUpper)) {
                tvStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
            } else if ("REVERSED".equals(statusUpper)) {
                tvStatus.setTextColor(Color.parseColor("#757575")); // Grey
            } else {
                tvStatus.setTextColor(Color.parseColor("#F44336")); // Red
            }

            infoLayout.addView(tvTime);
            infoLayout.addView(tvStatus);
            row.addView(infoLayout);

            // dp helper
            float density = getResources().getDisplayMetrics().density;

            // Amount + Currency
            TextView tvAmount = new TextView(this);
            String amtStr = txn.amount != null ? txn.amount : "0";
            // Parse currency from DE 49
            String currencyLabel = "VND";
            try {
                if (txn.requestHex != null) {
                    com.example.mysoftpos.iso8583.message.IsoMessage req = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                            .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker.hexToBytes(txn.requestHex));
                    if (req.hasField(49)) {
                        String code = req.getField(49).trim();
                        if ("840".equals(code))
                            currencyLabel = "USD";
                        else if ("704".equals(code))
                            currencyLabel = "VND";
                        else
                            currencyLabel = code;
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
            try {
                long amtVal = Long.parseLong(amtStr);
                tvAmount.setText(currencyFmt.format(amtVal) + " " + currencyLabel);
            } catch (NumberFormatException e) {
                tvAmount.setText(amtStr);
            }
            tvAmount.setTextSize(14f);
            tvAmount.setTextColor(Color.parseColor("#0A2463"));
            tvAmount.setTypeface(null, android.graphics.Typeface.BOLD);
            tvAmount.setGravity(Gravity.END);

            // Extract RRN (DE 37)
            String rrn = "";
            try {
                if (txn.responseHex != null) {
                    com.example.mysoftpos.iso8583.message.IsoMessage resp = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                            .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker.hexToBytes(txn.responseHex));
                    if (resp.hasField(37)) {
                        rrn = resp.getField(37).trim();
                    }
                }
            } catch (Exception e) {
                // Ignore parse error
            }

            // RRN display (no label prefix)
            TextView tvRrn = new TextView(this);
            tvRrn.setText(rrn);
            tvRrn.setTextSize(14f);
            tvRrn.setTextColor(Color.parseColor("#757575"));
            tvRrn.setVisibility(rrn.isEmpty() ? View.GONE : View.VISIBLE);
            tvRrn.setGravity(Gravity.END);

            // Right Column (RRN on top, Amount below, then Detail icon)
            LinearLayout rightLayout = new LinearLayout(this);
            rightLayout.setOrientation(LinearLayout.VERTICAL);
            rightLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            rightLayout.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            rightLayout.setMinimumWidth((int) (100 * density));

            rightLayout.addView(tvRrn); // RRN on top
            rightLayout.addView(tvAmount); // Amount below

            // View Detail Icon/Button
            ImageView btnDetail = new ImageView(this);
            btnDetail.setImageResource(android.R.drawable.ic_menu_info_details);
            btnDetail.setColorFilter(Color.parseColor("#757575"));
            int btnSizePx = (int) (28 * density);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(btnSizePx, btnSizePx);
            btnParams.topMargin = (int) (4 * density);
            btnParams.gravity = Gravity.END;
            btnDetail.setLayoutParams(btnParams);

            // On Click Detail
            btnDetail.setOnClickListener(v -> {
                Intent intent = new Intent(this, com.example.mysoftpos.ui.dashboard.TransactionDetailActivity.class);
                intent.putExtra(com.example.mysoftpos.ui.dashboard.TransactionDetailActivity.EXTRA_TRANSACTION_ID,
                        txn.id);
                startActivity(intent);
            });

            rightLayout.addView(btnDetail);

            row.addView(rightLayout);

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
