package com.example.mysoftpos;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mysoftpos.iso8583.PurchaseFlowData;
import com.example.mysoftpos.ui.AmountInputFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/** Step 1: user nhập số tiền. */
public class PurchaseAmountActivity extends AppCompatActivity {

    private static final long MAX_AMOUNT_VND = 100_000_000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_amount);

        TextInputEditText etAmount = findViewById(R.id.etAmount);
        MaterialButton btnNext = findViewById(R.id.btnNextToCard);

        etAmount.addTextChangedListener(new AmountInputFormatter(null));

        btnNext.setOnClickListener(v -> {
            String raw = etAmount.getText() == null ? "" : etAmount.getText().toString().trim();

            final String digits;
            try {
                digits = PurchaseFlowData.normalizeAmountDigits(raw);
            } catch (Exception e) {
                Toast.makeText(this, "Số tiền không hợp lệ", Toast.LENGTH_SHORT).show();
                etAmount.requestFocus();
                return;
            }

            // normalize leading zeros for business validation
            String normalized = digits.replaceFirst("^0+(?!$)", "");
            if (normalized.isEmpty() || "0".equals(normalized)) {
                Toast.makeText(this, "Vui lòng nhập số tiền hợp lệ", Toast.LENGTH_SHORT).show();
                etAmount.requestFocus();
                return;
            }

            final long amount;
            try {
                amount = Long.parseLong(normalized);
            } catch (Exception e) {
                Toast.makeText(this, "Số tiền không hợp lệ", Toast.LENGTH_SHORT).show();
                etAmount.requestFocus();
                return;
            }

            if (amount > MAX_AMOUNT_VND) {
                Toast.makeText(this, "Số tiền tối đa là 100.000.000 đ", Toast.LENGTH_SHORT).show();
                etAmount.requestFocus();
                return;
            }

            final String f4;
            try {
                // ensure strictly 12n
                f4 = PurchaseFlowData.toIsoAmount12FromDigits(normalized);
            } catch (Exception e) {
                Toast.makeText(this, "Lỗi format số tiền", Toast.LENGTH_SHORT).show();
                etAmount.requestFocus();
                return;
            }

            // Show Payment Selection Dialog
            showPaymentMethodSelection(normalized, f4);
        });
    }

    private void showPaymentMethodSelection(String amountDigits, String amountF4) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_payment_method, null);
        dialog.setContentView(view);
        
        view.findViewById(R.id.cardNfc).setOnClickListener(v -> {
            dialog.dismiss();
            navigateToCardActivity(amountDigits, amountF4, true);
        });

        view.findViewById(R.id.cardManual).setOnClickListener(v -> {
            dialog.dismiss();
            navigateToCardActivity(amountDigits, amountF4, false);
        });

        dialog.show();
    }

    private void navigateToCardActivity(String amountDigits, String amountF4, boolean isNfcMode) {
        Intent i = new Intent(this, PurchaseCardActivity.class);
        i.putExtra(PurchaseFlowData.EXTRA_AMOUNT_DIGITS, amountDigits);
        i.putExtra(PurchaseFlowData.EXTRA_AMOUNT_F4, amountF4);
        i.putExtra("EXTRA_MODE_NFC", isNfcMode);
        startActivity(i);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
}
