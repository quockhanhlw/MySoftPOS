package com.example.mysoftpos.testsuite.viewmodel;

import android.app.Application;
import android.util.Log;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RunnerViewModel extends AndroidViewModel {

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
    /**
     * Build ISO message and show field breakdown without sending.
     */
    public void previewTransaction(String de22, String track2Data, String panData, String expiryData,
            String pinBlockData, String txnType, String amount, String currencyCode, String countryCode) {
        executor.execute(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                TransactionExecutor.LogCallback logger = msg -> sb.append(msg).append("\n");

                TransactionContext ctx = TransactionExecutor.buildContext(getApplication(), txnType, amount,
                        currencyCode, countryCode);
                CardInputData card = TransactionExecutor.prepareCard(
                        getApplication(), de22, panData, expiryData, track2Data, pinBlockData, ctx, logger);

                IsoMessage msg;
                if ("BALANCE".equals(txnType)) {
                    msg = Iso8583Builder.buildBalanceMsg(ctx, card);
                } else {
                    msg = Iso8583Builder.buildPurchaseMsg(ctx, card);
                }

                sb.append("=== ISO MESSAGE PREVIEW ===\n");
                sb.append(StandardIsoPacker.logIsoMessage(msg));

                byte[] packed = StandardIsoPacker.pack(msg);
                String reqHex = StandardIsoPacker.bytesToHex(packed);
                sb.append("\nPacked Hex (").append(reqHex.length() / 2).append(" bytes):\n");
                sb.append(reqHex).append("\n");
                sb.append("===========================\n");

                previewMessage.postValue(sb.toString());
            } catch (Exception e) {
                previewMessage.postValue("Preview Error: " + e.getMessage());
            }
        });
    }

    public void runTransaction(String de22, String track2Data, String panData, String expiryData,
            String pinBlockData, String txnType, String amount, String currencyCode, String countryCode) {
        executor.execute(() -> {
            try {
                StringBuilder sb = new StringBuilder();

                TransactionExecutor.LogCallback logger = msg -> sb.append(msg).append("\n");

                TransactionContext ctx = TransactionExecutor.buildContext(getApplication(), txnType, amount,
                        currencyCode, countryCode);

                CardInputData card = TransactionExecutor.prepareCard(
                        getApplication(), de22, panData, expiryData, track2Data, pinBlockData, ctx, logger);

                sb.append("Building ").append("BALANCE".equals(txnType) ? "Balance Inquiry" : "Purchase")
                        .append("...\n");

                TransactionResult result = transactionExecutor.execute(
                        getApplication(), ctx, card, txnType, logger, "");

                sb.append("\nPacked Hex (").append(result.reqHex.length() / 2).append(" bytes):\n")
                        .append(result.reqHex).append("\n");
                sb.append("\nResponse Hex:\n").append(result.respHex).append("\n");

                String reason = ResponseCodeHelper.getMessage(result.rc);
                if (result.approved) {
                    sb.append("\n*** STATUS: PASS ***\n");
                    sb.append("RC: ").append(result.rc).append(" (").append(reason).append(")\n");
                } else {
                    sb.append("\n*** STATUS: FAIL ***\n");
                    sb.append("RC: ").append(result.rc).append(" - Reason: ").append(reason).append("\n");
                }

                logMessage.postValue(sb.toString());

                saveTransactionToDb(ctx, card, result);

            } catch (java.net.SocketTimeoutException e) {
                logMessage.postValue("\n*** STATUS: FAIL ***\nError: Timeout waiting for response.");
            } catch (Exception e) {
                logMessage.postValue("\n*** STATUS: FAIL ***\nError: " + e.getMessage());
                Log.e("RunnerVM", "Run transaction", e);
            }
        });
    }

    private void saveTransactionToDb(TransactionContext ctx, CardInputData card, TransactionResult result) {
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
                    .build();
            repository.saveTransaction(record);

            logMessage.postValue("Transaction saved to History (Trace: " + ctx.stan11 + ")");
        } catch (Exception e) {
            logMessage.postValue("Error saving to DB: " + e.getMessage());
            Log.e("RunnerVM", "Save to DB", e);
        }
    }
}
