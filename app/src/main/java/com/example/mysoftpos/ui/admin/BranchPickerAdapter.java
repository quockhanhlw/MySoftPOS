package com.example.mysoftpos.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.remote.api.ApiService;

import java.util.ArrayList;
import java.util.List;

public class BranchPickerAdapter extends RecyclerView.Adapter<BranchPickerAdapter.BranchViewHolder> {

    public interface OnBranchClickListener {
        void onBranchClick(ApiService.BranchDto branch);
    }

    private final List<ApiService.BranchDto> branches = new ArrayList<>();
    private final OnBranchClickListener listener;

    public BranchPickerAdapter(OnBranchClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<ApiService.BranchDto> data) {
        branches.clear();
        if (data != null) {
            branches.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BranchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_branch_picker, parent, false);
        return new BranchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BranchViewHolder holder, int position) {
        ApiService.BranchDto branch = branches.get(position);
        String name = branch.branchName != null && !branch.branchName.trim().isEmpty()
                ? branch.branchName.trim()
                : branch.branchCode;
        int count = branch.accountCount != null ? branch.accountCount : 0;

        holder.tvBranchName.setText(name);
        holder.tvBranchCode.setText(holder.itemView.getContext().getString(R.string.user_mgmt_branch_code_format, branch.branchCode));
        holder.tvBranchAccountBadge
                .setText(holder.itemView.getContext().getString(R.string.user_mgmt_branch_accounts_badge, count));
        holder.itemView.setOnClickListener(v -> listener.onBranchClick(branch));
    }

    @Override
    public int getItemCount() {
        return branches.size();
    }

    static class BranchViewHolder extends RecyclerView.ViewHolder {
        final TextView tvBranchName;
        final TextView tvBranchCode;
        final TextView tvBranchAccountBadge;

        BranchViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBranchName = itemView.findViewById(R.id.tvBranchName);
            tvBranchCode = itemView.findViewById(R.id.tvBranchCode);
            tvBranchAccountBadge = itemView.findViewById(R.id.tvBranchAccountBadge);
        }
    }
}

