package com.example.mysoftpos.testsuite.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
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
    private boolean multiMode = false;

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

    public void setMultiMode(boolean multiMode) {
        this.multiMode = multiMode;
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
        holder.bind(item, listener, multiMode);
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
        final CheckBox cbSelect;

        ViewHolder(View view) {
            super(view);
            tvTitle = view.findViewById(R.id.tvTitle);
            tvDetail = view.findViewById(R.id.tvDetail);
            chipBadge = view.findViewById(R.id.chipBadge);
            imgIcon = view.findViewById(R.id.imgIcon);
            ivArrow = view.findViewById(R.id.ivArrow);
            iconContainer = view.findViewById(R.id.iconContainer);
            cbSelect = view.findViewById(R.id.cbSelect);
        }

        void bind(TestScenario item, OnItemClickListener listener, boolean multiMode) {
            String code = item.getField(22);
            if (code == null)
                code = "---";

            String desc = item.getDescription();
            tvTitle.setText(desc);
            chipBadge.setText(code);

            if (multiMode) {
                cbSelect.setVisibility(View.VISIBLE);
                cbSelect.setChecked(item.isSelected());
                imgIcon.setVisibility(View.GONE);
                tvDetail.setText(item.isSelected() ? "✅ Configured" : "Tap to configure");
            } else {
                cbSelect.setVisibility(View.GONE);
                imgIcon.setVisibility(View.VISIBLE);
                tvDetail.setText("Tap to run test case");
            }

            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}
