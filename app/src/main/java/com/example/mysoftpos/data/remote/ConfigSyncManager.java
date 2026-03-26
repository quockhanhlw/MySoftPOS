package com.example.mysoftpos.data.remote;

import android.content.Context;
import android.util.Log;

import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.local.dao.BranchDao;
import com.example.mysoftpos.data.local.dao.MerchantDao;
import com.example.mysoftpos.data.local.dao.TerminalDao;
import com.example.mysoftpos.data.local.entity.BranchEntity;
import com.example.mysoftpos.data.local.entity.MerchantEntity;
import com.example.mysoftpos.data.local.entity.TerminalEntity;
import com.example.mysoftpos.data.remote.api.ApiClient;
import com.example.mysoftpos.data.remote.api.ApiService;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ConfigSyncManager {

    private static final String TAG = "ConfigSyncManager";
    private final Context context;

    public ConfigSyncManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void sync() {
        if (!ApiClient.isLoggedIn(context)) {
            Log.d(TAG, "Not logged in, skipping config sync");
            return;
        }
        syncMerchants();
        syncTerminals();
    }

    private void syncMerchants() {
        String token = ApiClient.bearerToken(context);
        ApiClient.getService(context).getMerchants(token).enqueue(new Callback<List<ApiService.MerchantDto>>() {
            @Override
            public void onResponse(Call<List<ApiService.MerchantDto>> call,
                                   Response<List<ApiService.MerchantDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    new Thread(() -> {
                        try {
                            MerchantDao dao = AppDatabase.getInstance(context).merchantDao();
                            for (ApiService.MerchantDto dto : response.body()) {
                                MerchantEntity existing = dao.getByBackendId(dto.id);
                                if (existing != null) {
                                    existing.merchantCode = dto.merchantCode;
                                    existing.merchantNameLocation = dto.merchantName;
                                    existing.adminBackendId = dto.adminId;
                                    existing.ownerUserBackendId = dto.ownerUserId != null ? dto.ownerUserId : 0L;
                                    existing.businessType = dto.businessType != null ? dto.businessType : "";
                                    existing.storeAddress = dto.storeAddress != null ? dto.storeAddress : "";
                                    dao.update(existing);
                                } else {
                                    existing = dao.getByCode(dto.merchantCode);
                                    if (existing != null) {
                                        existing.backendId = dto.id;
                                        existing.merchantNameLocation = dto.merchantName;
                                        existing.adminBackendId = dto.adminId;
                                        existing.ownerUserBackendId = dto.ownerUserId != null ? dto.ownerUserId : 0L;
                                        existing.businessType = dto.businessType != null ? dto.businessType : "";
                                        existing.storeAddress = dto.storeAddress != null ? dto.storeAddress : "";
                                        dao.update(existing);
                                    } else {
                                        MerchantEntity entity = new MerchantEntity();
                                        entity.backendId = dto.id;
                                        entity.merchantCode = dto.merchantCode;
                                        entity.merchantNameLocation = dto.merchantName;
                                        entity.adminBackendId = dto.adminId;
                                        entity.ownerUserBackendId = dto.ownerUserId != null ? dto.ownerUserId : 0L;
                                        entity.businessType = dto.businessType != null ? dto.businessType : "";
                                        entity.storeAddress = dto.storeAddress != null ? dto.storeAddress : "";
                                        dao.insert(entity);
                                    }
                                }
                            }
                            Log.i(TAG, "Synced " + response.body().size() + " merchants");
                            syncBranches(response.body());
                        } catch (Exception e) {
                            Log.e(TAG, "Error syncing merchants: " + e.getMessage(), e);
                        }
                    }).start();
                }
            }

            @Override
            public void onFailure(Call<List<ApiService.MerchantDto>> call, Throwable t) {
                Log.w(TAG, "Merchant sync error: " + t.getMessage());
            }
        });
    }

    private void syncBranches(List<ApiService.MerchantDto> merchants) {
        if (merchants == null || merchants.isEmpty()) {
            return;
        }
        String token = ApiClient.bearerToken(context);
        for (ApiService.MerchantDto merchant : merchants) {
            if (merchant == null || merchant.id <= 0) {
                continue;
            }
            ApiClient.getService(context).getMerchantBranches(token, merchant.id)
                    .enqueue(new Callback<List<ApiService.BranchDto>>() {
                        @Override
                        public void onResponse(Call<List<ApiService.BranchDto>> call,
                                               Response<List<ApiService.BranchDto>> response) {
                            if (!response.isSuccessful() || response.body() == null) {
                                return;
                            }
                            new Thread(() -> {
                                try {
                                    BranchDao branchDao = AppDatabase.getInstance(context).branchDao();
                                    for (ApiService.BranchDto dto : response.body()) {
                                        BranchEntity local = branchDao.getByBackendId(dto.id);
                                        if (local == null) {
                                            local = branchDao.getByMerchantBackendIdAndCode(
                                                    dto.merchantId != null ? dto.merchantId : 0L,
                                                    dto.branchCode != null ? dto.branchCode : "");
                                        }
                                        if (local == null) {
                                            local = new BranchEntity();
                                        }
                                        local.backendId = dto.id;
                                        local.merchantBackendId = dto.merchantId != null ? dto.merchantId : 0L;
                                        local.branchCode = dto.branchCode;
                                        local.branchName = dto.branchName;
                                        local.branchAddress = dto.branchAddress;
                                        local.createdAt = System.currentTimeMillis();

                                        if (local.id > 0) {
                                            branchDao.update(local);
                                        } else {
                                            branchDao.insert(local);
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Branch sync error: " + e.getMessage());
                                }
                            }).start();
                        }

                        @Override
                        public void onFailure(Call<List<ApiService.BranchDto>> call, Throwable t) {
                            Log.w(TAG, "Branch sync network error: " + t.getMessage());
                        }
                    });
        }
    }

    private void syncTerminals() {
        String token = ApiClient.bearerToken(context);
        ApiClient.getService(context).getTerminals(token).enqueue(new Callback<List<ApiService.TerminalDto>>() {
            @Override
            public void onResponse(Call<List<ApiService.TerminalDto>> call,
                                   Response<List<ApiService.TerminalDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    new Thread(() -> {
                        try {
                            AppDatabase db = AppDatabase.getInstance(context);
                            TerminalDao terminalDao = db.terminalDao();
                            MerchantDao merchantDao = db.merchantDao();
                            for (ApiService.TerminalDto dto : response.body()) {
                                long localMerchantId = 0;
                                if (dto.merchant != null) {
                                    MerchantEntity m = merchantDao.getByBackendId(dto.merchant.id);
                                    if (m == null) m = merchantDao.getByCode(dto.merchant.merchantCode);
                                    if (m != null) localMerchantId = m.id;
                                }
                                TerminalEntity existing = terminalDao.getByBackendId(dto.id);
                                if (existing != null) {
                                    existing.terminalCode = dto.terminalCode;
                                    existing.serverIp = dto.serverIp;
                                    existing.serverPort = dto.serverPort != null ? dto.serverPort : 0;
                                    existing.branchBackendId = dto.branchId != null ? dto.branchId : 0L;
                                    existing.posAccountBackendId = dto.posAccountId != null ? dto.posAccountId : 0L;
                                    if (localMerchantId > 0) existing.merchantId = localMerchantId;
                                    terminalDao.update(existing);
                                } else {
                                    existing = terminalDao.getByCode(dto.terminalCode);
                                    if (existing != null) {
                                        existing.backendId = dto.id;
                                        existing.serverIp = dto.serverIp;
                                        existing.serverPort = dto.serverPort != null ? dto.serverPort : 0;
                                        existing.branchBackendId = dto.branchId != null ? dto.branchId : 0L;
                                        existing.posAccountBackendId = dto.posAccountId != null ? dto.posAccountId : 0L;
                                        if (localMerchantId > 0) existing.merchantId = localMerchantId;
                                        terminalDao.update(existing);
                                    } else {
                                        TerminalEntity entity = new TerminalEntity();
                                        entity.backendId = dto.id;
                                        entity.terminalCode = dto.terminalCode;
                                        entity.merchantId = localMerchantId;
                                        entity.serverIp = dto.serverIp;
                                        entity.serverPort = dto.serverPort != null ? dto.serverPort : 0;
                                        entity.branchBackendId = dto.branchId != null ? dto.branchId : 0L;
                                        entity.posAccountBackendId = dto.posAccountId != null ? dto.posAccountId : 0L;
                                        terminalDao.insert(entity);
                                    }
                                }
                            }
                            Log.i(TAG, "Synced " + response.body().size() + " terminals");
                        } catch (Exception e) {
                            Log.e(TAG, "Error syncing terminals: " + e.getMessage(), e);
                        }
                    }).start();
                }
            }

            @Override
            public void onFailure(Call<List<ApiService.TerminalDto>> call, Throwable t) {
                Log.w(TAG, "Terminal sync error: " + t.getMessage());
            }
        });
    }
}

