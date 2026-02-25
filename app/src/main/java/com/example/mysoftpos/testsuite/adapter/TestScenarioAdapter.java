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
    private final OnItemToggleListener toggleListener;
    private boolean multiMode = false;
    private boolean selectionMode = false;

    public interface OnItemClickListener {
        void onItemClick(TestScenario scenario);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(TestScenario scenario);
    }

    public interface OnItemToggleListener {
        void onItemToggle(TestScenario scenario);
    }

    public TestScenarioAdapter(OnItemClickListener listener, OnItemLongClickListener longListener,
            OnItemToggleListener toggleListener) {
        this.listener = listener;
        this.longListener = longListener;
        this.toggleListener = toggleListener;
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
        holder.bind(item, listener, longListener, toggleListener, multiMode, selectionMode);
    }

    @Override
    public int getItemCount() {
        return scenarios.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvDetail;
        final TextView chipBadge;
        final CheckBox cbSelect;
        final ImageView ivEdit;
        final View viewAccent;

        ViewHolder(View view) {
            super(view);
            tvTitle = view.findViewById(R.id.tvTitle);
            tvDetail = view.findViewById(R.id.tvDetail);
            chipBadge = view.findViewById(R.id.chipBadge);
            cbSelect = view.findViewById(R.id.cbSelect);
            ivEdit = view.findViewById(R.id.ivEdit);
            viewAccent = view.findViewById(R.id.viewAccent);
        }

        void bind(TestScenario item, OnItemClickListener listener, OnItemLongClickListener longListener,
                OnItemToggleListener toggleListener, boolean multiMode, boolean selectionMode) {
            String code = item.getField(22);
            if (code == null)
                code = "---";

            String desc = item.getDescription();
            tvTitle.setText(desc);

            if (item.isCustom()) {
                chipBadge.setText("CUSTOM");
            } else {
                chipBadge.setText(code);
            }

            // Color-code accent strip by DE22 entry mode
            if (viewAccent != null) {
                int accentColor;
                if (code.startsWith("02")) {
                    accentColor = 0xFF3B82F6; // Blue — Magstripe
                } else if (code.startsWith("01")) {
                    accentColor = 0xFF10B981; // Green — Manual Key-in
                } else if (code.startsWith("05") || code.startsWith("07") || code.startsWith("91")) {
                    accentColor = 0xFFF59E0B; // Amber — Chip/Contactless
                } else {
                    accentColor = 0xFF94A3B8; // Gray — Other
                }
                viewAccent.setBackgroundColor(accentColor);

                // Selected state — accent becomes darker
                if (item.isSelected()) {
                    viewAccent.setBackgroundColor(0xFF0F172A);
                }
            }

            if (multiMode && selectionMode) {
                cbSelect.setVisibility(View.VISIBLE);
                cbSelect.setChecked(item.isSelected());

                if (item.isSelected()) {
                    tvDetail.setText("Selected");
                } else {
                    tvDetail.setText("Tap to select");
                }
                ivEdit.setVisibility(View.GONE);
            } else {
                cbSelect.setVisibility(View.GONE);

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

            // Separate listener for Checkbox — use onClickListener
            // and prevent itemView from also receiving the click
            cbSelect.setOnClickListener(v -> {
                if (toggleListener != null) {
                    toggleListener.onItemToggle(item);
                }
            });
            // Prevent checkbox clicks from also triggering itemView click
            cbSelect.setOnTouchListener((v, event) -> {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            });

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
