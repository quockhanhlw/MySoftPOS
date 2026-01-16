package com.example.mysoftpos;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mysoftpos.iso8583.IsoMessageStore;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class PaymentSuccessActivity extends AppCompatActivity {

    public static final String EXTRA_AMOUNT_DIGITS = "extra_amount_digits";
    private static final String TAG = "SoftPOS-ISO";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_success);

        TextView tvAmount = findViewById(R.id.tvAmount);
        TextView tvIsoDump = null;
        int isoId = getResources().getIdentifier("tvIsoDump", "id", getPackageName());
        if (isoId != 0) {
            try {
                tvIsoDump = findViewById(isoId);
            } catch (Throwable ignored) {
                tvIsoDump = null;
            }
        }

        String digits = getIntent().getStringExtra(EXTRA_AMOUNT_DIGITS);
        if (digits == null) digits = "0";
        String formatted = formatVnd(digits);
        tvAmount.setText(formatted + " đ");

        // Load last packed ISO from store
        String framedHex;
        try {
            framedHex = IsoMessageStore.getLastFramedHex(this);
        } catch (Throwable ignored) {
            framedHex = getSharedPreferences("SoftPOSLastMsg", MODE_PRIVATE).getString("framedHex", "");
        }
        String payloadHex = IsoMessageStore.getLastPayloadHex(this);

        String uiDump = "Payload Hex (MTI+Bitmap+Fields)\n" + wrapHex(payloadHex, 48)
                + "\n\nFramed Hex (LenPrefix+Payload)\n" + wrapHex(framedHex, 48);
        if (tvIsoDump != null) {
            tvIsoDump.setText(uiDump);
        }

        Log.i(TAG, "===== PAYMENT RESULT: SUCCESS =====");
        Log.i(TAG, "AmountDigits=" + digits + " (" + formatted + " đ)");
        Log.i(TAG, "PayloadHex (MTI+Bitmap+Fields):\n" + wrapHex(payloadHex, 48));
        Log.i(TAG, "FramedHex (LenPrefix+Payload):\n" + wrapHex(framedHex, 48));
    }

    private static String wrapHex(String hex, int chunk) {
        if (hex == null) return "";
        String s = hex.trim();
        if (s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i += chunk) {
            int end = Math.min(i + chunk, s.length());
            sb.append(s, i, end);
            if (end < s.length()) sb.append('\n');
        }
        return sb.toString();
    }

    private static String formatVnd(String digits) {
        String d = digits.replaceAll("\\D+", "");
        if (d.isEmpty()) d = "0";
        long v;
        try {
            v = Long.parseLong(d);
        } catch (Exception e) {
            v = 0;
        }
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        sym.setGroupingSeparator(',');
        DecimalFormat df = new DecimalFormat("#,###", sym);
        return df.format(v);
    }
}
