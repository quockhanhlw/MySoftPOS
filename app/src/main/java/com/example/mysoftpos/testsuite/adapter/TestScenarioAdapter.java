package com.example.mysoftpos.testsuite.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
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
    private final OnItemEditClickListener editClickListener;
    private final OnItemDeleteClickListener deleteClickListener;
    private boolean multiMode = false;
    private boolean selectionMode = false;
    private boolean deleteMode = false;
    private int openedSwipePosition = RecyclerView.NO_POSITION;

    public interface OnItemClickListener {
        void onItemClick(TestScenario scenario);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(TestScenario scenario);
    }

    public interface OnItemToggleListener {
        void onItemToggle(TestScenario scenario);
    }

    public interface OnItemEditClickListener {
        void onItemEditClick(TestScenario scenario);
    }

    public interface OnItemDeleteClickListener {
        void onItemDeleteClick(TestScenario scenario);
    }

    public TestScenarioAdapter(OnItemClickListener listener, OnItemLongClickListener longListener,
            OnItemToggleListener toggleListener,
            OnItemEditClickListener editClickListener,
            OnItemDeleteClickListener deleteClickListener) {
        this.listener = listener;
        this.longListener = longListener;
        this.toggleListener = toggleListener;
        this.editClickListener = editClickListener;
        this.deleteClickListener = deleteClickListener;
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

    public void setDeleteMode(boolean deleteMode) {
        this.deleteMode = deleteMode;
        notifyDataSetChanged();
    }

    public void setOpenedSwipePosition(int openedSwipePosition) {
        this.openedSwipePosition = openedSwipePosition;
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
        holder.bind(item, listener, longListener, toggleListener, editClickListener,
                deleteClickListener,
                multiMode, selectionMode, deleteMode, openedSwipePosition == position);
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
        final View foregroundCard;
        final View btnSwipeEdit;
        final View btnSwipeDelete;
        final View viewAccent;

        ViewHolder(View view) {
            super(view);
            tvTitle = view.findViewById(R.id.tvTitle);
            tvDetail = view.findViewById(R.id.tvDetail);
            chipBadge = view.findViewById(R.id.chipBadge);
            cbSelect = view.findViewById(R.id.cbSelect);
            ivEdit = view.findViewById(R.id.ivEdit);
            foregroundCard = view.findViewById(R.id.cardForeground);
            btnSwipeEdit = view.findViewById(R.id.btnSwipeEdit);
            btnSwipeDelete = view.findViewById(R.id.btnSwipeDelete);
            viewAccent = view.findViewById(R.id.viewAccent);
        }

        void bind(TestScenario item, OnItemClickListener listener, OnItemLongClickListener longListener,
                OnItemToggleListener toggleListener,
                OnItemEditClickListener editClickListener,
                OnItemDeleteClickListener deleteClickListener,
                boolean multiMode,
                boolean selectionMode,
                boolean deleteMode,
                boolean isSwipeOpened) {
            String code = item.getField(22);
            if (code == null)
                code = "---";

            String desc = item.getDescription();
            tvTitle.setText(desc);

            if (item.isCustom()) {
                chipBadge.setText(R.string.test_scenario_badge_custom);
            } else {
                chipBadge.setText(code);
            }

            boolean swipeEnabled = item.isCustom() && !selectionMode && !deleteMode;
            btnSwipeEdit.setVisibility(swipeEnabled ? View.VISIBLE : View.GONE);
            btnSwipeDelete.setVisibility(swipeEnabled ? View.VISIBLE : View.GONE);
            if (foregroundCard != null) {
                foregroundCard.setTranslationX(isSwipeOpened && swipeEnabled
                        ? -itemView.getResources().getDisplayMetrics().density * 112f
                        : 0f);
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
                    tvDetail.setText(R.string.test_scenario_detail_selected);
                } else {
                    tvDetail.setText(R.string.test_scenario_detail_tap_select);
                }
                ivEdit.setVisibility(View.GONE);
            } else {
                cbSelect.setVisibility(View.GONE);

                // In multi-thread mode (but selection OFF), show "Long press to select" or
                // standard text
                if (multiMode) {
                    tvDetail.setText(R.string.test_scenario_detail_long_press_select);
                } else {
                    tvDetail.setText(R.string.test_scenario_detail_tap_run);
                }
                ivEdit.setVisibility(View.GONE);
            }

            View clickTarget = foregroundCard != null ? foregroundCard : itemView;
            clickTarget.setOnClickListener(v -> listener.onItemClick(item));

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

            clickTarget.setLongClickable(true);
            clickTarget.setOnLongClickListener(v -> {
                if (longListener != null) {
                    longListener.onItemLongClick(item);
                    return true;
                }
                return false;
            });

            // Edit Button Click
            ivEdit.setOnClickListener(v -> {
                if (editClickListener != null) {
                    editClickListener.onItemEditClick(item);
                }
            });

            btnSwipeEdit.setOnClickListener(v -> {
                if (editClickListener != null) {
                    editClickListener.onItemEditClick(item);
                }
            });
            btnSwipeEdit.setOnTouchListener((v, event) -> {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            });

            btnSwipeDelete.setOnClickListener(v -> {
                if (deleteClickListener != null) {
                    deleteClickListener.onItemDeleteClick(item);
                }
            });
            btnSwipeDelete.setOnTouchListener((v, event) -> {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            });
        }
    }
}
