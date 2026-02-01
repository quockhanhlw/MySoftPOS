package com.example.mysoftpos.testsuite.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.TestSuiteEntity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TestSuiteAdapter extends RecyclerView.Adapter<TestSuiteAdapter.ViewHolder> {

    private List<TestSuiteEntity> suites = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(TestSuiteEntity suite);
    }

    public TestSuiteAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setSuites(List<TestSuiteEntity> suites) {
        this.suites = suites;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TestSuiteEntity suite = suites.get(position);
        holder.text1.setText(suite.name);
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(suite.createdAt));
        holder.text2.setText(suite.description + "\nCreated: " + date);

        holder.itemView.setOnClickListener(v -> listener.onItemClick(suite));
    }

    @Override
    public int getItemCount() {
        return suites.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView text1;
        public TextView text2;

        public ViewHolder(View view) {
            super(view);
            text1 = view.findViewById(android.R.id.text1);
            text2 = view.findViewById(android.R.id.text2);
        }
    }
}
