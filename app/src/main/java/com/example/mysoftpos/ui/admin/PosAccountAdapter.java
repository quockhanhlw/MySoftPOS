package com.example.mysoftpos.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.remote.api.ApiService;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PosAccountAdapter extends RecyclerView.Adapter<PosAccountAdapter.ViewHolder> {

    private List<ApiService.MerchantDto> merchants = new ArrayList<>();
    private Map<Long, Integer> missingTidCountByMerchantId = new HashMap<>();
    private final OnMerchantListener listener;

    private static final String[] COLORS = {
            "#0D9488", "#1565C0", "#7C3AED", "#C2410C", "#059669", "#DB2777", "#0369A1", "#B45309"
    };

    public interface OnMerchantListener {
        void onMerchantClick(ApiService.MerchantDto merchant);
        void onMerchantEdit(ApiService.MerchantDto merchant);
        void onMerchantDelete(ApiService.MerchantDto merchant);
    }

    public PosAccountAdapter(OnMerchantListener listener) {
        this.listener = listener;
    }

    public void setMerchants(List<ApiService.MerchantDto> merchants) {
        this.merchants = merchants != null ? merchants : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setMissingTidCounts(Map<Long, Integer> missingTidCounts) {
        this.missingTidCountByMerchantId = missingTidCounts != null
                ? new HashMap<>(missingTidCounts)
                : new HashMap<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pos_account, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(merchants.get(position), position, listener, missingTidCountByMerchantId);
    }

    @Override
    public int getItemCount() {
        return merchants.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final FrameLayout avatarContainer;
        final TextView tvAvatarLetter;
        final TextView tvName;
        final TextView tvPhone;
        final TextView tvServerInfo;
        final TextView tvTidAlertBadge;
        final MaterialButton btnViewAccounts;
        final MaterialButton btnEditMerchant;
        final MaterialButton btnDeleteMerchant;

        ViewHolder(View view) {
            super(view);
            avatarContainer = view.findViewById(R.id.avatarContainer);
            tvAvatarLetter = view.findViewById(R.id.tvAvatarLetter);
            tvName = view.findViewById(R.id.tvName);
            tvPhone = view.findViewById(R.id.tvPhone);
            tvServerInfo = view.findViewById(R.id.tvServerInfo);
            tvTidAlertBadge = view.findViewById(R.id.tvTidAlertBadge);
            btnViewAccounts = view.findViewById(R.id.btnViewAccounts);
            btnEditMerchant = view.findViewById(R.id.btnEditMerchant);
            btnDeleteMerchant = view.findViewById(R.id.btnDeleteMerchant);
        }

        void bind(ApiService.MerchantDto merchant,
                int position,
                OnMerchantListener listener,
                Map<Long, Integer> missingTidCountByMerchantId) {
            String name = merchant.merchantName != null && !merchant.merchantName.trim().isEmpty()
                    ? merchant.merchantName
                    : merchant.merchantCode;
            tvName.setText(name);
            String mid = merchant.merchantCode != null && !merchant.merchantCode.trim().isEmpty()
                    ? merchant.merchantCode
                    : itemView.getContext().getString(R.string.txn_detail_placeholder_dash);
            tvPhone.setText(itemView.getContext().getString(R.string.user_mgmt_mid_format, mid));

            // Avatar
            String letter = name.substring(0, 1).toUpperCase();
            tvAvatarLetter.setText(letter);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.parseColor(COLORS[position % COLORS.length]));
            avatarContainer.setBackground(circle);

            // TID badge
            int branchCount = merchant.branchCount != null ? merchant.branchCount : 0;
            int accountCount = merchant.accountCount != null ? merchant.accountCount : 0;
            if (branchCount > 0 || accountCount > 0) {
                tvServerInfo.setText(itemView.getContext().getString(
                        R.string.user_mgmt_branch_count_format,
                        branchCount,
                        accountCount));
                tvServerInfo.setVisibility(View.VISIBLE);
            } else {
                tvServerInfo.setVisibility(View.GONE);
            }

            int missingTidCount = 0;
            if (missingTidCountByMerchantId != null) {
                Integer count = missingTidCountByMerchantId.get(merchant.id);
                missingTidCount = count != null ? count : 0;
            }
            if (missingTidCount > 0) {
                tvTidAlertBadge.setText(itemView.getContext().getString(
                        R.string.user_mgmt_missing_tid_badge,
                        missingTidCount));
                tvTidAlertBadge.setVisibility(View.VISIBLE);
            } else {
                tvTidAlertBadge.setVisibility(View.GONE);
            }

            // Online status indicator
            View vOnlineStatus = itemView.findViewById(R.id.vOnlineStatus);
            if (vOnlineStatus != null) {
                vOnlineStatus.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onMerchantClick(merchant));
            btnViewAccounts.setOnClickListener(v -> listener.onMerchantClick(merchant));
            btnEditMerchant.setOnClickListener(v -> listener.onMerchantEdit(merchant));
            btnDeleteMerchant.setOnClickListener(v -> listener.onMerchantDelete(merchant));
        }
    }
}
