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

import com.example.mysoftpos.testsuite.storage.SchemeRepository;
import com.example.mysoftpos.testsuite.model.Scheme;
import com.example.mysoftpos.data.remote.TransactionSyncManager;
import com.example.mysoftpos.data.remote.SyncWorker;

public class TransactionDetailViewModel extends BaseViewModel {

    private static final long VOID_WINDOW_MS = 24L * 60L * 60L * 1000L;

    private final TransactionRepository repository;
    private final ConfigManager configManager;
    private final IsoNetworkClient isoNetworkClient;
    private final SchemeRepository schemeRepository;
    private final MutableLiveData<TransactionState> state = new MutableLiveData<>();

    public TransactionDetailViewModel(Application application, TransactionRepository repository,
            ConfigManager configManager, DispatcherProvider dispatchers,
            IsoNetworkClient isoNetworkClient, SchemeRepository schemeRepository) {
        super(application, dispatchers);
        this.repository = repository;
        this.configManager = configManager;
        this.isoNetworkClient = isoNetworkClient;
        this.schemeRepository = schemeRepository;
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
                    postError(getApplication().getString(R.string.txn_error_not_found));
                    return;
                }

                if (!isWithinVoidWindow(txnDetails.transaction.timestamp)) {
                    postError(getApplication().getString(R.string.txn_void_expired_24h));
                    return;
                }

                String processingCode = txnDetails.transaction.processingCode;
                if (processingCode == null || processingCode.trim().isEmpty()) {
                    try {
                        if (txnDetails.transaction.requestHex != null) {
                            IsoMessage reqForType = new StandardIsoPacker()
                                    .unpack(StandardIsoPacker.hexToBytes(txnDetails.transaction.requestHex));
                            processingCode = reqForType.hasField(3) ? reqForType.getField(3) : null;
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (processingCode == null || !processingCode.startsWith("00")) {
                    postError(getApplication().getString(R.string.void_only_purchase));
                    return;
                }

                if (txnDetails.transaction.requestHex == null
                        || txnDetails.transaction.requestHex.trim().isEmpty()
                        || !isHexPayload(txnDetails.transaction.requestHex)) {
                    postError(getApplication().getString(R.string.txn_error_original_request_missing));
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
                // 3) Else use the current runtime config if it was set dynamically
                String serverIp = configManager.getServerIp();
                int serverPort = configManager.getServerPort();

                if (schemeName != null && !schemeName.isEmpty()) {
                    // Admin void: use scheme's server config (injected repository — M-4)
                    Scheme scheme = schemeRepository.getByName(schemeName);
                    if (scheme != null && scheme.hasConnectionConfig()) {
                        serverIp = scheme.getServerIp();
                        serverPort = scheme.getServerPort();
                    }
                }

                if (serverIp == null || serverIp.trim().isEmpty() || serverPort <= 0) {
                    postError(getApplication().getString(R.string.err_server_not_configured));
                    return;
                }

                ctx.ip = serverIp.trim();
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
                    repository.updateTransactionStatusAndClearSyncSync(txnDetails.transaction.traceNumber, "REVERSED");
                    new TransactionSyncManager(getApplication()).syncUnsynced();
                    SyncWorker.enqueueOneTime(getApplication());
                    if (schemeName != null) {
                        com.example.mysoftpos.utils.logging.FileLogger.logTestSuiteString(getApplication(), "VOID APPROVED", "RC: 00 | Trace: " + txnDetails.transaction.traceNumber);
                    }
                      launchUi(() -> state.setValue(TransactionState.success(
                              getApplication().getString(R.string.txn_void_success),
                            StandardIsoPacker.bytesToHex(responseBytes), revWithNewTrace)));
                } else {
                    com.example.mysoftpos.utils.logging.FileLogger.logString(getApplication(), "VOID DECLINED", "RC: " + rc);
                    if (schemeName != null) {
                        com.example.mysoftpos.utils.logging.FileLogger.logTestSuiteString(getApplication(), "VOID DECLINED", "RC: " + rc);
                    }
                    postError(getApplication().getString(R.string.txn_void_failed_with_rc, rc));
                }

            } catch (java.net.SocketTimeoutException e) {
                android.util.Log.e("TxnDetailVM", "Void timeout", e);
                com.example.mysoftpos.utils.logging.FileLogger.logString(getApplication(), "VOID TIMEOUT", "No response from server");
                if (schemeName != null) {
                    com.example.mysoftpos.utils.logging.FileLogger.logTestSuiteString(getApplication(), "VOID TIMEOUT", "No response from server");
                }
                postError(getApplication().getString(R.string.txn_void_timeout));
            } catch (NumberFormatException e) {
                android.util.Log.e("TxnDetailVM", "Void parse error", e);
                com.example.mysoftpos.utils.logging.FileLogger.logString(getApplication(), "VOID ERROR",
                        "Original request payload is not valid hex/ISO");
                postError(getApplication().getString(R.string.txn_error_original_request_missing));
            } catch (Exception e) {
                android.util.Log.e("TxnDetailVM", "Void error", e);
                com.example.mysoftpos.utils.logging.FileLogger.logString(getApplication(), "VOID ERROR", e.getMessage());
                if (schemeName != null) {
                    com.example.mysoftpos.utils.logging.FileLogger.logTestSuiteString(getApplication(), "VOID ERROR", e.getMessage());
                }
                postError(getApplication().getString(R.string.txn_void_error_with_reason, e.getMessage()));
            }
        });
    }

    /** Convenience: void without scheme (user side — uses user's server config) */
    public void voidTransaction(long transactionId) {
        voidTransaction(transactionId, null);
    }

    private boolean isWithinVoidWindow(long txnTimestamp) {
        long ageMs = System.currentTimeMillis() - txnTimestamp;
        return ageMs >= 0 && ageMs <= VOID_WINDOW_MS;
    }

    private boolean isHexPayload(String value) {
        if (value == null) {
            return false;
        }
        String s = value.trim();
        if (s.isEmpty() || (s.length() % 2 != 0)) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private void postError(String message) {
        launchUi(() -> state.setValue(TransactionState.error(message)));
    }
}
