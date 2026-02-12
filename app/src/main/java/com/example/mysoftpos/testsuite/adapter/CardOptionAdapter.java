package com.example.mysoftpos.testsuite.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mysoftpos.R;
import java.util.List;

public class CardOptionAdapter extends RecyclerView.Adapter<CardOptionAdapter.ViewHolder> {

    private final List<String> cardOptions;
    private final OnCardSelectedListener listener;

    public interface OnCardSelectedListener {
        void onCardSelected(String cardData);
    }

    public CardOptionAdapter(List<String> cardOptions, OnCardSelectedListener listener) {
        this.cardOptions = cardOptions;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_card_option, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String data = cardOptions.get(position);
        holder.bind(data, listener);
    }

    @Override
    public int getItemCount() {
        return cardOptions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvPan;
        final TextView tvExpiry;
        final TextView tvBankName;
        final android.widget.ImageView ivChip;

        ViewHolder(View view) {
            super(view);
            tvPan = view.findViewById(R.id.tvPan);
            tvExpiry = view.findViewById(R.id.tvExpiry);
            tvBankName = view.findViewById(R.id.tvBankName);
            ivChip = view.findViewById(R.id.ivChip);
        }

        void bind(String rawData, OnCardSelectedListener listener) {
            // Parse raw track2 data: PAN=Expiry...
            // "9704166606226219923=31016010000000123"
            String pan = "";
            String expiry = "";

            if (rawData.contains("=")) {
                String[] parts = rawData.split("=");
                pan = parts[0];
                if (parts.length > 1 && parts[1].length() >= 4) {
                    // YYMM
                    String rawExp = parts[1].substring(0, 4);
                    // Format to MM/YY for display if possible, or just keep raw
                    // Let's nice format it: 3101 -> 31/01 (wait, expiry is YYMM generally in
                    // Track2?
                    // ISO 7813 says YYMM. So 3101 is Year 31, Month 01.
                    // Display as MM/YY: 01/31
                    if (rawExp.length() == 4) {
                        String yy = rawExp.substring(0, 2);
                        String mm = rawExp.substring(2, 4);
                        expiry = mm + "/" + yy;
                    } else {
                        expiry = rawExp;
                    }
                }
            } else if (rawData.contains("D")) {
                String[] parts = rawData.split("D");
                pan = parts[0];
                if (parts.length > 1 && parts[1].length() >= 4) {
                    String rawExp = parts[1].substring(0, 4);
                    String yy = rawExp.substring(0, 2);
                    String mm = rawExp.substring(2, 4);
                    expiry = mm + "/" + yy;
                }
            } else {
                pan = rawData; // Fallback
            }

            // Format PAN with spaces: 1234 5678 ...
            StringBuilder formattedPan = new StringBuilder();
            for (int i = 0; i < pan.length(); i++) {
                if (i > 0 && i % 4 == 0) {
                    formattedPan.append(" ");
                }
                formattedPan.append(pan.charAt(i));
            }

            tvPan.setText(formattedPan.toString());
            tvExpiry.setText(expiry);

            // Determine Bank Name (Simple logic or hardcoded based on BIN)
            // Determine Bank Name (Simple logic or hardcoded based on BIN)
            String bankName = "NAPAS";
            if (pan.startsWith("970416")) {
                bankName = "ACB";
            } else if (pan.startsWith("970430")) {
                bankName = "PG Bank";
            } else if (pan.startsWith("970418")) {
                bankName = "BIDV";
            }
            // Add more if known, else default NAPAS

            tvBankName.setText(bankName);

            // Icon logic? Just keep existing chip icon but verify it sets tint correctly in
            // XML

            itemView.setOnClickListener(v -> listener.onCardSelected(rawData));
        }
    }
}
