package com.example.mysoftpos.testsuite.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.testsuite.model.TestScenario;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying TestScenarios.
 */
public class TestCaseAdapter extends RecyclerView.Adapter<TestCaseAdapter.ViewHolder> {

    private List<TestScenario> items = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(TestScenario testCase);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<TestScenario> newItems) {
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
        TestScenario item = items.get(position);
        holder.tvName.setText(item.getDescription());
        holder.tvMti.setText(item.getMti());
        // For scenarios, processing code is often dynamic or in fields
        // We'll use a placeholder or extract from fields if needed
        holder.tvProcessingCode.setText("Check Log");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null)
                listener.onItemClick(item);
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
