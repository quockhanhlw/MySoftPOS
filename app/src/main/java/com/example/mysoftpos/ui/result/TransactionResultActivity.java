package com.example.mysoftpos.ui.result;

import com.example.mysoftpos.R;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.ui.dashboard.MainDashboardActivity;

import android.content.Intent;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.media.MediaScannerConnection;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.example.mysoftpos.ui.BaseActivity;
import com.example.mysoftpos.utils.format.AmountFormatUtils;
import com.example.mysoftpos.utils.format.DateTimeFormatUtils;

public class TransactionResultActivity extends BaseActivity {

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

    private boolean enableSaveReceiptAction;
    private android.view.View receiptCardView;
    private String currentTxnId;
    private final ActivityResultLauncher<String> storagePermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    saveResultImageToGallery();
                } else {
                    android.widget.Toast.makeText(this, getString(R.string.txn_save_receipt_permission_denied),
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            });

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
        android.widget.LinearLayout layoutActions = findViewById(R.id.layoutActions);
        receiptCardView = findViewById(R.id.cardReceipt);

        // Get Data
        ResultType type = (ResultType) getIntent().getSerializableExtra(EXTRA_RESULT_TYPE);
        String amount = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.AMOUNT);
        String currency = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.CURRENCY);
        String currencyCode = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.CURRENCY_CODE);
        String maskedPan = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.MASKED_PAN);
        String txnDate = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.TXN_DATE);
        String txnId = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.TXN_ID);
        String txnTypeStr = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.TXN_TYPE);
        currentTxnId = txnId;

        if (type == null)
            type = ResultType.SYSTEM_ERROR;

        enableSaveReceiptAction = type == ResultType.SUCCESS;

        // Set Common Data
        tvTxnId.setText(txnId != null ? txnId : getString(R.string.txn_detail_placeholder_dash));
        String normalizedDate = DateTimeFormatUtils.normalizeDisplayTimestamp(txnDate);
        tvDate.setText(normalizedDate != null ? normalizedDate : getString(R.string.txn_detail_placeholder_dash));
        tvCardNum.setText(maskedPan != null ? maskedPan : getString(R.string.txn_result_masked_pan_placeholder));
        tvType.setText(txnTypeStr != null ? txnTypeStr : getString(R.string.txn_result_type_default));

        // Format Amount
        if (amount != null && !"OVERFLOW".equals(amount)) {
            String resolvedCurrencyCode = currencyCode;
            if (resolvedCurrencyCode == null || resolvedCurrencyCode.trim().isEmpty()) {
                resolvedCurrencyCode = currency;
            }
            String formattedAmount = AmountFormatUtils.formatAmountDisplay(amount, resolvedCurrencyCode);
            tvAmount.setText(formattedAmount);
        } else if ("OVERFLOW".equals(amount)) {
            tvAmount.setText(R.string.txn_result_amount_overflow);
        } else {
            tvAmount.setText(R.string.txn_detail_placeholder_dash);
        }

        // Stylize based on Result
        if (type == ResultType.SUCCESS) {
            // Checked Green Default
            tvTitle.setText(R.string.txn_approved);
            tvSubtitle.setText(R.string.txn_approved_subtitle);
            tvStatus.setText(R.string.txn_approved_status);
            tvStatus.setTextColor(Color.parseColor("#22C55E")); // Green

            ivIcon.setImageResource(R.drawable.ic_check);
            // layoutIcon background is already green circle
            bgHeader.setBackgroundColor(Color.parseColor("#D1FAE5")); // Light Green

            // Special Label for Balance Inquiry
            if (TxnType.BALANCE_INQUIRY.name().equals(txnTypeStr)) {
                TextView tvAmountLabel = findViewById(R.id.tvAmountLabel);
                String balanceType = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.BALANCE_TYPE);
                if (tvAmountLabel != null) {
                    if (balanceType != null) {
                        tvAmountLabel.setText(getString(R.string.txn_result_balance_type_format, balanceType));
                    } else {
                        tvAmountLabel.setText(R.string.txn_result_available_balance);
                    }
                }
            }

        } else {
            // Failure Red
            if (tvTitle != null) {
                tvTitle.setText(R.string.txn_failed_title);
                tvTitle.setTextColor(Color.parseColor("#EF4444"));
            }
            if (tvSubtitle != null)
                tvSubtitle.setText(getIntent().getStringExtra(EXTRA_MESSAGE));
            if (tvStatus != null) {
                tvStatus.setText(R.string.txn_failed_status);
                tvStatus.setTextColor(Color.parseColor("#EF4444"));
            }

            if (ivIcon != null)
                ivIcon.setImageResource(R.drawable.ic_close);

            if (layoutIcon != null) {
                android.graphics.drawable.GradientDrawable bgShape = new android.graphics.drawable.GradientDrawable();
                bgShape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                bgShape.setColor(Color.parseColor("#EF4444"));
                layoutIcon.setBackground(bgShape);
            }

            if (bgHeader != null)
                bgHeader.setBackgroundColor(Color.parseColor("#FEE2E2")); // Light Red

            if (layoutActions != null) {
                layoutActions.setVisibility(android.view.View.GONE);
            }
        }

        // Actions
        btnClose.setOnClickListener(v -> {
            Intent i = new Intent(this, MainDashboardActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });

        btnPrint.setOnClickListener(v -> {
            if (enableSaveReceiptAction) {
                requestPermissionAndSaveImage();
                return;
            }
            android.widget.Toast.makeText(this, getString(R.string.msg_printing_receipt), android.widget.Toast.LENGTH_SHORT)
                    .show();
        });

        btnShare.setOnClickListener(v -> {
            android.widget.Toast.makeText(this, getString(R.string.msg_coming_soon), android.widget.Toast.LENGTH_SHORT)
                    .show();
        });

        if (enableSaveReceiptAction) {
            btnPrint.setText(R.string.txn_save_image);
        }
    }


    private void requestPermissionAndSaveImage() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
                return;
            }
        }
        saveResultImageToGallery();
    }

    private void saveResultImageToGallery() {
        if (receiptCardView == null || receiptCardView.getWidth() == 0 || receiptCardView.getHeight() == 0) {
            android.widget.Toast.makeText(this, getString(R.string.txn_save_receipt_failed), android.widget.Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        Bitmap bitmap = Bitmap.createBitmap(receiptCardView.getWidth(), receiptCardView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        receiptCardView.draw(canvas);

        String txnPart = currentTxnId == null || currentTxnId.trim().isEmpty() ? "txn" : currentTxnId.trim();
        String fileName = "MySoftPOS_" + txnPart + "_" + System.currentTimeMillis() + ".png";

        if (!saveWithMediaStore(bitmap, fileName)) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && saveWithLegacyFile(bitmap, fileName)) {
                android.widget.Toast.makeText(this, getString(R.string.txn_save_receipt_success), android.widget.Toast.LENGTH_SHORT)
                        .show();
            } else {
                android.widget.Toast.makeText(this, getString(R.string.txn_save_receipt_failed), android.widget.Toast.LENGTH_SHORT)
                        .show();
            }
            return;
        }

        android.widget.Toast.makeText(this, getString(R.string.txn_save_receipt_success), android.widget.Toast.LENGTH_SHORT)
                .show();
    }

    private boolean saveWithMediaStore(Bitmap bitmap, String fileName) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Save under DCIM so common gallery apps index and show the image quickly.
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/MySoftPOS");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        android.content.ContentResolver resolver = getContentResolver();
        Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (imageUri == null) {
            return false;
        }

        try (java.io.OutputStream outputStream = resolver.openOutputStream(imageUri)) {
            if (outputStream == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                throw new java.io.IOException("Cannot write image");
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues finalizeValues = new ContentValues();
                finalizeValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(imageUri, finalizeValues, null, null);
            }
            return true;
        } catch (Exception e) {
            resolver.delete(imageUri, null, null);
            return false;
        }
    }

    private boolean saveWithLegacyFile(Bitmap bitmap, String fileName) {
        try {
            java.io.File dir = new java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    "MySoftPOS");
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }

            java.io.File outFile = new java.io.File(dir, fileName);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                    return false;
                }
            }

            MediaScannerConnection.scanFile(this, new String[] { outFile.getAbsolutePath() },
                    new String[] { "image/png" }, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Helper removed as logic is inline for custom layout styling
    // private void setupUI(...) {}
}
