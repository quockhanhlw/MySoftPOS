package com.example.mysoftpos.ui.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.entity.TransactionEntity;
import com.example.mysoftpos.data.local.entity.TransactionWithDetails;
import com.example.mysoftpos.ui.base.GlobalViewModelFactory;
import com.example.mysoftpos.di.ServiceLocator;
import com.example.mysoftpos.viewmodel.TransactionDetailViewModel;
import com.example.mysoftpos.ui.BaseActivity;
import com.example.mysoftpos.utils.IntentKeys;
import com.example.mysoftpos.utils.format.AmountFormatUtils;
import com.example.mysoftpos.utils.format.DateTimeFormatUtils;


public class TransactionDetailActivity extends BaseActivity {

    private static final long VOID_WINDOW_MS = 24L * 60L * 60L * 1000L;

    public static final String EXTRA_TRANSACTION_ID = "EXTRA_TRANSACTION_ID";

    private TransactionDetailViewModel viewModel;
    private long transactionId;

    private TextView tvAmount, tvStatus;
    private FrameLayout layoutLoading;
    private Button btnVoid;

    // Detail Rows
    private TextView valDate, valCard, valBank, valMid, valTid, valTrace, valRrn;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_detail);

        transactionId = getIntent().getLongExtra(EXTRA_TRANSACTION_ID, -1);
        if (transactionId == -1) {
            Toast.makeText(this, R.string.txn_detail_invalid_id, Toast.LENGTH_SHORT).show();
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
            ((TextView) rowDate.findViewById(R.id.tvLabel)).setText(R.string.txn_detail_label_date_time);
        }

        View rowCard = findViewById(R.id.rowCard);
        if (rowCard != null) {
            valCard = rowCard.findViewById(R.id.tvValue);
            ((TextView) rowCard.findViewById(R.id.tvLabel)).setText(R.string.txn_detail_label_card_number);
        }

        View rowBank = findViewById(R.id.rowBank);
        if (rowBank != null) {
            valBank = rowBank.findViewById(R.id.tvValue);
            ((TextView) rowBank.findViewById(R.id.tvLabel)).setText(R.string.txn_detail_label_bank_issuer);
        }

        View rowMid = findViewById(R.id.rowMid);
        if (rowMid != null) {
            valMid = rowMid.findViewById(R.id.tvValue);
            ((TextView) rowMid.findViewById(R.id.tvLabel)).setText(R.string.txn_detail_label_mid);
        }

        View rowTid = findViewById(R.id.rowTid);
        if (rowTid != null) {
            valTid = rowTid.findViewById(R.id.tvValue);
            ((TextView) rowTid.findViewById(R.id.tvLabel)).setText(R.string.txn_detail_label_tid);
        }

        View rowTrace = findViewById(R.id.rowTrace);
        if (rowTrace != null) {
            valTrace = rowTrace.findViewById(R.id.tvValue);
            ((TextView) rowTrace.findViewById(R.id.tvLabel)).setText(R.string.txn_detail_label_trace);
        }

        View rowRrn = findViewById(R.id.rowRrn);
        if (rowRrn != null) {
            valRrn = rowRrn.findViewById(R.id.tvValue);
            ((TextView) rowRrn.findViewById(R.id.tvLabel)).setText(R.string.txn_detail_label_rrn);
        }

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#0A2463")); // neoprimary dark
            swipeRefreshLayout.setOnRefreshListener(() -> {
                // Since this uses a ViewModel that fetches from DB via LiveData,
                // we'll trigger a background sync, and the observer will handle UI updates
                new com.example.mysoftpos.data.remote.TransactionSyncManager(TransactionDetailActivity.this)
                        .syncUnsynced();

                // Artificial delay to show the spinner briefly while sync runs
                swipeRefreshLayout.postDelayed(() -> {
                    if (swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(TransactionDetailActivity.this, R.string.history_refreshed, Toast.LENGTH_SHORT)
                                .show();
                    }
                }, 1000);
            });
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
            TransactionEntity txn = cachedTxnDetails.transaction;
            String currencyCode = resolveCurrencyCode(txn);
            String realAmount = resolveDisplayAmount(txn);
            String currencyLabel = "840".equals(currencyCode)
                    ? getString(R.string.currency_usd)
                    : getString(R.string.currency_vnd);

            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.AMOUNT, realAmount);
            intent.putExtra(IntentKeys.CURRENCY_CODE, currencyCode);
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.TXN_ID, cachedTxnDetails.transaction.traceNumber);

            // Date
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.TXN_DATE,
                    DateTimeFormatUtils.formatEpochMillis(cachedTxnDetails.transaction.timestamp));
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

        // Amount: for Balance Inquiry, prefer DE54 balance over stored amount (often 0)
        String displayAmount = resolveDisplayAmount(txn);
        String displayCurrencyCode = resolveCurrencyCode(txn);
        tvAmount.setText(AmountFormatUtils.formatAmountDisplay(displayAmount, displayCurrencyCode));

        // Status
        tvStatus.setText(txn.status);

        // Determine if this is a Purchase transaction (only purchases can be voided)
        boolean isPurchase = isPurchaseTransaction(txn);

        boolean withinVoidWindow = isWithinVoidWindow(txn.timestamp);

        boolean hasVoidPayload = hasVoidRequestPayload(txn);

        if (("APPROVED".equals(txn.status) || "SUCCESS".equals(txn.status))
                && isPurchase && withinVoidWindow && hasVoidPayload) {
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
        valDate.setText(DateTimeFormatUtils.formatEpochMillis(txn.timestamp));

        // Card Details from Relation
        if (txnDetails.card != null) {
            valCard.setText(txnDetails.card.panMasked != null ? txnDetails.card.panMasked : getString(R.string.txn_detail_unknown));
            valBank.setText(txnDetails.card.scheme != null ? txnDetails.card.scheme : getString(R.string.txn_detail_unknown));
        } else {
            valCard.setText(R.string.txn_detail_placeholder_dash);
            valBank.setText(R.string.txn_detail_placeholder_dash);
        }

        // Terminal Details from Relation
        // Note: TransactionWithDetails has `terminal` relation
        if (txnDetails.terminal != null) {
            valTid.setText(txnDetails.terminal.terminalCode != null ? txnDetails.terminal.terminalCode : getString(R.string.txn_detail_placeholder_dash));
        } else {
            valTid.setText(R.string.txn_detail_placeholder_dash);
        }

        // Merchant ID (DE 42) from Request Hex or Terminal
        String mid = getString(R.string.txn_detail_placeholder_dash);
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
        String rrn = getString(R.string.txn_detail_placeholder_dash);
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

    private boolean isPurchaseTransaction(TransactionEntity txn) {
        if (txn.processingCode != null && !txn.processingCode.trim().isEmpty()) {
            return txn.processingCode.startsWith("00");
        }
        try {
            if (txn.requestHex != null) {
                com.example.mysoftpos.iso8583.message.IsoMessage reqMsg = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                        .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker
                                .hexToBytes(txn.requestHex));
                String processingCode = reqMsg.hasField(3) ? reqMsg.getField(3) : "";
                return processingCode.startsWith("00");
            }
        } catch (Exception e) {
            Log.e("TxnDetail", "Parse DE3 for void check", e);
        }
        return false;
    }

    private boolean isBalanceTransaction(TransactionEntity txn) {
        if (txn.processingCode != null && !txn.processingCode.trim().isEmpty()) {
            return txn.processingCode.startsWith("30");
        }
        try {
            if (txn.requestHex != null) {
                com.example.mysoftpos.iso8583.message.IsoMessage reqMsg = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                        .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker
                                .hexToBytes(txn.requestHex));
                String processingCode = reqMsg.hasField(3) ? reqMsg.getField(3) : "";
                return processingCode.startsWith("30");
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private String resolveDisplayAmount(TransactionEntity txn) {
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

    private String resolveCurrencyCode(TransactionEntity txn) {
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

    private void showVoidConfirmation() {
        if (cachedTxnDetails == null || cachedTxnDetails.transaction == null
                || !isPurchaseTransaction(cachedTxnDetails.transaction)) {
            Toast.makeText(this, R.string.void_only_purchase, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isWithinVoidWindow(cachedTxnDetails.transaction.timestamp)) {
            Toast.makeText(this, R.string.txn_void_expired_24h, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasVoidRequestPayload(cachedTxnDetails.transaction)) {
            Toast.makeText(this, R.string.txn_error_original_request_missing, Toast.LENGTH_SHORT).show();
            return;
        }
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

    private boolean isWithinVoidWindow(long txnTimestamp) {
        long ageMs = System.currentTimeMillis() - txnTimestamp;
        return ageMs >= 0 && ageMs <= VOID_WINDOW_MS;
    }

    private boolean hasVoidRequestPayload(TransactionEntity txn) {
        if (txn == null || txn.requestHex == null) {
            return false;
        }
        String hex = txn.requestHex.trim();
        if (hex.isEmpty() || (hex.length() % 2 != 0)) {
            return false;
        }
        for (int i = 0; i < hex.length(); i++) {
            if (Character.digit(hex.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
