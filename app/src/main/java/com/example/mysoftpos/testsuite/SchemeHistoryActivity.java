package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.widget.Toast;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.entity.TransactionEntity;
import com.example.mysoftpos.testsuite.model.Scheme;
import com.example.mysoftpos.testsuite.storage.SchemeRepository;
import com.example.mysoftpos.ui.dashboard.TransactionDetailActivity;
import com.example.mysoftpos.ui.BaseActivity;
import com.example.mysoftpos.utils.IntentKeys;
import com.example.mysoftpos.utils.format.AmountFormatUtils;
import com.example.mysoftpos.utils.security.PasswordUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Shows transaction history for a specific scheme.
 * Transactions are matched by card BIN prefix (from cards table) against
 * scheme.prefix.
 * Void uses the scheme's own server IP/port.
 */
public class SchemeHistoryActivity extends BaseActivity {

    private String schemeName;
    private String schemePrefix;
    private final List<TransactionEntity> transactions = new ArrayList<>();
    private TxnAdapter adapter;
    private TextView tvEmpty, tvCount;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scheme_history);

        schemeName = getIntent().getStringExtra(IntentKeys.SCHEME);
        if (schemeName == null) {
            finish();
            return;
        }

        // Look up scheme prefix
        SchemeRepository schemeRepo = new SchemeRepository(this);
        Scheme scheme = schemeRepo.getByName(schemeName);
        schemePrefix = (scheme != null && scheme.getPrefix() != null) ? scheme.getPrefix() : "";

        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvSubtitle = findViewById(R.id.tvSubtitle);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvCount = findViewById(R.id.tvCount);

        tvTitle.setText(R.string.scheme_history_title);
        tvSubtitle.setText(schemeName);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvTransactions);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TxnAdapter();
        rv.setAdapter(adapter);

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#0A2463")); // neoprimary dark
            swipeRefreshLayout.setOnRefreshListener(() -> {
                // In a real app, this might trigger a network fetch for a specific scheme
                // Here, we just mimic a refresh delay and let the LiveData update it naturally
                swipeRefreshLayout.postDelayed(() -> {
                    if (swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(SchemeHistoryActivity.this, R.string.history_refreshed, Toast.LENGTH_SHORT)
                                .show();
                    }
                }, 1000);
            });
        }

        // Observe ALL transactions, filter by BIN prefix in-memory
        String adminIdentifier = getIntent().getStringExtra(IntentKeys.USERNAME);
        if (adminIdentifier == null || adminIdentifier.trim().isEmpty()) {
            adminIdentifier = com.example.mysoftpos.data.remote.api.ApiClient.getUsername(this);
        }
        String adminHash = adminIdentifier != null ? PasswordUtils.hashSHA256(adminIdentifier.trim()) : "";

        androidx.lifecycle.LiveData<List<TransactionEntity>> source =
                (adminHash.isEmpty())
                        ? AppDatabase.getInstance(this).transactionDao().getAllTransactionsLive()
                        : AppDatabase.getInstance(this).transactionDao().getTransactionsByAdminHashLive(adminHash);

        source
                .observe(this, allTxns -> {
                    transactions.clear();
                    try {
                        if (allTxns != null) {
                            for (TransactionEntity t : allTxns) {
                                // Skip zero-amount (balance inquiry)
                                if (t.amount == null || "0".equals(t.amount) || "000000000000".equals(t.amount)) {
                                    continue;
                                }
                                // Only show Purchase transactions (DE 3 starts with 00)
                                if (!isPurchaseTransaction(t)) {
                                    continue;
                                }
                                // Match by extracting PAN from requestHex DE 2
                                if (matchesScheme(t)) {
                                    transactions.add(t);
                                }
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("SchemeHistory", "Error filtering transactions", e);
                    }
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(transactions.isEmpty() ? View.VISIBLE : View.GONE);
                    tvCount.setText(getString(R.string.scheme_history_count, transactions.size()));
                });
    }

    /** Check if transaction is a Purchase (DE 3 starts with 00) */
    private boolean isPurchaseTransaction(TransactionEntity txn) {
        if (txn.processingCode != null && !txn.processingCode.trim().isEmpty()) {
            return txn.processingCode.startsWith("00");
        }
        try {
            if (txn.requestHex == null)
                return false;
            com.example.mysoftpos.iso8583.message.IsoMessage req = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                    .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker
                            .hexToBytes(txn.requestHex));
            if (req.hasField(3)) {
                return req.getField(3).startsWith("00");
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Check if transaction's PAN starts with scheme prefix */
    private boolean matchesScheme(TransactionEntity txn) {
        String schemeNorm = normalizeScheme(schemeName);

        // 1) Prefer persisted metadata (cardScheme)
        if (txn.cardScheme != null && !txn.cardScheme.trim().isEmpty()) {
            if (normalizeScheme(txn.cardScheme).equals(schemeNorm)) {
                return true;
            }
        }

        // 2) Fallback to persisted masked PAN prefix if available
        if (schemePrefix != null && !schemePrefix.isEmpty() && txn.maskedPan != null) {
            String digits = txn.maskedPan.replaceAll("[^0-9]", "");
            if (digits.length() >= schemePrefix.length()) {
                if (digits.substring(0, schemePrefix.length()).equals(schemePrefix)) {
                    return true;
                }
            }
        }

        // 3) Legacy fallback: parse DE2 from requestHex
        if (schemePrefix == null || schemePrefix.isEmpty()) {
            return false;
        }
        try {
            if (txn.requestHex == null)
                return false;
            com.example.mysoftpos.iso8583.message.IsoMessage req = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                    .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker
                            .hexToBytes(txn.requestHex));
            if (req.hasField(2)) {
                String pan = req.getField(2);
                return pan.startsWith(schemePrefix);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private String normalizeScheme(String value) {
        if (value == null) {
            return "";
        }
        String s = value.trim().toUpperCase(Locale.ROOT);
        if (s.contains("MASTER")) {
            return "MASTERCARD";
        }
        if (s.contains("VISA")) {
            return "VISA";
        }
        if (s.contains("NAPAS")) {
            return "NAPAS";
        }
        return s;
    }

    private String resolveTxnCurrencyCodeForDisplay(TransactionEntity txn) {
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

    private String resolveTxnAmountForDisplay(TransactionEntity txn) {
        if (txn != null && txn.amount != null && !txn.amount.trim().isEmpty()) {
            return txn.amount.trim();
        }
        try {
            if (txn != null && txn.requestHex != null && !txn.requestHex.trim().isEmpty()) {
                com.example.mysoftpos.iso8583.message.IsoMessage req = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                        .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker.hexToBytes(txn.requestHex));
                if (req.hasField(4)) {
                    return req.getField(4).trim();
                }
            }
        } catch (Exception ignored) {
        }
        return "0";
    }

    // ── Adapter ──

    private class TxnAdapter extends RecyclerView.Adapter<TxnAdapter.VH> {

        private final SimpleDateFormat dateFmt;

        TxnAdapter() {
            dateFmt = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_scheme_transaction, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            TransactionEntity txn = transactions.get(position);

            String trace = txn.traceNumber != null ? txn.traceNumber : getString(R.string.txn_detail_placeholder_dash);
            h.tvTrace.setText(getString(R.string.scheme_history_trace_format, trace));
            h.tvDateTime.setText(dateFmt.format(new Date(txn.timestamp)));

            String status = txn.status != null ? txn.status.toUpperCase(Locale.ROOT) : "UNKNOWN";
            h.tvStatus.setText(status);

            int color;
            if ("APPROVED".equals(status) || "SUCCESS".equals(status)) {
                color = Color.parseColor("#10B981");
            } else if ("REVERSED".equals(status) || "VOIDED".equals(status)) {
                color = Color.parseColor("#94A3B8");
            } else if ("PENDING".equals(status)) {
                color = Color.parseColor("#F59E0B");
            } else {
                color = Color.parseColor("#EF4444");
            }
            h.tvStatus.setTextColor(color);

            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(color);
            h.statusDot.setBackground(dot);

            String amountRaw = resolveTxnAmountForDisplay(txn);
            String currencyCode = resolveTxnCurrencyCodeForDisplay(txn);
            String amountDisplay = AmountFormatUtils.formatAmountDisplay(amountRaw, currencyCode);
            if ("-".equals(amountDisplay)) {
                amountDisplay = getString(R.string.txn_detail_placeholder_dash);
            }
            h.tvAmount.setText(amountDisplay);

            // Card (masked PAN from request DE 2)
            String cardText = getString(R.string.txn_detail_placeholder_dash);
            try {
                if (txn.requestHex != null) {
                    com.example.mysoftpos.iso8583.message.IsoMessage req = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                            .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker
                                    .hexToBytes(txn.requestHex));
                    if (req.hasField(2)) {
                        String pan = req.getField(2);
                        if (pan.length() > 8) {
                            cardText = pan.substring(0, 6) + "****" + pan.substring(pan.length() - 4);
                        } else {
                            cardText = pan;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            h.tvCard.setText(cardText);

            // Click → detail (pass scheme name so void uses scheme's server)
            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(SchemeHistoryActivity.this, TransactionDetailActivity.class);
                intent.putExtra(TransactionDetailActivity.EXTRA_TRANSACTION_ID, txn.id);
                intent.putExtra(IntentKeys.SCHEME, schemeName);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return transactions.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final View statusDot;
            final TextView tvTrace, tvDateTime, tvStatus, tvAmount, tvCard;

            VH(@NonNull View v) {
                super(v);
                statusDot = v.findViewById(R.id.statusDot);
                tvTrace = v.findViewById(R.id.tvTrace);
                tvDateTime = v.findViewById(R.id.tvDateTime);
                tvStatus = v.findViewById(R.id.tvStatus);
                tvAmount = v.findViewById(R.id.tvAmount);
                tvCard = v.findViewById(R.id.tvCard);
            }
        }
    }
}
