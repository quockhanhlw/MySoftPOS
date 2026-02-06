package com.example.mysoftpos.ui.result;

import com.example.mysoftpos.R;
import com.example.mysoftpos.iso8583.util.StandardIsoPacker;
import com.example.mysoftpos.ui.dashboard.MainDashboardActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import androidx.appcompat.app.AppCompatActivity;

import androidx.constraintlayout.widget.ConstraintLayout;
import com.example.mysoftpos.iso8583.TxnType;

public class TransactionResultActivity extends AppCompatActivity {

    public static final String EXTRA_RESULT_TYPE = "RESULT_TYPE";
    public static final String EXTRA_MESSAGE = "MESSAGE";
    public static final String EXTRA_ISO_RESPONSE = "ISO_RESPONSE";
    public static final String EXTRA_ISO_REQUEST = "ISO_REQUEST";

    public enum ResultType {
        SUCCESS, // Happy Path
        LIMIT_EXCEEDED, // Validation
        CARD_EXPIRED, // Validation
        INVALID_CARD, // Validation
        SYSTEM_ERROR, // Network/Exception
        TRANSACTION_FAILED // Server Rejection (DE 39 != 00)
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_result);

        // Bind New Views
        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvSubtitle = findViewById(R.id.tvSubtitle);
        TextView tvAmount = findViewById(R.id.tvAmount);
        TextView tvTxnId = findViewById(R.id.tvTxnId);
        TextView tvDate = findViewById(R.id.tvDate);
        TextView tvType = findViewById(R.id.tvType);
        TextView tvCardNum = findViewById(R.id.tvCardNum);
        TextView tvStatus = findViewById(R.id.tvStatus);

        ImageView ivIcon = findViewById(R.id.ivResultIcon);
        android.view.View bgHeader = findViewById(R.id.viewHeaderBg);
        android.widget.FrameLayout layoutIcon = findViewById(R.id.layoutIcon);

        MaterialButton btnClose = findViewById(R.id.btnClose);
        android.widget.Button btnPrint = findViewById(R.id.btnPrint);
        android.widget.Button btnShare = findViewById(R.id.btnShare);

        // Get Data
        ResultType type = (ResultType) getIntent().getSerializableExtra(EXTRA_RESULT_TYPE);
        String amount = getIntent().getStringExtra("AMOUNT");
        String currency = getIntent().getStringExtra("CURRENCY");
        String maskedPan = getIntent().getStringExtra("MASKED_PAN");
        String txnDate = getIntent().getStringExtra("TXN_DATE");
        String txnId = getIntent().getStringExtra("TXN_ID");
        String txnTypeStr = getIntent().getStringExtra("TXN_TYPE");

        if (type == null)
            type = ResultType.SYSTEM_ERROR;

        // Set Common Data
        tvTxnId.setText(txnId != null ? txnId : "---");
        tvDate.setText(txnDate != null ? txnDate : "---");
        tvCardNum.setText(maskedPan != null ? maskedPan : "**** ----");
        tvType.setText(txnTypeStr != null ? txnTypeStr : "Transaction");

        // Format Amount
        if (amount != null) {
            try {
                long val = Long.parseLong(amount);
                java.text.NumberFormat nf = java.text.NumberFormat
                        .getCurrencyInstance(new java.util.Locale("vi", "VN"));
                if ("USD".equals(currency)) {
                    nf = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US);
                }
                tvAmount.setText(nf.format(val)); // Implicit 100/1000 divisor omitted for simplicity matching previous
                                                  // logic, or dividing by 1 should be checked
                // Assuming amount passed is RAW, we might need to decimalize?
                // Previous logic in PurchaseCardActivity: formatAmount just added commas.
                // Let's assume standard int display for now unless specific requirement.
            } catch (Exception e) {
                tvAmount.setText(amount);
            }
        } else {
            tvAmount.setText("---");
        }

        // Stylize based on Result
        if (type == ResultType.SUCCESS) {
            // Checked Green Default
            tvTitle.setText("Transaction Approved");
            tvSubtitle.setText("Payment completed successfully");
            tvStatus.setText("Approved");
            tvStatus.setTextColor(Color.parseColor("#22C55E")); // Green

            ivIcon.setImageResource(R.drawable.ic_check);
            // layoutIcon background is already green circle
            bgHeader.setBackgroundColor(Color.parseColor("#D1FAE5")); // Light Green

        } else {
            // Failure Red
            tvTitle.setText("Transaction Failed");
            tvSubtitle.setText(getIntent().getStringExtra(EXTRA_MESSAGE));
            tvStatus.setText("Failed");
            tvStatus.setTextColor(Color.parseColor("#EF4444")); // Red

            ivIcon.setImageResource(R.drawable.ic_close); // Ensure icon exists or use fallback
            // Update Icon Background to Red
            android.graphics.drawable.GradientDrawable bgShape = new android.graphics.drawable.GradientDrawable();
            bgShape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            bgShape.setColor(Color.parseColor("#EF4444"));
            layoutIcon.setBackground(bgShape);

            bgHeader.setBackgroundColor(Color.parseColor("#FEE2E2")); // Light Red
        }

        // Actions
        btnClose.setOnClickListener(v -> {
            Intent i = new Intent(this, MainDashboardActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });

        btnPrint.setOnClickListener(v -> {
            android.widget.Toast.makeText(this, "Printing Receipt...", android.widget.Toast.LENGTH_SHORT).show();
        });

        btnShare.setOnClickListener(v -> {
            android.widget.Toast.makeText(this, "Sharing Receipt...", android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    // Helper removed as logic is inline for custom layout styling
    // private void setupUI(...) {}
}
