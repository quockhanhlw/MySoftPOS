package com.example.mysoftpos;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class PurchaseAmountActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_amount);

        EditText etAmount = findViewById(R.id.etAmount);
        MaterialButton btnConfirm = findViewById(R.id.btnNextToCard);
        
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                String val = etAmount.getText().toString().trim();
                if (val.isEmpty() || Long.parseLong(val) <= 0) {
                    Toast.makeText(this, "Vui lòng nhập số tiền hợp lệ", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                Intent intent = new Intent(this, PurchaseCardActivity.class);
                intent.putExtra("TXN_TYPE", "PURCHASE");
                intent.putExtra("AMOUNT", val);
                startActivity(intent);
            });
        }
    }
}
