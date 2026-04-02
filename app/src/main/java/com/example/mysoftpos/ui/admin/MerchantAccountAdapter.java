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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MerchantAccountAdapter extends RecyclerView.Adapter<MerchantAccountAdapter.AccountViewHolder> {

    private static final int SOURCE_NONE = 0;
    private static final int SOURCE_POS_ACCOUNT_ID = 1;
    private static final int SOURCE_TERMINAL_ID = 2;

    public interface OnAccountActionListener {
        void onEdit(ApiService.PosAccountDto user);

        void onDelete(ApiService.PosAccountDto user);

        void onResetPassword(ApiService.PosAccountDto user);
    }

    private final List<ApiService.PosAccountDto> users = new ArrayList<>();
    private final OnAccountActionListener listener;
    private final Map<Long, ApiService.TerminalDto> terminalByPosAccountId = new HashMap<>();
    private final Map<String, ApiService.TerminalDto> terminalByTid = new HashMap<>();
    private final Map<Long, String> passwordPreviewByAccountId = new HashMap<>();
    private final Map<Long, HostPreview> hostPreviewByAccountId = new HashMap<>();

    static final class HostPreview {
        final String serverIp;
        final Integer serverPort;

        HostPreview(String serverIp, Integer serverPort) {
            this.serverIp = serverIp;
            this.serverPort = serverPort;
        }
    }

    public MerchantAccountAdapter(OnAccountActionListener listener) {
        this.listener = listener;
    }

    public void submit(List<ApiService.PosAccountDto> data) {
        users.clear();
        if (data != null) {
            users.addAll(data);
        }
        notifyDataSetChanged();
    }

    public void setTerminalMappings(Map<Long, ApiService.TerminalDto> byAccountId,
            Map<String, ApiService.TerminalDto> byTid) {
        terminalByPosAccountId.clear();
        terminalByTid.clear();
        if (byAccountId != null) {
            terminalByPosAccountId.putAll(byAccountId);
        }
        if (byTid != null) {
            terminalByTid.putAll(byTid);
        }
    }

    public void setPasswordPreviews(Map<Long, String> byAccountId) {
        passwordPreviewByAccountId.clear();
        if (byAccountId != null) {
            passwordPreviewByAccountId.putAll(byAccountId);
        }
    }

    public void setHostPreviews(Map<Long, HostPreview> byAccountId) {
        hostPreviewByAccountId.clear();
        if (byAccountId != null) {
            hostPreviewByAccountId.putAll(byAccountId);
        }
    }

    @NonNull
    @Override
    public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_merchant_account, parent, false);
        return new AccountViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AccountViewHolder holder, int position) {
        ApiService.PosAccountDto user = users.get(position);
        String name = safe(user.fullName);
        String phone = safe(user.phone);
        String merchantPhone = safe(user.merchantPhone);
        String username = safe(user.username);
        String tid = normalizeTid(user.terminalId);

        int source = SOURCE_NONE;
        ApiService.TerminalDto mappedTerminal = terminalByPosAccountId.get(user.id);
        if (mappedTerminal != null) {
            source = SOURCE_POS_ACCOUNT_ID;
        } else if (!tid.isEmpty()) {
            mappedTerminal = terminalByTid.get(tid);
            if (mappedTerminal != null) {
                source = SOURCE_TERMINAL_ID;
            }
        }

        String serverIp = safe(user.serverIp);
        Integer serverPort = user.serverPort;
        if (mappedTerminal != null) {
            if (!safe(mappedTerminal.serverIp).isEmpty()) {
                serverIp = safe(mappedTerminal.serverIp);
            }
            if (mappedTerminal.serverPort != null) {
                serverPort = mappedTerminal.serverPort;
            }
        }
        HostPreview preview = hostPreviewByAccountId.get(user.id);
        if (preview != null) {
            if (!safe(preview.serverIp).isEmpty()) {
                serverIp = safe(preview.serverIp);
            }
            if (preview.serverPort != null) {
                serverPort = preview.serverPort;
            }
        }
        boolean hasIp = !serverIp.isEmpty();
        boolean hasPort = serverPort != null;
        boolean hasValidPort = hasPort && serverPort > 0 && serverPort <= 65535;
        boolean configured = hasIp && hasValidPort;
        boolean partial = (hasIp && !hasPort) || (!hasIp && hasPort) || (hasPort && !hasValidPort);

        holder.tvAccountName.setText(name.isEmpty() ? "-" : name);
        holder.tvAccountPhone.setText(holder.itemView.getContext().getString(
                R.string.user_mgmt_account_merchant_phone_format,
                merchantPhone.isEmpty() ? "-" : merchantPhone));
        holder.tvAccountUsername.setText(holder.itemView.getContext().getString(
                R.string.user_mgmt_account_username_format,
                username.isEmpty() ? (phone.isEmpty() ? "-" : phone) : username));
        String passwordPreview = passwordPreviewByAccountId.get(user.id);
        if (passwordPreview == null || passwordPreview.trim().isEmpty()) {
            passwordPreview = holder.itemView.getContext().getString(R.string.user_mgmt_account_password_saved_placeholder);
        }
        holder.tvAccountPassword.setText(holder.itemView.getContext().getString(
                R.string.user_mgmt_account_password_format,
                passwordPreview));
        holder.tvAccountConfig.setText(holder.itemView.getContext().getString(
                R.string.user_mgmt_account_item_config_format,
                tid.isEmpty() ? "-" : tid,
                hasIp ? serverIp : "-",
                hasPort ? String.valueOf(serverPort) : "-"));

        String sourceSuffix = source == SOURCE_POS_ACCOUNT_ID
                ? holder.itemView.getContext().getString(R.string.user_mgmt_host_source_pos_account)
                : source == SOURCE_TERMINAL_ID
                        ? holder.itemView.getContext().getString(R.string.user_mgmt_host_source_terminal)
                        : "";
        java.util.function.Function<String, String> withSource = base -> sourceSuffix.isEmpty()
                ? base
                : holder.itemView.getContext().getString(R.string.user_mgmt_host_status_with_source, base, sourceSuffix);

        if (configured) {
            holder.tvHostStatusBadge.setText(withSource.apply(
                    holder.itemView.getContext().getString(R.string.user_mgmt_host_status_configured)));
            holder.tvHostStatusBadge.setBackgroundResource(R.drawable.bg_host_status_configured);
        } else if (partial) {
            holder.tvHostStatusBadge.setText(withSource.apply(
                    holder.itemView.getContext().getString(R.string.user_mgmt_host_status_partial)));
            holder.tvHostStatusBadge.setBackgroundResource(R.drawable.bg_host_status_partial);
        } else {
            holder.tvHostStatusBadge.setText(withSource.apply(
                    holder.itemView.getContext().getString(R.string.user_mgmt_host_status_missing)));
            holder.tvHostStatusBadge.setBackgroundResource(R.drawable.bg_host_status_missing);
        }

        holder.btnEdit.setOnClickListener(v -> listener.onEdit(user));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(user));
        holder.btnResetPassword.setOnClickListener(v -> listener.onResetPassword(user));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeTid(String value) {
        return safe(value).toUpperCase(Locale.ROOT);
    }

    static class AccountViewHolder extends RecyclerView.ViewHolder {
        final TextView tvAccountName;
        final TextView tvAccountPhone;
        final TextView tvAccountUsername;
        final TextView tvAccountPassword;
        final TextView tvAccountConfig;
        final TextView tvHostStatusBadge;
        final MaterialButton btnEdit;
        final MaterialButton btnResetPassword;
        final MaterialButton btnDelete;

        AccountViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAccountName = itemView.findViewById(R.id.tvAccountName);
            tvAccountPhone = itemView.findViewById(R.id.tvAccountPhone);
            tvAccountUsername = itemView.findViewById(R.id.tvAccountUsername);
            tvAccountPassword = itemView.findViewById(R.id.tvAccountPassword);
            tvAccountConfig = itemView.findViewById(R.id.tvAccountConfig);
            tvHostStatusBadge = itemView.findViewById(R.id.tvHostStatusBadge);
            btnEdit = itemView.findViewById(R.id.btnEditAccount);
            btnResetPassword = itemView.findViewById(R.id.btnResetPasswordAccount);
            btnDelete = itemView.findViewById(R.id.btnDeleteAccount);
        }
    }
}

