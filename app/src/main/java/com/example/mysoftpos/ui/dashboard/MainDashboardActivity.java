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
import android.widget.Toast;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.entity.TransactionEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.example.mysoftpos.ui.BaseActivity;
import com.example.mysoftpos.utils.format.AmountFormatUtils;

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
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_dashboard);

        // Get user info
        String userRoleArg = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.USER_ROLE);
        String usernameArg = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME);
        String displayNameArg = getIntent().getStringExtra("DISPLAY_NAME");
        if (userRoleArg == null || userRoleArg.trim().isEmpty()) {
            userRoleArg = com.example.mysoftpos.data.remote.api.ApiClient.getRole(this);
        }
        if (userRoleArg == null || userRoleArg.trim().isEmpty()) {
            userRoleArg = "USER";
        }

        final String userRole = userRoleArg;
        final String username = (usernameArg != null) ? usernameArg : getString(R.string.guest_user);
        final String displayName = (displayNameArg != null) ? displayNameArg : username;
        final boolean isAdmin = "ADMIN".equalsIgnoreCase(userRole);

        // Bind Views
        tvMerchantName = findViewById(R.id.tvMerchantName);
        TextView tvWelcomeTitle = findViewById(R.id.tvWelcomeTitle);
        historyListContainer = findViewById(R.id.historyListContainer);
        hiddenAdminTrigger = findViewById(R.id.hiddenAdminTrigger);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        View btnPurchase = findViewById(R.id.btnPurchase);
        View btnBalance = findViewById(R.id.btnBalance);
        View btnTestSuite = findViewById(R.id.btnTestSuite);
        View btnUserManagement = findViewById(R.id.btnUserManagement);
        View btnTransactionManagement = findViewById(R.id.btnTransactionManagement);
        ImageView btnSettings = findViewById(R.id.btnSettings);
        // ImageView btnLogout = findViewById(R.id.btnLogout); // Removed

        // Set role-aware hero title text to avoid showing generic "User" for admin accounts.
        String heroName = displayName != null ? displayName.trim() : "";
        if (heroName.isEmpty()
                || getString(R.string.common_user).equalsIgnoreCase(heroName)
                || looksLikeContactIdentifier(heroName)) {
            heroName = isAdmin ? getString(R.string.dashboard_admin_label) : getString(R.string.common_user);
        }
        tvMerchantName.setVisibility(View.VISIBLE);
        tvMerchantName.setText(heroName);
        if (tvWelcomeTitle != null) {
            tvWelcomeTitle.setText(isAdmin
                    ? R.string.dashboard_welcome_admin
                    : R.string.dashboard_welcome);
        }

        // --- ROLE BASED UI ---
        if (isAdmin) {
            btnPurchase.setVisibility(View.GONE);
            btnBalance.setVisibility(View.GONE);
            if (btnTestSuite != null) {
                btnTestSuite.setVisibility(View.VISIBLE);
            }
            if (btnUserManagement != null) {
                btnUserManagement.setVisibility(View.VISIBLE);
            }
            if (btnTransactionManagement != null) {
                btnTransactionManagement.setVisibility(View.VISIBLE);
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
            if (btnTransactionManagement != null) {
                btnTransactionManagement.setVisibility(View.GONE);
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
                Intent intent = new Intent(this, com.example.mysoftpos.ui.admin.PosAccountManagementActivity.class);
                // Pass email for admin features (adminId = SHA256(email) in existing DB)
                String userEmail = getIntent().getStringExtra("USER_EMAIL");
                intent.putExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME,
                        userEmail != null ? userEmail : username);
                startActivity(intent);
            });
        }

        // Transaction Management Action (Admin Only)
        if (btnTransactionManagement != null) {
            btnTransactionManagement.setOnClickListener(v -> {
                Intent intent = new Intent(this, com.example.mysoftpos.ui.admin.TransactionManagementActivity.class);
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
            if (cardHistory != null)
                cardHistory.setVisibility(View.GONE);

            // disable swipe to refresh for admin as they don't have history here
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setEnabled(false);
            }
        } else {
            setupHistoryObserver(false);
            setupSwipeRefresh();
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
        if (userId <= 0)
            return;

        db.transactionDao().getTransactionsByUserIdLive(userId).observe(
                this, this::updateHistoryList);
    }

    private void setupSwipeRefresh() {
        if (swipeRefreshLayout != null) {
            // Set colors
            swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#0A2463")); // neoprimary dark

            // On refresh listener
            swipeRefreshLayout.setOnRefreshListener(() -> {
                // Trigger a background sync using the Sync Manager
                new com.example.mysoftpos.data.remote.TransactionSyncManager(MainDashboardActivity.this)
                        .syncUnsynced();

                // Artificial delay to show the spinner briefly, as the observer will update the
                // list
                swipeRefreshLayout.postDelayed(() -> {
                    if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(MainDashboardActivity.this, R.string.history_refreshed, Toast.LENGTH_SHORT)
                                .show();
                    }
                }, 1000);
            });
        }
    }

    // --- History Logic Refactored for Expansion --- //
    private boolean isHistoryExpanded = false;
    private List<TransactionEntity> currentTransactions;

    private void updateHistoryList(List<TransactionEntity> transactions) {
        // Show all completed transactions for the logged-in user.
        // Previous purchase-only filtering caused valid rows to disappear from history.
        java.util.List<TransactionEntity> filtered = new java.util.ArrayList<>();
        if (transactions != null) {
            for (TransactionEntity t : transactions) {
                String status = t.status != null ? t.status.trim().toUpperCase(java.util.Locale.ROOT) : "";
                if (!status.isEmpty() && !"PENDING".equals(status)) {
                    filtered.add(t);
                }
            }
        }
        currentTransactions = filtered;
        renderHistoryList();
    }

    /**
     * Check if transaction is a Purchase using the denormalized processing_code
     * column.
     * Falls back to hex unpacking only for legacy rows that were saved before
     * migration 18.
     */
    private boolean isPurchaseTransaction(TransactionEntity txn) {
        // Fast path: use denormalized column (migration 17→18)
        if (txn.processingCode != null) {
            return txn.processingCode.startsWith("00");
        }
        // Legacy fallback: unpack hex for rows created before the migration
        try {
            if (txn.requestHex == null)
                return false;
            com.example.mysoftpos.iso8583.message.IsoMessage req = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                    .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker
                            .hexToBytes(txn.requestHex));
            return req.hasField(3) && req.getField(3).startsWith("00");
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isBalanceTransaction(TransactionEntity txn) {
        if (txn.processingCode != null) {
            return txn.processingCode.startsWith("30");
        }
        try {
            if (txn.requestHex == null)
                return false;
            com.example.mysoftpos.iso8583.message.IsoMessage req = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                    .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker
                            .hexToBytes(txn.requestHex));
            return req.hasField(3) && req.getField(3).startsWith("30");
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * For balance inquiry history, prefer DE54 (available/ledger balance) instead of
     * the request amount (often 0).
     */
    private String resolveHistoryAmount(TransactionEntity txn) {
        if (!isBalanceTransaction(txn)) {
            return txn.amount != null ? txn.amount : "0";
        }

        try {
            if (txn.responseHex == null || txn.responseHex.isEmpty()) {
                return txn.amount != null ? txn.amount : "0";
            }
            com.example.mysoftpos.iso8583.message.IsoMessage resp = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                    .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker.hexToBytes(txn.responseHex));
            String de54 = resp.getField(54);
            if (de54 == null || de54.length() < 20) {
                return txn.amount != null ? txn.amount : "0";
            }

            String available = null;
            String ledger = null;
            for (int i = 0; i + 20 <= de54.length(); i += 20) {
                String block = de54.substring(i, i + 20);
                String amountType = block.substring(2, 4);
                char sign = block.charAt(7);
                String raw = block.substring(8, 20);
                if (sign == 'D') {
                    raw = "-" + raw;
                }
                if ("02".equals(amountType)) {
                    available = raw;
                } else if ("01".equals(amountType)) {
                    ledger = raw;
                }
            }

            String chosen = available != null ? available : ledger;
            if (chosen != null) {
                return String.valueOf(Long.parseLong(chosen));
            }
        } catch (Exception ignored) {
        }
        return txn.amount != null ? txn.amount : "0";
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
                displayStatus = getString(R.string.status_reversed);
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

            // Amount + Currency — normalized by shared formatter to keep screens consistent.
            TextView tvAmount = new TextView(this);
            String amtStr = resolveHistoryAmount(txn);
            String currencyCode = resolveHistoryCurrencyCode(txn);
            tvAmount.setText(AmountFormatUtils.formatAmountDisplay(amtStr, currencyCode));
            tvAmount.setTextSize(14f);
            tvAmount.setTextColor(Color.parseColor("#0A2463"));
            tvAmount.setTypeface(null, android.graphics.Typeface.BOLD);
            tvAmount.setGravity(Gravity.END);

            // RRN — read from denormalized column (no hex unpacking!)
            // Legacy fallback for rows created before migration 18
            String rrn = "";
            if (txn.rrn != null && !txn.rrn.isEmpty()) {
                rrn = txn.rrn;
            } else {
                try {
                    if (txn.responseHex != null) {
                        com.example.mysoftpos.iso8583.message.IsoMessage resp = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                                .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker
                                        .hexToBytes(txn.responseHex));
                        if (resp.hasField(37)) {
                            rrn = resp.getField(37).trim();
                        }
                    }
                } catch (Exception ignored) {
                }
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

    private boolean looksLikeContactIdentifier(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        return v.contains("@") || v.matches("^[+]?\\d{8,15}$");
    }

    private String resolveHistoryCurrencyCode(TransactionEntity txn) {
        if (txn != null && txn.currencyCode != null && !txn.currencyCode.trim().isEmpty()) {
            return txn.currencyCode.trim();
        }
        try {
            if (txn != null && txn.requestHex != null && !txn.requestHex.trim().isEmpty()) {
                com.example.mysoftpos.iso8583.message.IsoMessage req = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                        .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker.hexToBytes(txn.requestHex));
                if (req.hasField(49)) {
                    String de49 = req.getField(49);
                    if (de49 != null && !de49.trim().isEmpty()) {
                        return de49.trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "704";
    }
}
