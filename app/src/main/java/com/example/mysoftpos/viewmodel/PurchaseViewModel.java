package com.example.mysoftpos.viewmodel;

import com.example.mysoftpos.R;
import com.example.mysoftpos.utils.logging.ResponseCodeHelper;
import com.example.mysoftpos.utils.logging.FileLogger;

import android.app.Application;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.mysoftpos.data.local.entity.TransactionEntity;
import com.example.mysoftpos.data.remote.IsoNetworkClient;
import com.example.mysoftpos.data.repository.TransactionRepository;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.builder.Iso8583Builder;
import com.example.mysoftpos.iso8583.spec.IsoField;
import com.example.mysoftpos.iso8583.message.IsoMessage;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.ui.base.BaseViewModel;
import com.example.mysoftpos.utils.PanUtils;
import com.example.mysoftpos.utils.config.ConfigManager;
import com.example.mysoftpos.utils.threading.DispatcherProvider;
import com.example.mysoftpos.iso8583.util.StandardIsoPacker;
import com.example.mysoftpos.utils.validation.TransactionValidator;
import java.net.SocketTimeoutException;

public class PurchaseViewModel extends BaseViewModel {

    private static final String TAG = "PurchaseViewModel";
    private final TransactionRepository repository;
    private final ConfigManager configManager;
    private final IsoNetworkClient isoNetworkClient;
    private final MutableLiveData<TransactionState> state = new MutableLiveData<>();

    public PurchaseViewModel(Application application, TransactionRepository repository, ConfigManager configManager,
            DispatcherProvider dispatchers, IsoNetworkClient isoNetworkClient) {
        super(application, dispatchers);
        this.repository = repository;
        this.configManager = configManager;
        this.isoNetworkClient = isoNetworkClient;
    }

    public LiveData<TransactionState> getState() {
        return state;
    }

