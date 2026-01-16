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
            String raw = etAmount.getText() == null ? "" : etAmount.getText().toString();
            String d;
            try {
                d = PurchaseFlowData.normalizeAmountDigits(raw);
            } catch (Exception e) {
                Toast.makeText(this, "Số tiền không hợp lệ", Toast.LENGTH_SHORT).show();
                etAmount.requestFocus();
                return;
            }

            if (d.isEmpty() || "0".equals(d)) {
                Toast.makeText(this, "Vui lòng nhập số tiền hợp lệ", Toast.LENGTH_SHORT).show();
                etAmount.requestFocus();
                return;
            }

            long amount;
            try {
                amount = Long.parseLong(d);
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
                f4 = PurchaseFlowData.toIsoAmount12FromDigits(d);
            } catch (Exception e) {
                Toast.makeText(this, "Lỗi format F4: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                etAmount.requestFocus();
                return;
            }

            Intent i = new Intent(this, PurchaseCardActivity.class);
            i.putExtra(PurchaseFlowData.EXTRA_AMOUNT_DIGITS, d);
            i.putExtra(PurchaseFlowData.EXTRA_AMOUNT_F4, f4);
            startActivity(i);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }
}
