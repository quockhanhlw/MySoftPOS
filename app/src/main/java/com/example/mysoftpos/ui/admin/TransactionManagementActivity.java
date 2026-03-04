package com.example.mysoftpos.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;
import com.example.mysoftpos.ui.BaseActivity;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Admin: View all transactions synced from all User devices.
 */
public class TransactionManagementActivity extends BaseActivity {

    private TxnAdapter adapter;
    private TextView tvTxnCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_management);

        tvTxnCount = findViewById(R.id.tvTxnCount);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvTransactions);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TxnAdapter();
        rv.setAdapter(adapter);

        loadTransactions();
    }

    private void loadTransactions() {
        String token = ApiClient.bearerToken(this);
        ApiClient.getService(this).getAllTransactions(token).enqueue(
                new Callback<List<ApiService.TransactionSummaryDto>>() {
                    @Override
                    public void onResponse(Call<List<ApiService.TransactionSummaryDto>> call,
                            Response<List<ApiService.TransactionSummaryDto>> resp) {
                        if (resp.isSuccessful() && resp.body() != null) {
                            List<ApiService.TransactionSummaryDto> txns = resp.body();
                            adapter.setData(txns);
                            tvTxnCount.setText(txns.size() + " transaction(s)");
                        } else {
                            Toast.makeText(TransactionManagementActivity.this,
                                    "Failed to load transactions", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<ApiService.TransactionSummaryDto>> call, Throwable t) {
                        Toast.makeText(TransactionManagementActivity.this,
                                "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ====== Adapter ======
    static class TxnAdapter extends RecyclerView.Adapter<TxnAdapter.VH> {
        private List<ApiService.TransactionSummaryDto> data = new ArrayList<>();

        void setData(List<ApiService.TransactionSummaryDto> data) {
            this.data = data != null ? data : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transaction_summary, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(data.get(position));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvTrace, tvAmount, tvStatus, tvCardInfo, tvTerminal, tvTime;

            VH(View v) {
                super(v);
                tvTrace = v.findViewById(R.id.tvTraceNumber);
                tvAmount = v.findViewById(R.id.tvAmount);
                tvStatus = v.findViewById(R.id.tvStatus);
                tvCardInfo = v.findViewById(R.id.tvCardInfo);
                tvTerminal = v.findViewById(R.id.tvTerminal);
                tvTime = v.findViewById(R.id.tvTime);
            }

            void bind(ApiService.TransactionSummaryDto txn) {
                tvTrace.setText("Trace: " + txn.traceNumber);
                tvAmount.setText(txn.amount != null ? txn.amount : "—");
                tvStatus.setText(txn.status != null ? txn.status : "—");
                tvCardInfo.setText((txn.maskedPan != null ? txn.maskedPan : "") +
                        (txn.cardScheme != null ? " (" + txn.cardScheme + ")" : ""));
                tvTerminal.setText("TID: " + (txn.terminalCode != null ? txn.terminalCode : "—"));
                tvTime.setText(txn.txnTimestamp != null ? txn.txnTimestamp : "—");

                // Status color
                if ("APPROVED".equalsIgnoreCase(txn.status)) {
                    tvStatus.setTextColor(0xFF4CAF50);
                } else if ("DECLINED".equalsIgnoreCase(txn.status)) {
                    tvStatus.setTextColor(0xFFF44336);
                } else {
                    tvStatus.setTextColor(0xFFFF9800);
                }
            }
        }
    }
}
