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

        // UI References
        // ConstraintLayout rootLayout = findViewById(R.id.rootLayout);

        ImageView ivIcon = findViewById(R.id.ivResultIcon);
        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvReason = findViewById(R.id.tvReason);
        MaterialButton btnClose = findViewById(R.id.btnClose);

        MaterialButton btnDetails = findViewById(R.id.btnDetails);
        TextView tvIsoLog = findViewById(R.id.tvIsoLog);

        ResultType type = (ResultType) getIntent().getSerializableExtra(EXTRA_RESULT_TYPE);
        String messageExtras = getIntent().getStringExtra(EXTRA_MESSAGE);
        String isoResponse = getIntent().getStringExtra(EXTRA_ISO_RESPONSE);
        String isoRequest = getIntent().getStringExtra(EXTRA_ISO_REQUEST);

        StringBuilder logBuilder = new StringBuilder();
        if (isoRequest != null) {
            logBuilder.append("=== REQUEST ===\n").append(isoRequest).append("\n\n");
        }
        if (isoResponse != null) {
            logBuilder.append("=== RESPONSE ===\n").append(isoResponse);
        }

        if (logBuilder.length() > 0) {
            tvIsoLog.setText(logBuilder.toString());
            btnDetails.setVisibility(android.view.View.VISIBLE);
        } else {
            btnDetails.setVisibility(android.view.View.GONE);
        }

        btnDetails.setOnClickListener(v -> {
            if (tvIsoLog.getVisibility() == android.view.View.VISIBLE) {
                tvIsoLog.setVisibility(android.view.View.GONE);
                btnDetails.setText("Xem chi tiết (ISO)");
            } else {
                tvIsoLog.setVisibility(android.view.View.VISIBLE);
                btnDetails.setText("Ẩn chi tiết");
            }
        });

        if (type == null)
            type = ResultType.SYSTEM_ERROR;

        switch (type) {
            case SUCCESS:
                setupUI(ivIcon, tvTitle, btnClose, R.drawable.ic_check_circle, "#4CAF50", "Giao dịch Thành công",
                        "Hoàn tất");

                // Special Balance Logic
                // Special Balance Logic
                String txnTypeStr = getIntent().getStringExtra("TXN_TYPE");
                TxnType txnType = null;
                if (txnTypeStr != null) {
                    try {
                        txnType = TxnType.valueOf(txnTypeStr);
                    } catch (IllegalArgumentException e) {
                    }
                }
                if (txnType == com.example.mysoftpos.iso8583.TxnType.BALANCE_INQUIRY && isoResponse != null) {
                    try {
                        // 1. Convert Hex String back to Bytes
                        // Note: isoResponse is now confirmed to be Hex String from MainDashboard
                        byte[] rawResp = com.example.mysoftpos.iso8583.util.StandardIsoPacker.hexToBytes(isoResponse);

                        // 2. Unpack DE 54
                        String f54 = com.example.mysoftpos.iso8583.util.StandardIsoPacker.unpackField(rawResp, 54);

                        if (f54 != null && f54.length() >= 20) {
                            // Spec: First 20 bytes = Available Balance.
                            // Position 9-20 (Index 8-20) = Amount (n12) with 2 decimal places.
                            String balStr = f54.substring(8, 20); // First block amount

                            try {
                                long val = Long.parseLong(balStr);
                                double amount = val / 100.0; // Implied 2 decimals

                                java.text.NumberFormat nf = java.text.NumberFormat
                                        .getCurrencyInstance(new java.util.Locale("vi", "VN"));
                                String fmt = nf.format(amount);
                                tvReason.setText("Số dư khả dụng: " + fmt);
                            } catch (Exception e) {
                                tvReason.setText("Số dư: " + balStr);
                            }
                            tvTitle.setText("Vấn tin số dư");
                        } else {
                            tvReason.setText("Lỗi định dạng số dư (DE 54): " + f54);
                        }
                    } catch (Exception e) {
                        tvReason.setText("Lỗi hiển thị số dư: " + e.getMessage());
                    }
                } else {
                    tvReason.setText(messageExtras != null ? messageExtras : "Cảm ơn quý khách!");
                }
                break;

            case LIMIT_EXCEEDED:
                setupUI(ivIcon, tvTitle, btnClose, R.drawable.ic_warning, "#FF9800", "Vượt quá hạn mức",
                        "Nhập lại số tiền"); // Orange
                tvReason.setText("Giao dịch vượt quá hạn mức cho phép.");
                break;

            case CARD_EXPIRED:
                setupUI(ivIcon, tvTitle, btnClose, R.drawable.ic_event_busy, "#F44336", "Thẻ đã hết hạn", "Thử lại"); // Red
                tvReason.setText("Thẻ đã hết hạn sử dụng. Vui lòng thử thẻ khác.");
                break;

            case INVALID_CARD:
                setupUI(ivIcon, tvTitle, btnClose, R.drawable.ic_credit_card_off, "#F44336", "Thẻ không hợp lệ",
                        "Thử lại"); // Red
                tvReason.setText("Số thẻ không hợp lệ hoặc không được hỗ trợ.");
                break;

            case SYSTEM_ERROR:
                setupUI(ivIcon, tvTitle, btnClose, R.drawable.ic_cloud_off, "#607D8B", "Lỗi kết nối",
                        "Về màn hình chính"); // Blue Grey
                tvReason.setText(messageExtras != null ? messageExtras : "Lỗi hệ thống hoặc mất kết nối.");
                break;

            case TRANSACTION_FAILED:
                setupUI(ivIcon, tvTitle, btnClose, R.drawable.ic_error, "#F44336", "Giao dịch Thất bại",
                        "Về màn hình chính"); // Red
                tvReason.setText("Lý do: " + (messageExtras != null ? messageExtras : "Không xác định"));
                break;
        }

        // Button Action
        ResultType finalType = type;
        btnClose.setOnClickListener(v -> {
            if (finalType == ResultType.LIMIT_EXCEEDED) {
                // Go back to Amount Input
                Intent i = new Intent(this, MainDashboardActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
            } else if (finalType == ResultType.CARD_EXPIRED || finalType == ResultType.INVALID_CARD) {
                // Try again (Back to Dashboard or Card Input?)
                // Dashboard is safest as "Retry" might imply same transaction params which
                // failed validation
                Intent i = new Intent(this, MainDashboardActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
            } else {
                // Success or Fail -> Home
                Intent i = new Intent(this, MainDashboardActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
            }
            finish();
        });
    }

    private void setupUI(ImageView iv, TextView tv, MaterialButton btn, int resId, String colorHex, String title,
            String btnText) {
        iv.setImageResource(resId);
        int color = Color.parseColor(colorHex);
        iv.setColorFilter(color);
        tv.setTextColor(color);
        tv.setText(title);
        btn.setText(btnText);
        // btn.setBackgroundColor(color); // Optional: tint button to match
    }
}
