package com.example.mysoftpos;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mysoftpos.iso8583.HexUtil;
import com.example.mysoftpos.iso8583.IsoHeader;
import com.example.mysoftpos.iso8583.IsoMessage;
import com.example.mysoftpos.iso8583.IsoMessageStore;
import com.example.mysoftpos.iso8583.IsoPacker;
import com.example.mysoftpos.iso8583.IsoRequestBuilder;
import com.example.mysoftpos.iso8583.PurchaseFlowData;
import com.example.mysoftpos.iso8583.TraceManager;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.iso8583.TransactionContext;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Step 2: user nhập thông tin thẻ, app build ISO8583 + header + pack. */
public class PurchaseCardActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "SoftPOSConfig";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_card);

        String f4 = getIntent().getStringExtra(PurchaseFlowData.EXTRA_AMOUNT_F4);
        if (f4 == null) {
            Toast.makeText(this, "Missing amount", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        EditText etPan = findViewById(R.id.etPan);
        EditText etExpiry = findViewById(R.id.etExpiry);
        EditText etCvv = findViewById(R.id.etCvv);
        Button btnPay = findViewById(R.id.btnPay);
        View vGlowRing = findViewById(R.id.vGlowRing);

        // amount digits to show on success
        String amountDigits = getIntent().getStringExtra(PurchaseFlowData.EXTRA_AMOUNT_DIGITS);
        if (amountDigits == null) amountDigits = "0";
        final String finalAmountDigits = amountDigits;

        btnPay.setOnClickListener(v -> {
            String pan = safe(etPan);
            String expiry = safe(etExpiry);
            String cvv = safe(etCvv);

            // Hard length limits
            if (pan.length() > 19) {
                etPan.setError("Tối đa 19 số");
                return;
            }
            if (expiry.length() > 4) {
                etExpiry.setError("Tối đa 4 số (YYMM)");
                return;
            }
            if (cvv.length() > 4) {
                etCvv.setError("Tối đa 4 số");
                return;
            }

            if (pan.isEmpty()) {
                etPan.setError("Nhập số thẻ");
                return;
            }
            // PAN common range (ISO/EMV card number 12..19)
            if (!pan.matches("\\d{12,19}")) {
                etPan.setError("Số thẻ 12-19 chữ số");
                return;
            }
            // Basic expiry validation (YYMM)
            if (!expiry.isEmpty() && !expiry.matches("\\d{4}")) {
                etExpiry.setError("YYMM (4 số)");
                return;
            }
            // CVV optional for simulator, but if provided must be 3-4 digits
            if (!cvv.isEmpty() && !cvv.matches("\\d{3,4}")) {
                etCvv.setError("3-4 số");
                return;
            }

            btnPay.setEnabled(false);

            // Build & pack ISO (still local, no socket). Save for preview/debugging later.
            try {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                String tid = prefs.getString("terminal_id", "TID00001");
                String mid = prefs.getString("merchant_id", "MID000000000001");
                boolean encryptPin = prefs.getBoolean("encrypt_pin", false);

                Date now = new Date();
                String f7 = new SimpleDateFormat("MMddHHmmss", Locale.US).format(now);
                String f12 = new SimpleDateFormat("HHmmss", Locale.US).format(now);
                String f13 = new SimpleDateFormat("MMdd", Locale.US).format(now);

                String stan11 = TraceManager.nextStan6(this);
                String rrn37 = new SimpleDateFormat("MMddHHmmssSS", Locale.US).format(now);
                rrn37 = rrn37.substring(0, 12);

                final String f43 = padRight40();

                TransactionContext ctx = new TransactionContext.Builder(TxnType.PURCHASE)
                        .pan2(pan)
                        .processingCode3("000000")
                        .amount4(f4)
                        .transmissionDt7(f7)
                        .stan11(stan11)
                        .localTime12(f12)
                        .localDate13(f13)
                        .mcc18("5999")
                        .posEntryMode22("012")
                        .posCondition25("00")
                        .acquirerId32("970403")
                        .rrn37(rrn37)
                        .terminalId41(tid)
                        .merchantId42(mid)
                        .merchantNameLocation43(f43)
                        .currency49("704")
                        .encryptPin(encryptPin)
                        .expiry14(expiry)
                        .track2_35(null)
                        .field60(null)
                        .build();

                IsoMessage iso = IsoRequestBuilder.buildPurchase(ctx);
                byte[] payload = IsoPacker.pack(iso);
                byte[] framed = IsoHeader.withLengthPrefix2(payload);

                String payloadHex = HexUtil.bytesToHex(payload);
                String framedHex = HexUtil.bytesToHex(framed);
                IsoMessageStore.saveLast(this, TxnType.PURCHASE, iso, payloadHex, framedHex);

                // Only play success animation if build succeeded
                playGlowAndNavigate(vGlowRing, finalAmountDigits);
            } catch (Exception e) {
                btnPay.setEnabled(true);
                Toast.makeText(this, "Lỗi dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String safe(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private static String padRight40() {
        String s = "MY EPOS TEST";
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < 40) sb.append(' ');
        if (sb.length() > 40) return sb.substring(0, 40);
        return sb.toString();
    }

    private void playGlowAndNavigate(View ring, String amountDigits) {
        if (ring == null) {
            goSuccess(amountDigits);
            return;
        }

        ring.setVisibility(View.VISIBLE);
        ring.setAlpha(0f);
        ring.setRotation(0f);

        // Fade-in so it's always visible
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(ring, View.ALPHA, 0f, 1f);
        fadeIn.setDuration(180);

        // 3s sweep rotation
        ObjectAnimator rotate = ObjectAnimator.ofFloat(ring, View.ROTATION, 0f, 1080f);
        rotate.setDuration(3000);

        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(ring, View.ALPHA, 1f, 0f);
        fadeOut.setDuration(250);
        fadeOut.setStartDelay(2750);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(fadeIn, rotate, fadeOut);
        set.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                ring.setVisibility(View.INVISIBLE);
                goSuccess(amountDigits);
            }
        });
        set.start();
    }

    private void goSuccess(String amountDigits) {
        Intent intent = new Intent(this, PaymentSuccessActivity.class);
        intent.putExtra(PaymentSuccessActivity.EXTRA_AMOUNT_DIGITS, amountDigits);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }
}
