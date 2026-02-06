package com.example.mysoftpos.testsuite.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mysoftpos.R;
import com.example.mysoftpos.testsuite.model.TestScenario;
import java.util.Collections;
import java.util.List;

public class TestScenarioAdapter extends RecyclerView.Adapter<TestScenarioAdapter.ViewHolder> {

    private List<TestScenario> scenarios = Collections.emptyList();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(TestScenario scenario);
    }

    public TestScenarioAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setScenarios(List<TestScenario> scenarios) {
        this.scenarios = scenarios;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_test_scenario, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TestScenario item = scenarios.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return scenarios.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvDetail;
        final TextView chipBadge;
        final ImageView imgIcon;
        final ImageView ivArrow;
        final FrameLayout iconContainer;

        ViewHolder(View view) {
            super(view);
            tvTitle = view.findViewById(R.id.tvTitle);
            tvDetail = view.findViewById(R.id.tvDetail);
            chipBadge = view.findViewById(R.id.chipBadge);
            imgIcon = view.findViewById(R.id.imgIcon);
            ivArrow = view.findViewById(R.id.ivArrow);
            iconContainer = view.findViewById(R.id.iconContainer);
        }

        void bind(TestScenario item, OnItemClickListener listener) {
            // Extract Code (DE 22)
            String code = item.getField(22);
            if (code == null)
                code = "---";

            // Title cleaning (remove code from description if redundant)
            String desc = item.getDescription();
            // Optional: Clean title logic if needed

            tvTitle.setText(desc);
            chipBadge.setText(code);

            // Dynamic Icon & Details logic
            if ("011".equals(code) || "012".equals(code)) {
                // Manual
                imgIcon.setImageResource(R.drawable.ic_edit_note); // Need to ensure this exists or use fallback
            } else if ("021".equals(code) || "022".equals(code)) {
                // Magstripe
                imgIcon.setImageResource(R.drawable.ic_credit_card);
            } else {
                imgIcon.setImageResource(R.drawable.ic_credit_card_off);
            }

            // Set Details (Bank Name or other info)
            tvDetail.setText("Tap to run test case");

            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}
