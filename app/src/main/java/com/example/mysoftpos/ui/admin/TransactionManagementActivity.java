package com.example.mysoftpos.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;
import com.example.mysoftpos.ui.BaseActivity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Admin: View transactions from USER accounts only.
 * Features: Summary stats, user filter, status-colored pills.
 */
public class TransactionManagementActivity extends BaseActivity {

    private TxnAdapter adapter;
    private TextView tvTxnCount, tvApprovedCount, tvDeclinedCount, tvOtherCount;
    private Spinner spinnerUserFilter;
    private View layoutEmpty;

    private List<ApiService.TransactionSummaryDto> userTransactions = new ArrayList<>();
    private Map<Long, String> userIdToName = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_management);

        tvTxnCount = findViewById(R.id.tvTxnCount);
        tvApprovedCount = findViewById(R.id.tvApprovedCount);
        tvDeclinedCount = findViewById(R.id.tvDeclinedCount);
        tvOtherCount = findViewById(R.id.tvOtherCount);
        spinnerUserFilter = findViewById(R.id.spinnerUserFilter);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvTransactions);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TxnAdapter();
        rv.setAdapter(adapter);

        spinnerUserFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                filterByUser((String) parent.getItemAtPosition(pos));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        loadUsersAndTransactions();
    }

    private void loadUsersAndTransactions() {
        String token = ApiClient.bearerToken(this);
        ApiClient.getService(this).getUsers(token).enqueue(
                new Callback<List<ApiService.UserDto>>() {
                    @Override
                    public void onResponse(Call<List<ApiService.UserDto>> call,
                            Response<List<ApiService.UserDto>> resp) {
                        if (resp.isSuccessful() && resp.body() != null) {
                            userIdToName.clear();
                            for (ApiService.UserDto u : resp.body()) {
                                userIdToName.put(u.id, u.username);
                            }
                            loadTransactions();
                        } else {
                            Toast.makeText(TransactionManagementActivity.this,
                                    "Failed to load users", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<ApiService.UserDto>> call, Throwable t) {
                        Toast.makeText(TransactionManagementActivity.this,
                                "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadTransactions() {
        String token = ApiClient.bearerToken(this);
        ApiClient.getService(this).getAllTransactions(token).enqueue(
                new Callback<List<ApiService.TransactionSummaryDto>>() {
                    @Override
                    public void onResponse(Call<List<ApiService.TransactionSummaryDto>> call,
                            Response<List<ApiService.TransactionSummaryDto>> resp) {
                        if (resp.isSuccessful() && resp.body() != null) {
                            userTransactions = new ArrayList<>();
                            for (ApiService.TransactionSummaryDto txn : resp.body()) {
                                if (txn.userId != null && userIdToName.containsKey(txn.userId)) {
                                    userTransactions.add(txn);
                                }
                            }
                            populateUserFilter();
                            filterByUser("All Users");
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

    private void populateUserFilter() {
        List<String> names = new ArrayList<>();
        names.add("All Users");
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (ApiService.TransactionSummaryDto txn : userTransactions) {
            String name = txn.username != null ? txn.username : "Unknown";
            seen.put(name, true);
        }
        names.addAll(seen.keySet());

        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUserFilter.setAdapter(a);
    }

    private void filterByUser(String selectedUser) {
        List<ApiService.TransactionSummaryDto> filtered;
        if (selectedUser == null || "All Users".equals(selectedUser)) {
            filtered = userTransactions;
        } else {
            filtered = new ArrayList<>();
            for (ApiService.TransactionSummaryDto txn : userTransactions) {
                String name = txn.username != null ? txn.username : "Unknown";
                if (name.equals(selectedUser))
                    filtered.add(txn);
            }
        }
        adapter.setData(filtered);
        updateStats(filtered);
        tvTxnCount.setText(String.valueOf(filtered.size()));
        layoutEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void updateStats(List<ApiService.TransactionSummaryDto> txns) {
        int approved = 0, declined = 0, other = 0;
        for (ApiService.TransactionSummaryDto t : txns) {
            if ("APPROVED".equalsIgnoreCase(t.status))
                approved++;
            else if (t.status != null && t.status.toUpperCase().startsWith("DECLINED"))
                declined++;
            else
                other++;
        }
        tvApprovedCount.setText(String.valueOf(approved));
        tvDeclinedCount.setText(String.valueOf(declined));
        tvOtherCount.setText(String.valueOf(other));
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
            final TextView tvTrace, tvAmount, tvStatus, tvCardInfo, tvTerminal, tvTime, tvUsername;

            VH(View v) {
                super(v);
                tvTrace = v.findViewById(R.id.tvTraceNumber);
                tvAmount = v.findViewById(R.id.tvAmount);
                tvStatus = v.findViewById(R.id.tvStatus);
                tvCardInfo = v.findViewById(R.id.tvCardInfo);
                tvTerminal = v.findViewById(R.id.tvTerminal);
                tvTime = v.findViewById(R.id.tvTime);
                tvUsername = v.findViewById(R.id.tvUsername);
            }

            void bind(ApiService.TransactionSummaryDto txn) {
                tvTrace.setText("#" + (txn.traceNumber != null ? txn.traceNumber : "—"));
                tvAmount.setText(txn.amount != null ? txn.amount : "—");
                tvCardInfo.setText((txn.maskedPan != null ? txn.maskedPan : "") +
                        (txn.cardScheme != null ? " (" + txn.cardScheme + ")" : ""));
                tvTerminal.setText(txn.terminalCode != null ? txn.terminalCode : "—");
                tvTime.setText(txn.txnTimestamp != null ? txn.txnTimestamp : "—");

                if (tvUsername != null) {
                    tvUsername.setText(txn.username != null ? txn.username : "Unknown");
                }

                // Status pill with colored background
                String status = txn.status != null ? txn.status : "UNKNOWN";
                tvStatus.setText(status);
                if ("APPROVED".equalsIgnoreCase(status)) {
                    tvStatus.setBackgroundResource(R.drawable.bg_status_approved);
                    tvStatus.setTextColor(0xFFFFFFFF);
                } else if (status.toUpperCase().startsWith("DECLINED")) {
                    tvStatus.setBackgroundResource(R.drawable.bg_status_declined);
                    tvStatus.setTextColor(0xFFFFFFFF);
                } else {
                    tvStatus.setBackgroundResource(R.drawable.bg_status_other);
                    tvStatus.setTextColor(0xFFFFFFFF);
                }
            }
        }
    }
}
