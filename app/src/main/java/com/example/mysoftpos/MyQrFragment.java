package com.example.mysoftpos;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class MyQrFragment extends Fragment {

    private final StringBuilder digits = new StringBuilder();
    private TextView tvAmount;
    private TextView tvDone;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_my_qr, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvAmount = view.findViewById(R.id.tvAmount);
        tvDone = view.findViewById(R.id.keyDone);

        // number keys
        setKey(view, R.id.key1, "1");
        setKey(view, R.id.key2, "2");
        setKey(view, R.id.key3, "3");
        setKey(view, R.id.key4, "4");
        setKey(view, R.id.key5, "5");
        setKey(view, R.id.key6, "6");
        setKey(view, R.id.key7, "7");
        setKey(view, R.id.key8, "8");
        setKey(view, R.id.key9, "9");
        setKey(view, R.id.key0, "0");

        TextView key000 = view.findViewById(R.id.key000);
        key000.setOnClickListener(v -> appendTripleZero());

        ImageView backspace = view.findViewById(R.id.keyBackspace);
        backspace.setOnClickListener(v -> backspace());

        refresh();

        tvDone.setOnClickListener(v -> {
            // TODO: Generate QR / proceed
            // For now just keep UI, button enabled state is controlled by amount
        });
    }

    private void setKey(@NonNull View root, int id, @NonNull String digit) {
        TextView key = root.findViewById(id);
        key.setOnClickListener(v -> appendDigit(digit));
    }

    private void appendDigit(@NonNull String d) {
        // avoid leading zeros
        if (digits.length() == 0 && "0".equals(d)) {
            refresh();
            return;
        }
        if (digits.length() >= 12) return; // cap
        digits.append(d);
        refresh();
    }

    private void appendTripleZero() {
        if (digits.length() == 0) {
            refresh();
            return;
        }
        if (digits.length() <= 9) {
            digits.append("000");
        }
        refresh();
    }

    private void backspace() {
        int len = digits.length();
        if (len == 0) {
            refresh();
            return;
        }
        digits.deleteCharAt(len - 1);
        refresh();
    }

    private void refresh() {
        String formatted = AmountFormatter.formatVnd(digits.toString());
        tvAmount.setText(formatted);

        boolean enabled = digits.length() > 0;
        tvDone.setEnabled(enabled);
        tvDone.setClickable(enabled);
        tvDone.setAlpha(enabled ? 1f : 0.5f);
        int color = ContextCompat.getColor(requireContext(), enabled ? R.color.text_primary : R.color.text_secondary);
        tvDone.setTextColor(color);
    }
}
