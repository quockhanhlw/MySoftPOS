package com.example.mysoftpos.testsuite.viewmodel;

import android.app.Application;
import android.util.Log;
import com.example.mysoftpos.R;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.mysoftpos.data.repository.TransactionRepository;
import com.example.mysoftpos.di.ServiceLocator;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.domain.service.TransactionExecutor;
import com.example.mysoftpos.domain.service.TransactionResult;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.iso8583.builder.Iso8583Builder;
import com.example.mysoftpos.iso8583.message.IsoMessage;
import com.example.mysoftpos.iso8583.util.StandardIsoPacker;
import com.example.mysoftpos.utils.PanUtils;
import com.example.mysoftpos.utils.logging.ResponseCodeHelper;
import com.example.mysoftpos.testsuite.model.Scheme;
import com.example.mysoftpos.testsuite.storage.SchemeRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RunnerViewModel extends AndroidViewModel {

    private static final String ADMIN_TEST_SUITE_MID = "MYSOFTPOSSHOP01";

    private final MutableLiveData<String> logMessage = new MutableLiveData<>();
    private final MutableLiveData<String> previewMessage = new MutableLiveData<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final TransactionRepository repository;
    private final TransactionExecutor transactionExecutor;

    public RunnerViewModel(Application application) {
        super(application);
        this.repository = ServiceLocator.getInstance(application).getTransactionRepository();
        this.transactionExecutor = ServiceLocator.getInstance(application).getTransactionExecutor();
    }

    public LiveData<String> getLogMessage() {
        return logMessage;
    }

    public LiveData<String> getPreviewMessage() {
        return previewMessage;
    }

    /**
     * Build ISO message and show field breakdown without sending.
     */
    public void previewTransaction(String de22, String track2Data, String panData, String expiryData,
            String pinBlockData, String txnType, String amount, String currencyCode, String countryCode,
            String schemeName, String fieldConfigJson) {
        executor.execute(() -> {
            try {
                Application app = getApplication();
                StringBuilder sb = new StringBuilder();
                TransactionExecutor.LogCallback logger = msg -> sb.append(msg).append("\n");

                TransactionContext ctx = TransactionExecutor.buildContext(getApplication(), txnType, amount,
                        currencyCode, countryCode);
                ctx.merchantId42 = ADMIN_TEST_SUITE_MID;
                applySchemeConnection(ctx, schemeName);

                CardInputData card = TransactionExecutor.prepareCard(
                        getApplication(), de22, panData, expiryData, track2Data, pinBlockData, ctx, logger);

                IsoMessage msg;
                if ("BALANCE".equals(txnType)) {
                    msg = Iso8583Builder.buildBalanceMsg(ctx, card);
                } else {
                    msg = Iso8583Builder.buildPurchaseMsg(ctx, card);
                }

                // Apply custom field overrides
                applyCustomFieldOverrides(msg, fieldConfigJson);

                sb.append(app.getString(R.string.runner_preview_header)).append("\n");
                sb.append(app.getString(R.string.runner_preview_server_format, ctx.ip, ctx.port)).append("\n");
                sb.append(StandardIsoPacker.logIsoMessage(msg));

                byte[] packed = StandardIsoPacker.pack(msg);
                String reqHex = StandardIsoPacker.bytesToHex(packed);
                sb.append("\n").append(app.getString(R.string.runner_packed_hex_bytes_format, reqHex.length() / 2)).append("\n");
                sb.append(reqHex).append("\n");
                sb.append(app.getString(R.string.runner_preview_footer)).append("\n");

                previewMessage.postValue(sb.toString());
            } catch (Exception e) {
                previewMessage.postValue(getApplication().getString(R.string.runner_preview_error_format, e.getMessage()));
            }
        });
    }

    public void runTransaction(String de22, String track2Data, String panData, String expiryData,
            String pinBlockData, String txnType, String amount, String currencyCode, String countryCode,
            String schemeName, String fieldConfigJson) {
        executor.execute(() -> {
            try {
                Application app = getApplication();
                StringBuilder sb = new StringBuilder();

                TransactionExecutor.LogCallback logger = msg -> sb.append(msg).append("\n");

                TransactionContext ctx = TransactionExecutor.buildContext(getApplication(), txnType, amount,
                        currencyCode, countryCode);
                ctx.merchantId42 = ADMIN_TEST_SUITE_MID;
                applySchemeConnection(ctx, schemeName);

                CardInputData card = TransactionExecutor.prepareCard(
                        getApplication(), de22, panData, expiryData, track2Data, pinBlockData, ctx, logger);

                String txnLabel = "BALANCE".equals(txnType)
                        ? app.getString(R.string.runner_txn_balance)
                        : app.getString(R.string.runner_txn_purchase);
                sb.append(app.getString(R.string.runner_building_format, txnLabel, ctx.ip, ctx.port));

                TransactionResult result = transactionExecutor.execute(
                        getApplication(), ctx, card, txnType, logger, "", sanitizeFieldConfigJson(fieldConfigJson));

                sb.append("\n").append(app.getString(R.string.runner_packed_hex_bytes_format, result.reqHex.length() / 2)).append("\n")
                        .append(result.reqHex).append("\n");
                sb.append("\n").append(app.getString(R.string.runner_response_hex_label)).append("\n")
                        .append(result.respHex).append("\n");

                String reason = ResponseCodeHelper.getMessage(result.rc);
                if (result.approved) {
                    sb.append("\n").append(app.getString(R.string.runner_status_pass_banner)).append("\n");
                    sb.append(app.getString(R.string.runner_rc_success_format, result.rc, reason)).append("\n");
                } else {
                    sb.append("\n").append(app.getString(R.string.runner_status_fail_banner)).append("\n");
                    sb.append(app.getString(R.string.runner_rc_fail_format, result.rc, reason)).append("\n");
                }

                logMessage.postValue(sb.toString());

                saveTransactionToDb(ctx, card, result);

            } catch (java.net.SocketTimeoutException e) {
                Application app = getApplication();
                logMessage.postValue("\n" + app.getString(R.string.runner_status_fail_banner) + "\n"
                        + app.getString(R.string.runner_error_timeout));
            } catch (Exception e) {
                Application app = getApplication();
                logMessage.postValue("\n" + app.getString(R.string.runner_status_fail_banner) + "\n"
                        + app.getString(R.string.runner_error_with_reason, e.getMessage()));
                Log.e("RunnerVM", "Run transaction", e);
            }
        });
    }

    /** Apply custom field overrides from JSON to an IsoMessage (for preview) */
    private void applyCustomFieldOverrides(IsoMessage msg, String fieldConfigJson) {
        if (fieldConfigJson == null || fieldConfigJson.isEmpty()) return;
        try {
            org.json.JSONObject json = new org.json.JSONObject(fieldConfigJson);
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                int fieldNum = Integer.parseInt(key);
                if (fieldNum == 42) {
                    continue;
                }
                msg.setField(fieldNum, json.getString(key));
            }
            msg.setField(42, ADMIN_TEST_SUITE_MID);
        } catch (Exception e) {
            Log.w("RunnerVM", "Failed to apply custom fields: " + e.getMessage());
        }
    }

    private String sanitizeFieldConfigJson(String fieldConfigJson) {
        if (fieldConfigJson == null || fieldConfigJson.isEmpty()) {
            return null;
        }
        try {
            org.json.JSONObject json = new org.json.JSONObject(fieldConfigJson);
            json.remove("42");
            return json.length() > 0 ? json.toString() : null;
        } catch (Exception e) {
            Log.w("RunnerVM", "Failed to sanitize custom fields: " + e.getMessage());
            return null;
        }
    }

    /** Override ctx fields from per-scheme config if available */
    private void applySchemeConnection(TransactionContext ctx, String schemeName) {
        ctx.merchantId42 = ADMIN_TEST_SUITE_MID;
        if (schemeName == null || schemeName.isEmpty())
            return;
        try {
            SchemeRepository repo = new SchemeRepository(getApplication());
            Scheme scheme = repo.getByName(schemeName);
            if (scheme == null) return;

            // Connection
            if (scheme.hasConnectionConfig()) {
                ctx.ip = scheme.getServerIp();
                ctx.port = scheme.getServerPort();
            }

            // Terminal / Merchant — override only if configured in scheme
            String tid = scheme.getTerminalId();
            if (tid != null && !tid.isEmpty()) ctx.terminalId41 = tid;


            String mcc = scheme.getMcc();
            if (mcc != null && !mcc.isEmpty()) ctx.mcc18 = mcc;

            String acq = scheme.getAcquirerId();
            if (acq != null && !acq.isEmpty()) ctx.acquirerId32 = acq;


            String currency = scheme.getCurrencyCode();
            if (currency != null && !currency.isEmpty()) ctx.currency49 = currency;

            String country = scheme.getCountryCode();
            if (country != null && !country.isEmpty()) ctx.country19 = country;

            String posCond = scheme.getPosConditionCode();
            if (posCond != null && !posCond.isEmpty()) ctx.posCondition25 = posCond;

            // Merchant Name/Location (DE 43)
            String de43 = scheme.buildMerchantNameLocation();
            if (!de43.isEmpty()) ctx.merchantNameLocation43 = de43;

        } catch (Exception e) {
            Log.w("RunnerVM", "Failed to load scheme config: " + e.getMessage());
        }
    }

    private void saveTransactionToDb(TransactionContext ctx, CardInputData card,
                                      TransactionResult result) {
        try {
            String pan = card.getPan();
            com.example.mysoftpos.domain.model.TransactionRecord record = new com.example.mysoftpos.domain.model.TransactionRecord.Builder()
                    .setTraceNumber(ctx.stan11)
                    .setAmount(ctx.amount4)
                    .setStatus(result.status)
                    .setRequestHex(result.reqHex)
                    .setResponseHex(result.respHex)
                    .setTimestamp(System.currentTimeMillis())
                    .setMerchantCode(ctx.merchantId42)
                    .setMerchantName(ctx.merchantNameLocation43)
                    .setTerminalCode(ctx.terminalId41)
                    .setPanMasked(PanUtils.mask(pan))
                    .setBin(PanUtils.getBin(pan))
                    .setLast4(PanUtils.getLast4(pan))
                    .setScheme(PanUtils.detectScheme(pan))
                    .setUsername("TEST_SUITE_USER")
                    .setProcessingCode(ctx.processingCode3)
                    .setCurrencyCode(ctx.currency49)
                    .build();
            repository.saveTransaction(record);

            // Sync to backend via WorkManager
            com.example.mysoftpos.data.remote.SyncWorker.enqueueOneTime(getApplication());

            logMessage.postValue(getApplication().getString(R.string.runner_saved_to_history_format, ctx.stan11));
        } catch (Exception e) {
            logMessage.postValue(getApplication().getString(R.string.runner_save_db_error_format, e.getMessage()));
            Log.e("RunnerVM", "Save to DB", e);
        }
    }
}
