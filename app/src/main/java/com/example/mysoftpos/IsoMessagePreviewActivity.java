package com.example.mysoftpos;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hiển thị ISO debug string + hex payload + hex after header. */
public class IsoMessagePreviewActivity extends AppCompatActivity {

    private static final int HEX_WRAP = 48; // chars per line

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iso_message_preview);

        TextView tvDebug = findViewById(R.id.tvIsoDebug);
        TextView tvHex = findViewById(R.id.tvIsoHex);
        TextView tvFramedHex = findViewById(R.id.tvFramedHex);

        Button btnCopyDebug = findOptionalBtn(R.id.btnCopyDebug);
        Button btnCopyPayload = findOptionalBtn(R.id.btnCopyPayload);
        Button btnCopyFramed = findOptionalBtn(R.id.btnCopyFramed);

        String debug = getIntent().getStringExtra("extra_iso_debug");
        String payloadHex = getIntent().getStringExtra("extra_iso_hex");
        String framedHex = getIntent().getStringExtra("extra_framed_hex");

        String safeDebug = maskSensitive(debug);

        tvDebug.setText(safeDebug);
        tvHex.setText(wrapHex(payloadHex, HEX_WRAP));
        tvFramedHex.setText(wrapHex(framedHex, HEX_WRAP));

        if (btnCopyDebug != null) {
            btnCopyDebug.setOnClickListener(v -> copyToClipboard("ISO Debug", safeDebug));
        }
        if (btnCopyPayload != null) {
            btnCopyPayload.setOnClickListener(v -> copyToClipboard("ISO Payload Hex", payloadHex));
        }
        if (btnCopyFramed != null) {
            btnCopyFramed.setOnClickListener(v -> copyToClipboard("ISO Framed Hex", framedHex));
        }
    }

    private Button findOptionalBtn(int id) {
        try {
            return findViewById(id);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void copyToClipboard(String label, String text) {
        if (text == null) text = "";
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb != null) {
            cb.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, "Copied: " + label, Toast.LENGTH_SHORT).show();
        }
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

    /**
     * Mask PAN/Track2 in debug string so logs/screenshots are safer.
     * - F2: keep first 6 + last 4
     * - F35: keep first 6 + last 4 (best effort)
     */
    private static String maskSensitive(String debug) {
        if (debug == null) return "";

        String out = debug;
        out = maskField(out, "F2", 6, 4);
        out = maskField(out, "F35", 6, 4);
        return out;
    }

    private static String maskField(String s, String fieldToken, int keepStart, int keepEnd) {
        Pattern p = Pattern.compile("\\|\\s*" + fieldToken + "=([^|]+)");
        Matcher m = p.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String v = m.group(1);
            String masked = maskDigitsLike(v, keepStart, keepEnd);
            m.appendReplacement(sb, "| " + fieldToken + "=" + Matcher.quoteReplacement(masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String maskDigitsLike(String v, int keepStart, int keepEnd) {
        if (v == null) return "";
        String t = v.trim();
        // only mask if looks like a PAN/track: contains many digits
        int digits = 0;
        for (int i = 0; i < t.length(); i++) {
            if (Character.isDigit(t.charAt(i))) digits++;
        }
        if (digits < 10) return t;

        // extract only digits then re-use original if it's pure digits
        if (t.matches("\\d+")) {
            return maskPureDigits(t, keepStart, keepEnd);
        }

        // for track2, keep structure but mask digits
        String digitsOnly = t.replaceAll("\\D+", "");
        if (digitsOnly.length() < keepStart + keepEnd) return t;
        String maskedDigits = maskPureDigits(digitsOnly, keepStart, keepEnd);
        return maskedDigits + " (masked)";
    }

    private static String maskPureDigits(String d, int keepStart, int keepEnd) {
        if (d == null) return "";
        if (d.length() <= keepStart + keepEnd) return d;
        StringBuilder sb = new StringBuilder();
        sb.append(d, 0, keepStart);
        for (int i = 0; i < d.length() - keepEnd - keepStart; i++) sb.append('*');
        sb.append(d, d.length() - keepEnd, d.length());
        return sb.toString();
    }
}
