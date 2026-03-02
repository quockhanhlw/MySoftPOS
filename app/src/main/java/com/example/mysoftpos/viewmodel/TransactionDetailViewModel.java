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

    /** Void with scheme's server config (admin from scheme history) */
    public void voidTransaction(long transactionId, String schemeName) {
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
                ctx.txnType = TxnType.PURCHASE;
                if ("300000".equals(origMsg.getField(3))) {
                    ctx.txnType = TxnType.BALANCE_INQUIRY;
                }

                ctx.amount4 = origMsg.getField(4);
                ctx.stan11 = origMsg.getField(11);
                ctx.transmissionDt7 = origMsg.getField(7);
                ctx.localTime12 = origMsg.getField(12);
                ctx.localDate13 = origMsg.getField(13);
                ctx.expiry14 = origMsg.getField(14);
                ctx.mcc18 = origMsg.getField(18);
                ctx.country19 = origMsg.getField(19);
                ctx.posEntryMode22 = origMsg.getField(22);
                ctx.posCondition25 = origMsg.getField(25);
                ctx.acquirerId32 = origMsg.getField(32);
                ctx.track2_35 = origMsg.getField(35);
                ctx.rrn37 = origMsg.getField(37);
                ctx.terminalId41 = origMsg.getField(41);
                ctx.merchantId42 = origMsg.getField(42);
                ctx.merchantNameLocation43 = origMsg.getField(43);
                ctx.currency49 = origMsg.getField(49);

                // IP/Port resolution:
                // 1) If schemeName provided (admin void from scheme history) → use scheme's IP/port
                // 2) Else if user has server config (user void) → use user's IP/port
                // 3) Fallback to current configManager
                String serverIp = configManager.getServerIp();
                int serverPort = configManager.getServerPort();

                if (schemeName != null && !schemeName.isEmpty()) {
                    // Admin void: use scheme's server config
                    com.example.mysoftpos.testsuite.storage.SchemeRepository schemeRepo =
                            new com.example.mysoftpos.testsuite.storage.SchemeRepository(getApplication());
                    com.example.mysoftpos.testsuite.model.Scheme scheme = schemeRepo.getByName(schemeName);
                    if (scheme != null && scheme.hasConnectionConfig()) {
                        serverIp = scheme.getServerIp();
                        serverPort = scheme.getServerPort();
                    }
                } else if (txnDetails.user != null
                        && txnDetails.user.serverIp != null && !txnDetails.user.serverIp.isEmpty()
                        && txnDetails.user.serverPort > 0) {
                    // User void: use user's server config from admin management
                    serverIp = txnDetails.user.serverIp;
                    serverPort = txnDetails.user.serverPort;
                }
                ctx.ip = serverIp;
                ctx.port = serverPort;

                // Reconstruct Card Data
                String pan = origMsg.getField(2);
                String track2 = origMsg.getField(35);
                CardInputData card = new CardInputData(pan, null, null, track2);

                // DE 55: ICC data for chip reversal
                if (origMsg.hasField(55)) {
                    ctx.reversalIccData55 = origMsg.getField(55);
                }

                // 3. Generate NEW Trace for Reversal
                String newTrace = configManager.getAndIncrementTrace();

                // 4. Build Reversal
                IsoMessage revMsg = Iso8583Builder.buildReversalAdvice(ctx, card, newTrace);

                // 5. Pack
                byte[] packed = StandardIsoPacker.pack(revMsg);
                String revWithNewTrace = StandardIsoPacker.bytesToHex(packed);

                // Log SEND 0420
                com.example.mysoftpos.utils.logging.FileLogger.logPacket(getApplication(), "SEND 0420 (VOID)", packed);
                com.example.mysoftpos.utils.logging.FileLogger.logString(getApplication(), "SEND 0420 DETAIL",
                        StandardIsoPacker.logIsoMessage(revMsg));
                com.example.mysoftpos.utils.logging.FileLogger.logString(getApplication(), "VOID TARGET",
                        "Server: " + ctx.ip + ":" + ctx.port + " | Scheme: " + (schemeName != null ? schemeName : "N/A")
                        + " | Original Trace: " + ctx.stan11);

                // Also log to test_suite_log when void is from admin (scheme history)
                if (schemeName != null) {
                    com.example.mysoftpos.utils.logging.FileLogger.logTestSuitePacket(getApplication(), "SEND 0420 (VOID)", packed);
                    com.example.mysoftpos.utils.logging.FileLogger.logTestSuiteString(getApplication(), "SEND 0420 DETAIL",
                            StandardIsoPacker.logIsoMessage(revMsg));
                    com.example.mysoftpos.utils.logging.FileLogger.logTestSuiteString(getApplication(), "VOID TARGET",
                            "Server: " + ctx.ip + ":" + ctx.port + " | Scheme: " + schemeName
                            + " | Original Trace: " + ctx.stan11);
                }

                // 6. Send
                byte[] responseBytes = isoNetworkClient.sendAndReceive(ctx.ip, ctx.port, packed);

                // Log RECV 0430
                com.example.mysoftpos.utils.logging.FileLogger.logPacket(getApplication(), "RECV 0430 (VOID)", responseBytes);
                IsoMessage respMsg = new StandardIsoPacker().unpack(responseBytes);
                com.example.mysoftpos.utils.logging.FileLogger.logString(getApplication(), "RECV 0430 DETAIL",
                        StandardIsoPacker.logIsoMessage(respMsg));

                // Also log response to test_suite_log when from admin
                if (schemeName != null) {
                    com.example.mysoftpos.utils.logging.FileLogger.logTestSuitePacket(getApplication(), "RECV 0430 (VOID)", responseBytes);
                    com.example.mysoftpos.utils.logging.FileLogger.logTestSuiteString(getApplication(), "RECV 0430 DETAIL",
                            StandardIsoPacker.logIsoMessage(respMsg));
                }

                // 7. Handle Response
                String rc = respMsg.getField(IsoField.RESPONSE_CODE_39);

                if ("00".equals(rc)) {
                    repository.updateTransactionStatus(txnDetails.transaction.traceNumber, "REVERSED");
                     if (schemeName != null) {
                        com.example.mysoftpos.utils.logging.FileLogger.logTestSuiteString(getApplication(), "VOID APPROVED", "RC: 00 | Trace: " + txnDetails.transaction.traceNumber);
                    }
                    launchUi(() -> state.setValue(TransactionState.success("Transaction Voided Successfully",
                            StandardIsoPacker.bytesToHex(responseBytes), revWithNewTrace)));
                } else {
                    com.example.mysoftpos.utils.logging.FileLogger.logString(getApplication(), "VOID DECLINED", "RC: " + rc);
                    if (schemeName != null) {
                        com.example.mysoftpos.utils.logging.FileLogger.logTestSuiteString(getApplication(), "VOID DECLINED", "RC: " + rc);
                    }
                    postError("Void Failed: RC " + rc);
                }

            } catch (java.net.SocketTimeoutException e) {
                android.util.Log.e("TxnDetailVM", "Void timeout", e);
                com.example.mysoftpos.utils.logging.FileLogger.logString(getApplication(), "VOID TIMEOUT", "No response from server");
                if (schemeName != null) {
                    com.example.mysoftpos.utils.logging.FileLogger.logTestSuiteString(getApplication(), "VOID TIMEOUT", "No response from server");
                }
                postError("Void Timeout: No response from server");
            } catch (Exception e) {
                android.util.Log.e("TxnDetailVM", "Void error", e);
                com.example.mysoftpos.utils.logging.FileLogger.logString(getApplication(), "VOID ERROR", e.getMessage());
                if (schemeName != null) {
                    com.example.mysoftpos.utils.logging.FileLogger.logTestSuiteString(getApplication(), "VOID ERROR", e.getMessage());
                }
                postError("Void Error: " + e.getMessage());
            }
        });
    }

    /** Convenience: void without scheme (user side — uses user's server config) */
    public void voidTransaction(long transactionId) {
        voidTransaction(transactionId, null);
    }

    private void postError(String message) {
        launchUi(() -> state.setValue(TransactionState.error(message)));
    }
}
