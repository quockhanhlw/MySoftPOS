package com.example.mysoftpos.config.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.entity.TransactionType;

import java.util.ArrayList;
import java.util.List;

public class TransactionTypeAdapter extends RecyclerView.Adapter<TransactionTypeAdapter.ViewHolder> {

    private List<TransactionType> items = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(TransactionType type);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<TransactionType> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction_type, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TransactionType type = items.get(position);
        holder.tvName.setText(type.name);
        holder.tvMti.setText(type.mti);
        holder.tvProcessingCode.setText(type.processingCode);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null)
                listener.onItemClick(type);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvMti, tvProcessingCode;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvMti = itemView.findViewById(R.id.tvMti);
            tvProcessingCode = itemView.findViewById(R.id.tvProcessingCode);
        }
    }
}