    public void processTransaction(CardInputData card, String amount, String currencyCode, TxnType txnType,
            String username, long userId) {
        state.setValue(TransactionState.loading());

        launchIo(() -> {
            TransactionContext ctx = new TransactionContext();
            TransactionEntity entity = new TransactionEntity();

            try {
                // Validation
                boolean isPurchase = (txnType == TxnType.PURCHASE);
                String amtF4 = isPurchase ? TransactionContext.formatAmount12(amount, currencyCode) : "000000000000";

                TransactionValidator.ValidationResult v = TransactionValidator.validate(card, amount, isPurchase);
                if (v != TransactionValidator.ValidationResult.VALID) {
                    postError(getApplication().getString(R.string.err_validation_failed, v.toString()));
                    return;
                }

                // Build Context
                ctx.txnType = txnType;
                ctx.amount4 = amtF4;
                ctx.stan11 = configManager.getAndIncrementTrace();
                ctx.generateDateTime();
                ctx.rrn37 = TransactionContext.calculateRrn(configManager.getServerId(), ctx.stan11);
                ctx.processingCode3 = (txnType == TxnType.BALANCE_INQUIRY)
                        ? configManager.getProcessingCodeBalance()
                        : configManager.getProcessingCodePurchase();
                ctx.mcc18 = configManager.getMcc18();
                ctx.posCondition25 = configManager.getPosConditionCode();
                ctx.acquirerId32 = configManager.getAcquirerId32();
                ctx.currency49 = currencyCode != null ? currencyCode : configManager.getCurrencyCode49();
                ctx.terminalId41 = configManager.getTerminalId();
                ctx.merchantId42 = configManager.getMerchantId();

                // DE 43 customization for USD
                String usdCode = configManager.getUsdCurrencyCode();
                if (usdCode.equals(ctx.currency49)) {
                    String base43 = configManager.getMerchantName();
                    String suffix = configManager.getUsdCountrySuffix();
                    ctx.merchantNameLocation43 = base43.length() >= 3
                            ? base43.substring(0, base43.length() - 3) + suffix
                            : base43;
                } else {
                    ctx.merchantNameLocation43 = configManager.getMerchantName();
                }

                ctx.ip = configManager.getServerIp();
                ctx.port = configManager.getServerPort();

                // Populate card-related fields into ctx for reversal
                ctx.posEntryMode22 = card.getPosEntryMode();
                ctx.country19 = ctx.currency49; // VN uses same code for country and currency
                if (card.getTrack2() != null && !card.getTrack2().isEmpty()) {
                    ctx.track2_35 = card.getTrack2().replace('=', 'D');
                }
                if (card.getExpiryDate() != null) {
                    ctx.expiry14 = card.getExpiryDate();
                }

                // Build & Pack
                IsoMessage req = (txnType == TxnType.BALANCE_INQUIRY)
                        ? Iso8583Builder.buildBalanceMsg(ctx, card)
                        : Iso8583Builder.buildPurchaseMsg(ctx, card);

                byte[] packed = StandardIsoPacker.pack(req);
                String requestHex = StandardIsoPacker.bytesToHex(packed);

                FileLogger.logPacket(getApplication(), "SEND 0200", packed);
                FileLogger.logString(getApplication(), "SEND DETAIL", StandardIsoPacker.logIsoMessage(req));

                // Save PENDING
                String pan = card.getPan();
                com.example.mysoftpos.domain.model.TransactionRecord record = new com.example.mysoftpos.domain.model.TransactionRecord.Builder()
                        .setTraceNumber(ctx.stan11)
                        .setAmount(amount)
                        .setStatus("PENDING")
                        .setRequestHex(requestHex)
                        .setResponseHex(null)
                        .setTimestamp(System.currentTimeMillis())
                        .setMerchantCode(ctx.merchantId42)
                        .setMerchantName(ctx.merchantNameLocation43)
                        .setTerminalCode(ctx.terminalId41)
                        .setPanMasked(PanUtils.mask(pan))
                        .setBin(PanUtils.getBin(pan))
                        .setLast4(PanUtils.getLast4(pan))
                        .setScheme(PanUtils.detectScheme(pan))
                        .setUsername(username)
                        .setUserId(userId)
                        .setProcessingCode(ctx.processingCode3)
                        .setCurrencyCode(ctx.currency49)
                        .build();
                repository.saveTransaction(record);

                // Mark entity state before network call — ensures handleAutoReversal
                // never operates on an entity with null status (fixes C-1).
                entity.traceNumber = ctx.stan11;
                entity.status = "PENDING";

                // Network Send
                byte[] resp;
                try {
                    // Use injected client
                    resp = isoNetworkClient.sendAndReceive(ctx.ip, ctx.port, packed);
                } catch (SocketTimeoutException e) {
                    FileLogger.logString(getApplication(), "ERROR", "Timeout waiting for response");
                    handleAutoReversal(ctx, card, entity);
                    return;
                } catch (Exception e) {
                    FileLogger.logString(getApplication(), "ERROR", "Network Error: " + e.getMessage());
                    throw e;
                }

                // Process Response
                String responseHex = StandardIsoPacker.bytesToHex(resp);
                FileLogger.logPacket(getApplication(), "RECV 0210", resp);
                repository.updateTransactionResponseHex(ctx.stan11, responseHex);

                IsoMessage respMsg = new StandardIsoPacker().unpack(resp);
                FileLogger.logString(getApplication(), "RECV DETAIL", StandardIsoPacker.logIsoMessage(respMsg));

                String rc = respMsg.getField(IsoField.RESPONSE_CODE_39);
                boolean isApproved = "00".equals(rc);
                entity.status = isApproved ? "APPROVED" : "DECLINED " + rc;
                entity.responseHex = responseHex;

                repository.updateTransactionStatus(ctx.stan11, entity.status);

                // Save denormalized RRN from response (performance optimization)
                if (respMsg.hasField(37)) {
                    repository.updateTransactionRrn(ctx.stan11, respMsg.getField(37).trim());
                }

                // Sync to backend via WorkManager (reliable, survives process death)
                com.example.mysoftpos.data.remote.SyncWorker.enqueueOneTime(getApplication());

                launchUi(() -> {
                    String msg = ResponseCodeHelper.getMessage(rc);
                    if (isApproved) {
                        state.setValue(TransactionState.success(msg, responseHex, requestHex));
                    } else {
                        state.setValue(TransactionState.failed(msg, responseHex, requestHex));
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error", e);
                postError("Error: " + e.getMessage());
            }
        });
    }

    private void postError(String message) {
        launchUi(() -> state.setValue(TransactionState.error(message)));
    }

    private void handleAutoReversal(TransactionContext ctx, CardInputData card, TransactionEntity entity) {
        try {
            Log.d(TAG, "Starting Auto-Reversal...");
            String newTrace = configManager.getAndIncrementTrace();
            IsoMessage rev = Iso8583Builder.buildReversalAdvice(ctx, card, newTrace);
            byte[] packedRev = StandardIsoPacker.pack(rev);

            FileLogger.logPacket(getApplication(), "SEND 0420 (Reversal)", packedRev);
            FileLogger.logString(getApplication(), "SEND 0420 DETAIL", StandardIsoPacker.logIsoMessage(rev));

            entity.status = "TIMEOUT_REVERSAL_INIT";
            repository.updateTransactionStatus(ctx.stan11, entity.status);

            try {
                // Use injected client
                byte[] revResp = isoNetworkClient.sendAndReceive(ctx.ip, ctx.port, packedRev);
                FileLogger.logPacket(getApplication(), "RECV 0430", revResp);

                try {
                    IsoMessage revRespMsg = new StandardIsoPacker().unpack(revResp);
                    FileLogger.logString(getApplication(), "RECV 0430 DETAIL",
                            StandardIsoPacker.logIsoMessage(revRespMsg));
                } catch (Exception e) {
                    FileLogger.logString(getApplication(), "RECV 0430 ERROR",
                            "Failed to unpack: " + e.getMessage());
                }

                entity.status = "TIMEOUT_REVERSED";
                repository.updateTransactionStatus(ctx.stan11, entity.status);
                com.example.mysoftpos.data.remote.SyncWorker.enqueueOneTime(getApplication());
                postError(getApplication().getString(R.string.err_timeout_reversed));

            } catch (SocketTimeoutException e) {
                entity.status = "TIMEOUT_REVERSAL_NO_RSP";
                repository.updateTransactionStatus(ctx.stan11, entity.status);
                postError(getApplication().getString(R.string.err_timeout_generic));
            }

        } catch (Exception e) {
            Log.e(TAG, "Reversal Failed", e);
            entity.status = "TIMEOUT_REVERSAL_FAILED";
            repository.updateTransactionStatus(ctx.stan11, entity.status);
            postError(getApplication().getString(R.string.err_timeout_generic));
        }
    }
}
