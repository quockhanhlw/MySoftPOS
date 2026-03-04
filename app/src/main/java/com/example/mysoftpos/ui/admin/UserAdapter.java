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

import java.util.ArrayList;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {

    private List<ApiService.UserDto> users = new ArrayList<>();
    private final OnUserListener listener;

    private static final String[] COLORS = {
            "#0D9488", "#1565C0", "#7C3AED", "#C2410C", "#059669", "#DB2777", "#0369A1", "#B45309"
    };

    public interface OnUserListener {
        void onUserClick(ApiService.UserDto user);

        void onUserLongClick(ApiService.UserDto user);
    }

    public UserAdapter(OnUserListener listener) {
        this.listener = listener;
    }

    public void setUsers(List<ApiService.UserDto> users) {
        this.users = users != null ? users : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(users.get(position), position, listener);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final FrameLayout avatarContainer;
        final TextView tvAvatarLetter;
        final TextView tvName;
        final TextView tvPhone;
        final TextView tvServerInfo;

        ViewHolder(View view) {
            super(view);
            avatarContainer = view.findViewById(R.id.avatarContainer);
            tvAvatarLetter = view.findViewById(R.id.tvAvatarLetter);
            tvName = view.findViewById(R.id.tvName);
            tvPhone = view.findViewById(R.id.tvPhone);
            tvServerInfo = view.findViewById(R.id.tvServerInfo);
        }

        void bind(ApiService.UserDto user, int position, OnUserListener listener) {
            String name = user.fullName != null ? user.fullName : "User";
            tvName.setText(name);
            tvPhone.setText(user.phone != null ? user.phone : "—");

            // Avatar
            String letter = name.substring(0, 1).toUpperCase();
            tvAvatarLetter.setText(letter);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.parseColor(COLORS[position % COLORS.length]));
            avatarContainer.setBackground(circle);

            // TID badge
            if (user.terminalId != null && !user.terminalId.isEmpty()) {
                tvServerInfo.setText("TID: " + user.terminalId);
                tvServerInfo.setVisibility(View.VISIBLE);
            } else {
                tvServerInfo.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onUserClick(user));
            itemView.setOnLongClickListener(v -> {
                listener.onUserLongClick(user);
                return true;
            });
        }
    }
}
