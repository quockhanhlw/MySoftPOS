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
    private final OnItemLongClickListener longListener;
    private boolean multiMode = false;
    private boolean selectionMode = false;

    public interface OnItemClickListener {
        void onItemClick(TestScenario scenario);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(TestScenario scenario);
    }

    public TestScenarioAdapter(OnItemClickListener listener, OnItemLongClickListener longListener) {
        this.listener = listener;
        this.longListener = longListener;
    }

    public void setScenarios(List<TestScenario> scenarios) {
        this.scenarios = scenarios;
        notifyDataSetChanged();
    }

    public void setMultiMode(boolean multiMode) {
        this.multiMode = multiMode;
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean selectionMode) {
        this.selectionMode = selectionMode;
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
        holder.bind(item, listener, longListener, multiMode, selectionMode);
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
        final ImageView ivEdit;

        ViewHolder(View view) {
            super(view);
            tvTitle = view.findViewById(R.id.tvTitle);
            tvDetail = view.findViewById(R.id.tvDetail);
            chipBadge = view.findViewById(R.id.chipBadge);
            imgIcon = view.findViewById(R.id.imgIcon);
            ivArrow = view.findViewById(R.id.ivArrow);
            iconContainer = view.findViewById(R.id.iconContainer);
            cbSelect = view.findViewById(R.id.cbSelect);
            ivEdit = view.findViewById(R.id.ivEdit);
        }

        void bind(TestScenario item, OnItemClickListener listener, OnItemLongClickListener longListener,
                boolean multiMode, boolean selectionMode) {
            String code = item.getField(22);
            if (code == null)
                code = "---";

            String desc = item.getDescription();
            tvTitle.setText(desc);

            if (item.isCustom()) {
                chipBadge.setText("CUSTOM");
                chipBadge.setBackgroundResource(R.drawable.bg_badge_custom); // Need to create this or use generic
            } else {
                chipBadge.setText(code);
                chipBadge.setBackgroundResource(R.drawable.bg_badge_default); // Assuming exists or default
            }

            if (multiMode && selectionMode) {
                cbSelect.setVisibility(View.VISIBLE);
                cbSelect.setChecked(item.isSelected());
                imgIcon.setVisibility(View.GONE);
                tvDetail.setText(item.isSelected() ? "✅ Configured" : "Tap to configure");
                ivEdit.setVisibility(View.GONE);
            } else {
                cbSelect.setVisibility(View.GONE);
                imgIcon.setVisibility(View.VISIBLE);
                // In multi-thread mode (but selection OFF), show "Long press to select" or
                // standard text
                if (multiMode) {
                    tvDetail.setText("Long press to select");
                } else {
                    tvDetail.setText("Tap to run test case");
                }
                // Show Edit button only for custom cases
                ivEdit.setVisibility(item.isCustom() ? View.VISIBLE : View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onItemClick(item));
            itemView.setOnLongClickListener(v -> {
                if (longListener != null) {
                    longListener.onItemLongClick(item);
                    return true;
                }
                return false;
            });

            // Edit Button Click
            ivEdit.setOnClickListener(v -> {
                if (longListener != null)
                    longListener.onItemLongClick(item);
            });
        }
    }
}
