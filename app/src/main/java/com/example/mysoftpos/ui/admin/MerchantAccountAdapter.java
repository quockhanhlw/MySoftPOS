package com.example.mysoftpos.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.remote.api.ApiService;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class MerchantAccountAdapter extends RecyclerView.Adapter<MerchantAccountAdapter.AccountViewHolder> {

    public interface OnAccountActionListener {
        void onEdit(ApiService.UserDto user);

        void onDelete(ApiService.UserDto user);
    }

    private final List<ApiService.UserDto> users = new ArrayList<>();
    private final OnAccountActionListener listener;

    public MerchantAccountAdapter(OnAccountActionListener listener) {
        this.listener = listener;
    }

    public void submit(List<ApiService.UserDto> data) {
        users.clear();
        if (data != null) {
            users.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_merchant_account, parent, false);
        return new AccountViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AccountViewHolder holder, int position) {
        ApiService.UserDto user = users.get(position);
        String name = safe(user.fullName);
        String phone = safe(user.phone);
        String tid = safe(user.terminalId);
        String ip = safe(user.serverIp);
        String port = user.serverPort != null && user.serverPort > 0 ? String.valueOf(user.serverPort) : "-";

        holder.tvAccountName.setText(name.isEmpty() ? "-" : name);
        holder.tvAccountPhone.setText(phone.isEmpty() ? "-" : phone);
        holder.tvAccountConfig.setText(holder.itemView.getContext().getString(
                R.string.user_mgmt_account_item_config_format,
                tid.isEmpty() ? "-" : tid,
                ip.isEmpty() ? "-" : ip,
                port));

        holder.btnEdit.setOnClickListener(v -> listener.onEdit(user));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(user));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    static class AccountViewHolder extends RecyclerView.ViewHolder {
        final TextView tvAccountName;
        final TextView tvAccountPhone;
        final TextView tvAccountConfig;
        final MaterialButton btnEdit;
        final MaterialButton btnDelete;

        AccountViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAccountName = itemView.findViewById(R.id.tvAccountName);
            tvAccountPhone = itemView.findViewById(R.id.tvAccountPhone);
            tvAccountConfig = itemView.findViewById(R.id.tvAccountConfig);
            btnEdit = itemView.findViewById(R.id.btnEditAccount);
            btnDelete = itemView.findViewById(R.id.btnDeleteAccount);
        }
    }
}

