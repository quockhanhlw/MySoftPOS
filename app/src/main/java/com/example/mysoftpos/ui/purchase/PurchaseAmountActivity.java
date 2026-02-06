package com.example.mysoftpos.ui.purchase;

import com.example.mysoftpos.R;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Payment Amount Entry Screen with custom numpad and currency selector.
 * Features: VND/USD toggle by swipe, blinking cursor, numpad shown on tap.
 */
public class PurchaseAmountActivity extends AppCompatActivity {

    private TextView tvAmountDisplay;
    private TextView tvCurrency;
    private View cursorView;
    private LinearLayout numpadContainer;
    private StringBuilder amountBuilder = new StringBuilder();
    private ObjectAnimator cursorAnimator;
    private boolean numpadVisible = false;
    private static final int MAX_DIGITS = 12;

    // Currency state
    private String[] currencies = { "VND", "USD" };
    private String[] currencyCodes = { "704", "840" };
    private int currentCurrencyIndex = 0; // 0 = VND, 1 = USD

    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_amount);

        // Initialize views
        ImageButton btnBack = findViewById(R.id.btnBack);
        tvAmountDisplay = findViewById(R.id.tvAmountDisplay);
        tvCurrency = findViewById(R.id.tvCurrency);
        cursorView = findViewById(R.id.cursorView);
        numpadContainer = findViewById(R.id.numpadContainer);
        LinearLayout amountContainer = findViewById(R.id.amountContainer);
        View currencySelector = findViewById(R.id.currencySelector);
        MaterialButton btnCharge = findViewById(R.id.btnCharge);

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Tap on amount to show numpad
        amountContainer.setOnClickListener(v -> toggleNumpad());

        // Currency swipe gesture detector
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffY) > 50 && Math.abs(velocityY) > 100) {
                    if (diffY < 0) {
                        // Swipe up - next currency
                        switchCurrency(1);
                    } else {
                        // Swipe down - previous currency
                        switchCurrency(-1);
                    }
                    return true;
                }
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // Tap to toggle currency
                switchCurrency(1);
                return true;
            }
        });

        currencySelector.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

        // Setup numpad buttons
        setupNumpadButtons();

        // Backspace button
        ImageButton btnBackspace = findViewById(R.id.btnBackspace);
        btnBackspace.setOnClickListener(v -> onBackspaceClick());

        // Charge button
        btnCharge.setOnClickListener(v -> onChargeClick());

        // Start cursor blinking animation
        startCursorAnimation();

        // Initialize display
        updateAmountDisplay();
        updateCurrencyDisplay();
    }

    private String getUsername() {
        String u = getIntent().getStringExtra("USERNAME");
        return u != null ? u : "Guest";
    }

    private void switchCurrency(int direction) {
        currentCurrencyIndex = (currentCurrencyIndex + direction + currencies.length) % currencies.length;
        updateCurrencyDisplay();

        // Animate currency change
        tvCurrency.animate()
                .scaleX(1.2f).scaleY(1.2f)
                .setDuration(100)
                .withEndAction(() -> tvCurrency.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(100)
                        .start())
                .start();
    }

    private void updateCurrencyDisplay() {
        tvCurrency.setText(currencies[currentCurrencyIndex]);
    }

    public String getCurrentCurrencyCode() {
        return currencyCodes[currentCurrencyIndex];
    }

    public String getCurrentCurrency() {
        return currencies[currentCurrencyIndex];
    }

    private void startCursorAnimation() {
        cursorAnimator = ObjectAnimator.ofFloat(cursorView, "alpha", 1f, 0f, 1f);
        cursorAnimator.setDuration(1000);
        cursorAnimator.setRepeatCount(ValueAnimator.INFINITE);
        cursorAnimator.setInterpolator(new LinearInterpolator());
        cursorAnimator.start();
    }

    private void toggleNumpad() {
        if (numpadVisible) {
            numpadContainer.setVisibility(View.GONE);
            numpadVisible = false;
        } else {
            numpadContainer.setVisibility(View.VISIBLE);
            numpadVisible = true;
        }
    }

    private void setupNumpadButtons() {
        int[] buttonIds = {
                R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        };

        for (int id : buttonIds) {
            Button btn = findViewById(id);
            if (btn != null) {
                btn.setOnClickListener(v -> onDigitClick(((Button) v).getText().toString()));
            }
        }
    }

    private void onDigitClick(String digit) {
        // Prevent leading zeros
        if (amountBuilder.length() == 0 && digit.equals("0")) {
            return;
        }

        // Limit max digits
        if (amountBuilder.length() >= MAX_DIGITS) {
            return;
        }

        amountBuilder.append(digit);
        updateAmountDisplay();
    }

    private void onBackspaceClick() {
        if (amountBuilder.length() > 0) {
            amountBuilder.deleteCharAt(amountBuilder.length() - 1);
            updateAmountDisplay();
        }
    }

    private void updateAmountDisplay() {
        if (amountBuilder.length() == 0) {
            tvAmountDisplay.setText("0");
        } else {
            // Format with thousand separators
            try {
                long amount = Long.parseLong(amountBuilder.toString());
                DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
                symbols.setGroupingSeparator(',');
                DecimalFormat formatter = new DecimalFormat("#,###", symbols);
                tvAmountDisplay.setText(formatter.format(amount));
            } catch (NumberFormatException e) {
                tvAmountDisplay.setText(amountBuilder.toString());
            }
        }
    }

    private void onChargeClick() {
        if (amountBuilder.length() == 0) {
            Toast.makeText(this, "Vui lòng nhập số tiền", Toast.LENGTH_SHORT).show();
            return;
        }

        long amount = Long.parseLong(amountBuilder.toString());
        if (amount <= 0) {
            Toast.makeText(this, "Số tiền phải lớn hơn 0", Toast.LENGTH_SHORT).show();
            return;
        }

        // Navigate to card tap screen with currency info
        Intent intent = new Intent(this, PurchaseCardActivity.class);
        intent.putExtra("TXN_TYPE", "PURCHASE");
        intent.putExtra("AMOUNT", amountBuilder.toString());
        intent.putExtra("CURRENCY", getCurrentCurrency()); // VND or USD
        intent.putExtra("CURRENCY_CODE", getCurrentCurrencyCode()); // 704 or 840
        intent.putExtra("USERNAME", getUsername());
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cursorAnimator != null) {
            cursorAnimator.cancel();
        }
    }
}
