package com.example.mysoftpos.testsuite.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mysoftpos.data.local.TestCaseEntity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TestCaseAdapter extends RecyclerView.Adapter<TestCaseAdapter.ViewHolder> {

    private List<TestCaseEntity> cases = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(TestCaseEntity testCase);
    }

    public TestCaseAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setCases(List<TestCaseEntity> cases) {
        this.cases = cases;
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
        TestCaseEntity testCase = cases.get(position);
        holder.text1.setText(testCase.name);

        String date = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(testCase.timestamp));
        String status = testCase.status != null ? testCase.status : "PENDING";

        holder.text2.setText(String.format("%s | %s", status, date));

        if ("PASS".equalsIgnoreCase(status) || "APPROVED".equalsIgnoreCase(status)) {
            holder.text2.setTextColor(Color.parseColor("#008000")); // Green
        } else if ("FAIL".equalsIgnoreCase(status) || status.startsWith("DECLINED") || status.startsWith("TIMEOUT")) {
            holder.text2.setTextColor(Color.RED);
        } else {
            holder.text2.setTextColor(Color.DKGRAY);
        }

        holder.itemView.setOnClickListener(v -> listener.onItemClick(testCase));
    }

    @Override
    public int getItemCount() {
        return cases.size();
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
