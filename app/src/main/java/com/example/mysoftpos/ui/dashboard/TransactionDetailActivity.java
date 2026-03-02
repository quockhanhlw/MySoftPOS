package com.example.mysoftpos.ui.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.entity.TransactionEntity;
import com.example.mysoftpos.data.local.entity.TransactionWithDetails;
import com.example.mysoftpos.ui.base.GlobalViewModelFactory;
import com.example.mysoftpos.di.ServiceLocator;
import com.example.mysoftpos.viewmodel.TransactionDetailViewModel;
import com.example.mysoftpos.viewmodel.TransactionState;
import com.example.mysoftpos.ui.BaseActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TransactionDetailActivity extends BaseActivity {

    public static final String EXTRA_TRANSACTION_ID = "EXTRA_TRANSACTION_ID";

    private TransactionDetailViewModel viewModel;
    private long transactionId;

    private TextView tvAmount, tvStatus;
    private FrameLayout layoutLoading;
    private Button btnVoid;

    // Detail Rows
    private TextView valDate, valCard, valBank, valMid, valTid, valTrace, valRrn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_detail);

        transactionId = getIntent().getLongExtra(EXTRA_TRANSACTION_ID, -1);
        if (transactionId == -1) {
            Toast.makeText(this, "Invalid Transaction ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViewModel();
        initViews();
        setupObservers();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnVoid.setOnClickListener(v -> showVoidConfirmation());
    }

    private void initViewModel() {
        GlobalViewModelFactory factory = new GlobalViewModelFactory(ServiceLocator.getInstance(this));
        viewModel = new ViewModelProvider(this, factory).get(TransactionDetailViewModel.class);
    }

    private void initViews() {
        tvAmount = findViewById(R.id.tvDetailAmount);
        tvStatus = findViewById(R.id.tvDetailStatus);
        layoutLoading = findViewById(R.id.layoutLoading);
        btnVoid = findViewById(R.id.btnVoid);

        // Bind Rows with safer lookups
        View rowDate = findViewById(R.id.rowDate);
        if (rowDate != null) {
            valDate = rowDate.findViewById(R.id.tvValue);
            ((TextView) rowDate.findViewById(R.id.tvLabel)).setText("Date / Time");
        }

        View rowCard = findViewById(R.id.rowCard);
        if (rowCard != null) {
            valCard = rowCard.findViewById(R.id.tvValue);
            ((TextView) rowCard.findViewById(R.id.tvLabel)).setText("Card Number");
        }

        View rowBank = findViewById(R.id.rowBank);
        if (rowBank != null) {
            valBank = rowBank.findViewById(R.id.tvValue);
            ((TextView) rowBank.findViewById(R.id.tvLabel)).setText("Bank / Issuer");
        }

        View rowMid = findViewById(R.id.rowMid);
        if (rowMid != null) {
            valMid = rowMid.findViewById(R.id.tvValue);
            ((TextView) rowMid.findViewById(R.id.tvLabel)).setText("Merchant ID");
        }

        View rowTid = findViewById(R.id.rowTid);
        if (rowTid != null) {
            valTid = rowTid.findViewById(R.id.tvValue);
            ((TextView) rowTid.findViewById(R.id.tvLabel)).setText("Terminal ID");
        }

        View rowTrace = findViewById(R.id.rowTrace);
        if (rowTrace != null) {
            valTrace = rowTrace.findViewById(R.id.tvValue);
            ((TextView) rowTrace.findViewById(R.id.tvLabel)).setText("Trace Number");
        }

        View rowRrn = findViewById(R.id.rowRrn);
        if (rowRrn != null) {
            valRrn = rowRrn.findViewById(R.id.tvValue);
            ((TextView) rowRrn.findViewById(R.id.tvLabel)).setText("RRN");
        }
    }

    // Cache current transaction data for result screen
    private TransactionWithDetails cachedTxnDetails;

    private void setupObservers() {
        viewModel.getTransaction(transactionId).observe(this, txnDetails -> {
            if (txnDetails != null && txnDetails.transaction != null) {
                cachedTxnDetails = txnDetails;
                updateUI(txnDetails);
            }
        });

        viewModel.getState().observe(this, state -> {
            layoutLoading.setVisibility(state.isLoading ? View.VISIBLE : View.GONE);
            btnVoid.setEnabled(!state.isLoading);

            if (state.isSuccess) {
                navigateToResultScreen(true, state.message);
            } else if (state.message != null && !state.isLoading) {
                navigateToResultScreen(false, state.message);
            }
        });
    }

    private void navigateToResultScreen(boolean success, String message) {
        Intent intent = new Intent(this, com.example.mysoftpos.ui.result.TransactionResultActivity.class);

        if (success) {
            intent.putExtra(com.example.mysoftpos.ui.result.TransactionResultActivity.EXTRA_RESULT_TYPE,
                    com.example.mysoftpos.ui.result.TransactionResultActivity.ResultType.SUCCESS);
        } else {
            intent.putExtra(com.example.mysoftpos.ui.result.TransactionResultActivity.EXTRA_RESULT_TYPE,
                    com.example.mysoftpos.ui.result.TransactionResultActivity.ResultType.TRANSACTION_FAILED);
        }
        intent.putExtra(com.example.mysoftpos.ui.result.TransactionResultActivity.EXTRA_MESSAGE, message);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.TXN_TYPE, "VOID");

        // Pass transaction data if available
        if (cachedTxnDetails != null && cachedTxnDetails.transaction != null) {
            // Parse currency from requestHex DE 49
            String currencyCode = "704";
            String currencyLabel = "VND";
            String realAmount = cachedTxnDetails.transaction.amount;
            try {
                if (cachedTxnDetails.transaction.requestHex != null) {
                    com.example.mysoftpos.iso8583.message.IsoMessage req = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                            .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker
                                    .hexToBytes(cachedTxnDetails.transaction.requestHex));
                    if (req.hasField(49)) {
                        currencyCode = req.getField(49).trim();
                        if ("840".equals(currencyCode)) currencyLabel = "USD";
                    }
                    // Use DE 4 for accurate amount, convert to real value
                    if (req.hasField(4)) {
                        long rawDe4 = Long.parseLong(req.getField(4).trim());
                        if ("704".equals(currencyCode)) {
                            rawDe4 = rawDe4 / 100; // VND: remove 2 trailing zeros
                        }
                        realAmount = String.valueOf(rawDe4);
                    }
                }
            } catch (Exception e) { /* ignore */ }

            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.AMOUNT, realAmount);
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.TXN_ID, cachedTxnDetails.transaction.traceNumber);

            // Date
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss dd/MM/yyyy",
                    java.util.Locale.getDefault());
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.TXN_DATE,
                    sdf.format(new java.util.Date(cachedTxnDetails.transaction.timestamp)));
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.SUCCESS, true);

            // Card
            if (cachedTxnDetails.card != null) {
                intent.putExtra(com.example.mysoftpos.utils.IntentKeys.MASKED_PAN, cachedTxnDetails.card.panMasked);
            }

            intent.putExtra("CURRENCY", currencyLabel);
        }

        startActivity(intent);
        finish();
    }

    private void updateUI(TransactionWithDetails txnDetails) {
        TransactionEntity txn = txnDetails.transaction;

        // Amount
        try {
            long amt = Long.parseLong(txn.amount);
            java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols(Locale.getDefault());
            symbols.setGroupingSeparator(',');
            java.text.DecimalFormat bucket = new java.text.DecimalFormat("#,###", symbols);
            tvAmount.setText(bucket.format(amt));
        } catch (Exception e) {
            tvAmount.setText(txn.amount);
        }

        // Status
        tvStatus.setText(txn.status);

        // Determine if this is a Purchase transaction (only purchases can be voided)
        boolean isPurchase = false;
        try {
            if (txn.requestHex != null) {
                com.example.mysoftpos.iso8583.message.IsoMessage reqMsg =
                        new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                                .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker
                                        .hexToBytes(txn.requestHex));
                String processingCode = reqMsg.hasField(3) ? reqMsg.getField(3) : "";
                // Purchase = 000000, Cash = 010000; Balance = 300000
                isPurchase = processingCode.startsWith("00");
            }
        } catch (Exception e) {
            Log.e("TxnDetail", "Parse DE3 for void check", e);
        }

        if (("APPROVED".equals(txn.status) || "SUCCESS".equals(txn.status)) && isPurchase) {
            tvStatus.setBackgroundResource(R.drawable.bg_status_pill_success);
            tvStatus.setTextColor(0xFF4CAF50);
            btnVoid.setVisibility(View.VISIBLE);
        } else if ("APPROVED".equals(txn.status) || "SUCCESS".equals(txn.status)) {
            tvStatus.setBackgroundResource(R.drawable.bg_status_pill_success);
            tvStatus.setTextColor(0xFF4CAF50);
            btnVoid.setVisibility(View.GONE);
        } else if ("REVERSED".equals(txn.status) || "VOIDED".equals(txn.status)) {
            tvStatus.setBackgroundResource(R.drawable.bg_status_pill_neutral); // Gray
            tvStatus.setTextColor(0xFF757575);
            btnVoid.setVisibility(View.GONE);
        } else {
            tvStatus.setBackgroundResource(R.drawable.bg_status_pill_error); // Red
            tvStatus.setTextColor(0xFFF44336);
            btnVoid.setVisibility(View.GONE);
        }

        // Details
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault());
        valDate.setText(sdf.format(new Date(txn.timestamp)));

        // Card Details from Relation
        if (txnDetails.card != null) {
            valCard.setText(txnDetails.card.panMasked != null ? txnDetails.card.panMasked : "Unknown");
            valBank.setText(txnDetails.card.scheme != null ? txnDetails.card.scheme : "Unknown");
        } else {
            valCard.setText("---");
            valBank.setText("---");
        }

        // Terminal Details from Relation
        // Note: TransactionWithDetails has `terminal` relation
        if (txnDetails.terminal != null) {
            valTid.setText(txnDetails.terminal.terminalCode != null ? txnDetails.terminal.terminalCode : "---");
        } else {
            valTid.setText("---");
        }

        // Merchant ID (DE 42) from Request Hex or Terminal
        String mid = "---";
        try {
            if (txn.requestHex != null) {
                com.example.mysoftpos.iso8583.message.IsoMessage req = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                        .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker.hexToBytes(txn.requestHex));
                if (req.hasField(42)) {
                    mid = req.getField(42);
                }
            }
        } catch (Exception e) {
            Log.e("TxnDetail", "Parse MID", e);
        }
        valMid.setText(mid);

        valTrace.setText(txn.traceNumber);

        // RRN (DE 37) from Response Hex
        String rrn = "---";
        try {
            if (txn.responseHex != null) {
                com.example.mysoftpos.iso8583.message.IsoMessage resp = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                        .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker.hexToBytes(txn.responseHex));
                if (resp.hasField(37)) {
                    rrn = resp.getField(37);
                }
            }
        } catch (Exception e) {
            Log.e("TxnDetail", "Parse RRN", e);
        }
        valRrn.setText(rrn);
    }

    private void showVoidConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.void_confirm_title)
                .setMessage(R.string.void_confirm_message)
                .setPositiveButton(R.string.void_confirm_yes, (dialog, which) -> {
                    String schemeName = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.SCHEME);
                    viewModel.voidTransaction(transactionId, schemeName);
                })
                .setNegativeButton(R.string.void_confirm_no, null)
                .show();
    }
}
