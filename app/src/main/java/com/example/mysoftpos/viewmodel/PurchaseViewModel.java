package com.example.mysoftpos.viewmodel;

import android.app.Application;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.mysoftpos.data.local.TransactionEntity;
import com.example.mysoftpos.data.remote.IsoNetworkClient;
import com.example.mysoftpos.data.repository.TransactionRepository;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.Iso8583Builder;
import com.example.mysoftpos.iso8583.IsoField;
import com.example.mysoftpos.iso8583.IsoMessage;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.ui.BaseViewModel;
import com.example.mysoftpos.utils.ConfigManager;
import com.example.mysoftpos.utils.DispatcherProvider;
import com.example.mysoftpos.utils.StandardIsoPacker;
import com.example.mysoftpos.utils.TransactionValidator;
import java.net.SocketTimeoutException;

public class PurchaseViewModel extends BaseViewModel {

    private static final String TAG = "PurchaseViewModel";
    private final TransactionRepository repository;
    private final ConfigManager configManager;
    private final MutableLiveData<TransactionState> state = new MutableLiveData<>();

    public PurchaseViewModel(Application application, TransactionRepository repository, ConfigManager configManager,
            DispatcherProvider dispatchers) {
        super(application, dispatchers);
        this.repository = repository;
        this.configManager = configManager;
    }

    public LiveData<TransactionState> getState() {
        return state;
    }

    public void processTransaction(CardInputData card, String amount, TxnType txnType) {
        state.setValue(TransactionState.loading());

        launchIo(() -> {
            TransactionContext ctx = new TransactionContext();
            TransactionEntity entity = new TransactionEntity();

            try {
                // Validation
                boolean isPurchase = (txnType == TxnType.PURCHASE);
                String amtF4 = isPurchase ? TransactionContext.formatAmount12(amount) : "000000000000";

                TransactionValidator.ValidationResult v = TransactionValidator.validate(card, amount, isPurchase);
                if (v != TransactionValidator.ValidationResult.VALID) {
                    postError("Validation Failed: " + v);
                    return;
                }

                // Context
                ctx.txnType = txnType;
                ctx.amount4 = amtF4;
                ctx.stan11 = configManager.getAndIncrementTrace();
                ctx.generateDateTime();
                ctx.rrn37 = TransactionContext.calculateRrn(configManager.getServerId(), ctx.stan11);

                // Set Processing Code based on Txn Type
                if (txnType == TxnType.BALANCE_INQUIRY) {
                    ctx.processingCode3 = configManager.getProcessingCodeBalance();
                } else {
                    ctx.processingCode3 = configManager.getProcessingCodePurchase();
                }

                // Configurable Fields
                ctx.mcc18 = configManager.getMcc18();
                ctx.acquirerId32 = configManager.getAcquirerId32();
                ctx.fwdInst33 = configManager.getForwardingInst33();
                ctx.currency49 = configManager.getCurrencyCode49();
                ctx.terminalId41 = configManager.getTerminalId();
                ctx.merchantId42 = configManager.getMerchantId();
                ctx.merchantNameLocation43 = configManager.getMerchantName();
                ctx.ip = configManager.getServerIp();
                ctx.port = configManager.getServerPort();

                // Build Request
                IsoMessage req = (txnType == TxnType.BALANCE_INQUIRY)
                        ? Iso8583Builder.buildBalanceMsg(ctx, card)
                        : Iso8583Builder.buildPurchaseMsg(ctx, card);

                // Pack
                byte[] packed = StandardIsoPacker.pack(req);
                String requestHex = StandardIsoPacker.bytesToHex(packed);

                // --- 1. LOG REQUEST (Before Network) ---
                com.example.mysoftpos.utils.FileLogger.logPacket(getApplication(), "SEND 0200", packed);
                com.example.mysoftpos.utils.FileLogger.logString(getApplication(), "SEND DETAIL",
                        StandardIsoPacker.logIsoMessage(req));

                // DB Log (Initial Save)
                entity.traceNumber = ctx.stan11;
                entity.amount = amount;
                entity.pan = card.getPan();
                entity.status = "PENDING";
                entity.requestHex = requestHex;
                entity.timestamp = System.currentTimeMillis();
                repository.saveTransaction(entity);

                // Network Send
                IsoNetworkClient client = new IsoNetworkClient(ctx.ip, ctx.port);
                byte[] resp;
                try {
                    resp = client.sendAndReceive(packed);
                } catch (SocketTimeoutException e) {
                    com.example.mysoftpos.utils.FileLogger.logString(getApplication(), "ERROR",
                            "Timeout waiting for response");
                    handleAutoReversal(ctx, card, entity);
                    return;
                } catch (Exception e) {
                    com.example.mysoftpos.utils.FileLogger.logString(getApplication(), "ERROR",
                            "Network Error: " + e.getMessage());
                    throw e;
                }

                // --- 2. LOG RESPONSE (Immediately after Network) ---
                String responseHex = StandardIsoPacker.bytesToHex(resp);
                com.example.mysoftpos.utils.FileLogger.logPacket(getApplication(), "RECV 0210", resp);

                // IMPORTANT: Update DB with Response Hex BEFORE Unpacking (in case unpack
                // fails)
                repository.updateTransactionResponseHex(ctx.stan11, responseHex);

                // 3. Unpack & Check Response
                IsoMessage respMsg = new StandardIsoPacker().unpack(resp);
                // Log Detail AFTER unpacking
                com.example.mysoftpos.utils.FileLogger.logString(getApplication(), "RECV DETAIL",
                        StandardIsoPacker.logIsoMessage(respMsg));

                String rc = respMsg.getField(IsoField.RESPONSE_CODE_39);
                entity.responseHex = responseHex;
                boolean isApproved = "00".equals(rc);
                entity.status = isApproved ? "APPROVED" : "DECLINED " + rc;

                // 4. Update Status (Final Step)
                repository.updateTransactionStatus(ctx.stan11, entity.status);

                launchUi(() -> {
                    String msg = com.example.mysoftpos.utils.ResponseCodeHelper.getMessage(rc);
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

            com.example.mysoftpos.utils.FileLogger.logPacket(getApplication(), "SEND 0420 (Reversal)", packedRev);
            entity.status = "TIMEOUT_REVERSAL_INIT";
            repository.updateTransactionStatus(ctx.stan11, entity.status);

            IsoNetworkClient revClient = new IsoNetworkClient(ctx.ip, ctx.port);
            try {
                byte[] revResp = revClient.sendAndReceive(packedRev);
                com.example.mysoftpos.utils.FileLogger.logPacket(getApplication(), "RECV 0430", revResp);
                entity.status = "TIMEOUT_REVERSED";
                repository.updateTransactionStatus(ctx.stan11, entity.status);
                postError("Giao dịch lỗi Time Out (Đã gửi hủy)");

            } catch (SocketTimeoutException e) {
                entity.status = "TIMEOUT_REVERSAL_NO_RSP";
                repository.updateTransactionStatus(ctx.stan11, entity.status);
                postError("Giao dịch lỗi Time Out");
            }

        } catch (Exception e) {
            Log.e(TAG, "Reversal Failed", e);
            entity.status = "TIMEOUT_REVERSAL_FAILED";
            repository.updateTransactionStatus(ctx.stan11, entity.status);
            postError("Giao dịch lỗi Time Out");
        }
    }
}
