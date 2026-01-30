package com.example.mysoftpos.config.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.entity.FieldConfiguration;

import java.util.ArrayList;
import java.util.List;

public class FieldConfigAdapter extends RecyclerView.Adapter<FieldConfigAdapter.ViewHolder> {

    private List<FieldConfiguration> items = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(FieldConfiguration config);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<FieldConfiguration> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    public List<FieldConfiguration> getData() {
        return items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_field_config, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FieldConfiguration config = items.get(position);

        holder.tvFieldId.setText(String.format("%03d", config.fieldId));

        String type = config.sourceType != null ? config.sourceType : "FIXED";
        holder.tvSourceType.setText(type);

        if ("FIXED".equals(type)) {
            holder.tvValue.setText(config.value);
            holder.tvValue.setVisibility(View.VISIBLE);
        } else {
            holder.tvValue.setText("");
            holder.tvValue.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null)
                listener.onItemClick(config);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvFieldId;
        TextView tvSourceType;
        TextView tvValue;
        ImageView btnEdit;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFieldId = itemView.findViewById(R.id.tvFieldId);
            tvSourceType = itemView.findViewById(R.id.tvSourceType);
            tvValue = itemView.findViewById(R.id.tvValue);
        }
    }
}
