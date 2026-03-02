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
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.entity.TransactionEntity;
import com.example.mysoftpos.data.local.entity.TransactionWithDetails;
import com.example.mysoftpos.testsuite.model.Scheme;
import com.example.mysoftpos.testsuite.storage.SchemeRepository;
import com.example.mysoftpos.ui.dashboard.TransactionDetailActivity;
import com.example.mysoftpos.utils.IntentKeys;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Shows transaction history for a specific scheme.
 * Transactions are matched by card BIN prefix (from cards table) against scheme.prefix.
 * Void uses the scheme's own server IP/port.
 */
public class SchemeHistoryActivity extends AppCompatActivity {

    private String schemeName;
    private String schemePrefix;
    private final List<TransactionEntity> transactions = new ArrayList<>();
    private TxnAdapter adapter;
    private TextView tvEmpty, tvCount;

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

        tvTitle.setText("Transaction History");
        tvSubtitle.setText(schemeName);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvTransactions);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TxnAdapter();
        rv.setAdapter(adapter);

        // Observe ALL transactions, filter by BIN prefix in-memory
        AppDatabase.getInstance(this).transactionDao()
                .getAllTransactionsLive()
                .observe(this, allTxns -> {
                    transactions.clear();
                    if (allTxns != null && !schemePrefix.isEmpty()) {
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
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(transactions.isEmpty() ? View.VISIBLE : View.GONE);
                    tvCount.setText(transactions.size() + " transactions");
                });
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

    /** Check if transaction's PAN starts with scheme prefix */
    private boolean matchesScheme(TransactionEntity txn) {
        try {
            if (txn.requestHex == null) return false;
            com.example.mysoftpos.iso8583.message.IsoMessage req =
                    new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                            .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker
                                    .hexToBytes(txn.requestHex));
            if (req.hasField(2)) {
                String pan = req.getField(2);
                return pan.startsWith(schemePrefix);
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ── Adapter ──

    private class TxnAdapter extends RecyclerView.Adapter<TxnAdapter.VH> {

        private final DecimalFormat amountFmt;
        private final SimpleDateFormat dateFmt;

        TxnAdapter() {
            DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.getDefault());
            s.setGroupingSeparator(',');
            amountFmt = new DecimalFormat("#,###", s);
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

            h.tvTrace.setText("Trace: " + (txn.traceNumber != null ? txn.traceNumber : "---"));
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

            // Amount — parse DE 4 from request hex, divide by 100 for VND
            String amountDisplay = "---";
            try {
                if (txn.requestHex != null) {
                    com.example.mysoftpos.iso8583.message.IsoMessage reqMsg =
                            new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                                    .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker
                                            .hexToBytes(txn.requestHex));
                    if (reqMsg.hasField(4)) {
                        long rawAmt = Long.parseLong(reqMsg.getField(4));
                        // DE 49 currency: 704=VND (minor=00, divide by 100), 840=USD (keep as cents)
                        String currency = reqMsg.hasField(49) ? reqMsg.getField(49).trim() : "704";
                        if ("704".equals(currency)) {
                            rawAmt = rawAmt / 100;
                        }
                        amountDisplay = amountFmt.format(rawAmt) + ("704".equals(currency) ? " VND" : " USD");
                    }
                }
            } catch (Exception e) {
                // Fallback: try amount field directly
                try {
                    long amt = Long.parseLong(txn.amount);
                    amountDisplay = amountFmt.format(amt) + " VND";
                } catch (Exception ignored) {}
            }
            h.tvAmount.setText(amountDisplay);

            // Card (masked PAN from request DE 2)
            String cardText = "---";
            try {
                if (txn.requestHex != null) {
                    com.example.mysoftpos.iso8583.message.IsoMessage req =
                            new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
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
            } catch (Exception ignored) {}
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

