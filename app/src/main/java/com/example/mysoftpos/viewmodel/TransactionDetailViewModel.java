package com.example.mysoftpos.viewmodel;

import android.app.Application;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mysoftpos.R;
import com.example.mysoftpos.data.local.entity.TransactionWithDetails;
import com.example.mysoftpos.data.repository.TransactionRepository;
import com.example.mysoftpos.ui.base.BaseViewModel;
import com.example.mysoftpos.utils.config.ConfigManager;
import com.example.mysoftpos.utils.threading.DispatcherProvider;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.iso8583.builder.Iso8583Builder;
import com.example.mysoftpos.iso8583.message.IsoMessage;
import com.example.mysoftpos.iso8583.util.StandardIsoPacker;
import com.example.mysoftpos.data.remote.IsoNetworkClient;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.iso8583.spec.IsoField;

public class TransactionDetailViewModel extends BaseViewModel {

    private final TransactionRepository repository;
    private final ConfigManager configManager;
    private final IsoNetworkClient isoNetworkClient;
    private final MutableLiveData<TransactionState> state = new MutableLiveData<>();

    public TransactionDetailViewModel(Application application, TransactionRepository repository,
            ConfigManager configManager, DispatcherProvider dispatchers, IsoNetworkClient isoNetworkClient) {
        super(application, dispatchers);
        this.repository = repository;
        this.configManager = configManager;
        this.isoNetworkClient = isoNetworkClient;
    }

    public LiveData<TransactionState> getState() {
        return state;
    }

    public LiveData<TransactionWithDetails> getTransaction(long id) {
        return repository.getTransactionWithDetailsById(id);
    }

    public void voidTransaction(long transactionId) {
        state.setValue(TransactionState.loading());

        launchIo(() -> {
            try {
                // 1. Fetch Transaction Sync
                TransactionWithDetails txnDetails = repository.getTransactionWithDetailsByIdSync(transactionId);

                if (txnDetails == null || txnDetails.transaction == null) {
                    postError("Transaction not found");
                    return;
                }

                if (txnDetails.transaction.requestHex == null) {
                    postError("Original request data missing");
                    return;
                }

                // 2. Unpack Original Request to Reconstruct Context
                IsoMessage origMsg = new StandardIsoPacker()
                        .unpack(StandardIsoPacker.hexToBytes(txnDetails.transaction.requestHex));

                TransactionContext ctx = new TransactionContext();
                ctx.txnType = TxnType.PURCHASE; // Assumption: History filters for Purchase
                if ("300000".equals(origMsg.getField(3))) {
                    ctx.txnType = TxnType.BALANCE_INQUIRY;
                }

                ctx.amount4 = origMsg.getField(4);
                ctx.stan11 = origMsg.getField(11); // Original Trace
                ctx.transmissionDt7 = origMsg.getField(7);
                ctx.localTime12 = origMsg.getField(12);
                ctx.localDate13 = origMsg.getField(13);
                ctx.mcc18 = origMsg.getField(18);
                ctx.rrn37 = origMsg.getField(37);
                ctx.terminalId41 = origMsg.getField(41);
                ctx.merchantId42 = origMsg.getField(42);
                ctx.merchantNameLocation43 = origMsg.getField(43);
                ctx.currency49 = origMsg.getField(49);
                ctx.acquirerId32 = origMsg.getField(32);

                // IP/Port from Config
                ctx.ip = configManager.getServerIp();
                ctx.port = configManager.getServerPort();

                // Reconstruct Card Data
                String pan = origMsg.getField(2);
                String track2 = origMsg.getField(35);
                CardInputData card = new CardInputData(pan, null, null, track2);

                // 3. Generate NEW Trace for Reversal
                String newTrace = configManager.getAndIncrementTrace();

                // 4. Build Reversal
                IsoMessage revMsg = Iso8583Builder.buildReversalAdvice(ctx, card, newTrace);

                // 5. Pack
                byte[] packed = StandardIsoPacker.pack(revMsg);
                String revWithNewTrace = StandardIsoPacker.bytesToHex(packed);

                // Log
                com.example.mysoftpos.utils.logging.FileLogger.logPacket(getApplication(), "SEND 0420 (VOID)", packed);

                // 6. Send
                // Use injected client
                byte[] responseBytes = isoNetworkClient.sendAndReceive(ctx.ip, ctx.port, packed);

                // 7. Handle Response
                com.example.mysoftpos.utils.logging.FileLogger.logPacket(getApplication(), "RECV 0430 (VOID)",
                        responseBytes);
                IsoMessage respMsg = new StandardIsoPacker().unpack(responseBytes);

                String rc = respMsg.getField(39);

                if ("00".equals(rc)) {
                    // Update DB
                    repository.updateTransactionStatus(txnDetails.transaction.traceNumber, "REVERSED");

                    launchUi(() -> state.setValue(TransactionState.success("Transaction Voided Successfully",
                            StandardIsoPacker.bytesToHex(responseBytes), revWithNewTrace)));
                } else {
                    postError("Void Failed: RC " + rc);
                }

            } catch (Exception e) {
                android.util.Log.e("TxnDetailVM", "Void error", e);
                postError("Void Error: " + e.getMessage());
            }
        });
    }

    private void postError(String message) {
        launchUi(() -> state.setValue(TransactionState.error(message)));
    }
}
