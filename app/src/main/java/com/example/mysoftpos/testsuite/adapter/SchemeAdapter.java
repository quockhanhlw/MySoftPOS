package com.example.mysoftpos.testsuite.adapter;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.testsuite.model.Scheme;

import java.util.List;

public class SchemeAdapter extends RecyclerView.Adapter<SchemeAdapter.ViewHolder> {

    private final List<Scheme> schemes;
    private final OnSchemeListener listener;

    public interface OnSchemeListener {
        void onSchemeClick(Scheme scheme);

        void onSchemeLongClick(Scheme scheme);
    }

    public SchemeAdapter(List<Scheme> schemes, OnSchemeListener listener) {
        this.schemes = schemes;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scheme, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Scheme scheme = schemes.get(position);
        holder.bind(scheme, listener);
    }

    @Override
    public int getItemCount() {
        return schemes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final FrameLayout iconContainer;
        final TextView tvIconLetter;
        final TextView tvName;
        final TextView tvPrefix;
        final TextView tvBuiltIn;

        ViewHolder(View view) {
            super(view);
            iconContainer = view.findViewById(R.id.iconContainer);
            tvIconLetter = view.findViewById(R.id.tvIconLetter);
            tvName = view.findViewById(R.id.tvName);
            tvPrefix = view.findViewById(R.id.tvPrefix);
            tvBuiltIn = view.findViewById(R.id.tvBuiltIn);
        }

        void bind(Scheme scheme, OnSchemeListener listener) {
            tvName.setText(scheme.getName());
            tvIconLetter.setText(scheme.getIconLetter());

            String prefix = scheme.getPrefix();
            tvPrefix.setText(prefix != null && !prefix.isEmpty()
                    ? "BIN: " + prefix + "xxxx"
                    : "No BIN prefix");

            tvBuiltIn.setVisibility(scheme.isBuiltIn() ? View.VISIBLE : View.GONE);

            // Icon circle color
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            try {
                circle.setColor(Color.parseColor(scheme.getColor()));
            } catch (Exception e) {
                circle.setColor(Color.parseColor("#607D8B"));
            }
            iconContainer.setBackground(circle);

            itemView.setOnClickListener(v -> listener.onSchemeClick(scheme));
            itemView.setOnLongClickListener(v -> {
                listener.onSchemeLongClick(scheme);
                return true;
            });
        }
    }
}
