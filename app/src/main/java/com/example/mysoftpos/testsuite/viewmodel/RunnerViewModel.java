package com.example.mysoftpos.testsuite.viewmodel;

import com.example.mysoftpos.utils.config.ConfigManager;
import com.example.mysoftpos.data.local.entity.TransactionEntity;
import com.example.mysoftpos.utils.logging.FileLogger;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.builder.Iso8583Builder;
import com.example.mysoftpos.iso8583.message.IsoMessage;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.iso8583.util.StandardIsoPacker;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RunnerViewModel extends AndroidViewModel {

    private final MutableLiveData<String> logMessage = new MutableLiveData<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final com.example.mysoftpos.data.local.DatabaseManager dbManager;

    public RunnerViewModel(Application application) {
        super(application);
        this.dbManager = new com.example.mysoftpos.data.local.DatabaseManager(application);
    }

    public LiveData<String> getLogMessage() {
        return logMessage;
    }

    public void runTransaction(String de22, String track2Data, String de55Data, String panData, String expiryData,
            String pinBlockData) {
        executor.execute(() -> {
            try {
                // 1. Prepare Context with ConfigManager
                com.example.mysoftpos.utils.config.ConfigManager config = com.example.mysoftpos.utils.config.ConfigManager
                        .getInstance(getApplication());

                TransactionContext ctx = new TransactionContext();
                // Fixed Amount per User Request: 3456789 -> 000345678900
                ctx.amount4 = TransactionContext.formatAmount12("3456789");
                ctx.stan11 = config.getAndIncrementTrace();
                ctx.generateDateTime();
                ctx.rrn37 = TransactionContext.calculateRrn(config.getServerId(), ctx.stan11);

                ctx.processingCode3 = config.getProcessingCodePurchase();
                ctx.posCondition25 = config.getPosConditionCode();
                ctx.mcc18 = config.getMcc18();
                ctx.acquirerId32 = config.getAcquirerId32();
                ctx.terminalId41 = config.getTerminalId();
                ctx.merchantId42 = config.getMerchantId();

                ctx.merchantNameLocation43 = config.getMerchantName();
                ctx.currency49 = config.getCurrencyCode49();
                // FIX: Set IP/Port for Network Client
                ctx.ip = config.getServerIp();
                ctx.port = config.getServerPort();
                // Pass PIN block to Context if needed for encryption logic??
                // Actually Iso8583Builder checks ctx.pinBlock52, NOT CardInputData.pinBlock
                // directly in some branches
                // But let's check Iso8583Builder: "if (ctx.encryptPin && ctx.pinBlock52 !=
                // null)"
                // RunnerViewModel sets context.
                // We should set ctx.pinBlock52 from passed pinBlockData
                ctx.pinBlock52 = pinBlockData;
                // Enable pin if block is present
                ctx.encryptPin = (pinBlockData != null);

                // 2. Prepare Card Data based on Inputs
                String pan = panData;
                String expiry = expiryData;

                // Extract PAN/Expiry from Track 2 if not provided
                if ((pan == null || pan.isEmpty()) && track2Data != null) {
                    // Support both D and = separators
                    String[] parts = track2Data.split("[=D]");
                    if (parts.length > 0)
                        pan = parts[0];
                    if (parts.length > 1 && parts[1].length() >= 4) {
                        expiry = parts[1].substring(0, 4);
                    }
                }

                // Fallbacks if absolutely nothing provided (Safety)
                if (pan == null)
                    pan = config.getMockPan();

                CardInputData card = new CardInputData(pan, expiry, de22, track2Data);
                // card.setRawIccData(de55Data); // Removed
                card.setPinBlock(pinBlockData);

                // 3. Build Message
                logMessage.postValue("Building 0200...");
                IsoMessage msg = Iso8583Builder.buildPurchaseMsg(ctx, card);

                // Detailed Log
                StringBuilder sb = new StringBuilder();
                sb.append("\n--- ISO MESSAGE DETAILS ---\n");
                sb.append("MTI: ").append(msg.getMti()).append("\n");

                // Sort fields for readable output
                java.util.TreeSet<Integer> sortedFields = new java.util.TreeSet<>(msg.getFieldNumbers());
                for (Integer field : sortedFields) {
                    String val = msg.getField(field);
                    // Mask sensitive data if needed (optional for Simulator)
                    if (field == 2)
                        val = maskPan(val);
                    if (field == 35)
                        val = maskTrack2(val);

                    sb.append(String.format(Locale.ROOT, "DE %03d: %s\n", field, val));
                }
                sb.append("---------------------------\n");
                logMessage.postValue(sb.toString());

                // 4. Pack
                byte[] packed = StandardIsoPacker.pack(msg);
                String sendHex = StandardIsoPacker.bytesToHex(packed);
                logMessage.postValue("\nPacked Hex (" + packed.length + " bytes):\n" + sendHex);

                // --- LOG TO FILE: TEST SUITE ---
                com.example.mysoftpos.utils.logging.FileLogger.logTestSuitePacket(getApplication(), "SEND", packed);
                com.example.mysoftpos.utils.logging.FileLogger.logTestSuiteString(getApplication(), "SEND DETAIL",
                        StandardIsoPacker.logIsoMessage(msg));

                // 5. Send (Real Network)
                logMessage.postValue("\nSending to Host (" + ctx.ip + ":" + ctx.port + ")...");
                com.example.mysoftpos.data.remote.IsoNetworkClient client = new com.example.mysoftpos.data.remote.IsoNetworkClient(
                        ctx.ip, ctx.port);

                byte[] responseBytes = client.sendAndReceive(packed);
                logMessage.postValue("Received " + responseBytes.length + " bytes.");
                String recvHex = StandardIsoPacker.bytesToHex(responseBytes);

                // --- LOG TO FILE: TEST SUITE (RECV) ---
                com.example.mysoftpos.utils.logging.FileLogger.logTestSuitePacket(getApplication(), "RECV",
                        responseBytes);

                // 6. Unpack Response
                IsoMessage respMsg = new StandardIsoPacker().unpack(responseBytes);

                // --- LOG TO FILE: TEST SUITE (RECV DETAIL) ---
                com.example.mysoftpos.utils.logging.FileLogger.logTestSuiteString(getApplication(), "RECV DETAIL",
                        StandardIsoPacker.logIsoMessage(respMsg));

                // Log Response Details
                StringBuilder sbResp = new StringBuilder();
                sbResp.append("\n--- RESPONSE MESSAGE DETAILS ---\n");
                sbResp.append("MTI: ").append(respMsg.getMti()).append("\n");

                java.util.TreeSet<Integer> respFields = new java.util.TreeSet<>(respMsg.getFieldNumbers());
                for (Integer field : respFields) {
                    String val = respMsg.getField(field);
                    sbResp.append(String.format(Locale.ROOT, "DE %03d: %s\n", field, val));
                }
                sbResp.append("----------------------------\n");
                logMessage.postValue(sbResp.toString());

                logMessage.postValue("\nResponse Hex:\n" + StandardIsoPacker.bytesToHex(responseBytes));

                // Summary result
                // Summary result
                String rc = respMsg.getField(39);
                String reason = getRcDescription(rc);
                String statusStr = "UNKNOWN";

                if ("00".equals(rc)) {
                    logMessage.postValue("\n*** STATUS: PASS ***");
                    logMessage.postValue("RC: " + rc + " (" + reason + ")");
                    statusStr = "APPROVED";
                } else {
                    logMessage.postValue("\n*** STATUS: FAIL ***");
                    logMessage.postValue("RC: " + rc + " - Reason: " + reason);
                    statusStr = "DECLINED";
                }

                // --- SAVE TO DATABASE ---
                saveTransactionToDb(ctx, card, statusStr, sendHex, StandardIsoPacker.bytesToHex(responseBytes));

            } catch (java.net.SocketTimeoutException e) {
                logMessage.postValue("\n*** STATUS: FAIL ***");
                logMessage.postValue("Error: Timeout waiting for response.");
                // Update: Save failed transaction (Response Hex null)
                // We need access to ctx/card here. Refactoring required if not accessible.
                // For now, logging error. Ideally should save "TIMEOUT" status.
            } catch (Exception e) {
                logMessage.postValue("\n*** STATUS: FAIL ***");
                logMessage.postValue("Error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void saveTransactionToDb(TransactionContext ctx, CardInputData card, String status, String reqHex,
            String respHex) {
        try {
            com.example.mysoftpos.data.local.entity.TransactionEntity entity = new com.example.mysoftpos.data.local.entity.TransactionEntity();
            entity.traceNumber = ctx.stan11;
            entity.amount = ctx.amount4;
            entity.pan = card.getPan(); // Unmasked for internal DB? Or Masked? Using Utils to mask.
            // Let's mask for privacy in DB too if preferred, or keep clear.
            // User can delete DB. Let's keep clear for history detail, or mask.
            // Standard approach: Masked.
            entity.pan = maskPan(card.getPan());
            entity.status = status;
            entity.requestHex = reqHex;
            entity.responseHex = respHex;
            entity.timestamp = System.currentTimeMillis();

            dbManager.insertTransaction(entity);
            logMessage.postValue("Transaction saved to History (Trace: " + ctx.stan11 + ")");
        } catch (Exception e) {
            logMessage.postValue("Error saving to DB: " + e.getMessage());
        }
    }

    private String getRcDescription(String rc) {
        if (rc == null)
            return "Unknown";
        switch (rc) {
            case "00":
                return "Approved";
            case "01":
                return "Refer to Card Issuer";
            case "03":
                return "Invalid Merchant";
            case "04":
                return "Pick-up Card";
            case "05":
                return "Do Not Honor";
            case "12":
                return "Invalid Transaction";
            case "13":
                return "Invalid Amount";
            case "14":
                return "Invalid Card Number";
            case "30":
                return "Format Error";
            case "41":
                return "Lost Card";
            case "43":
                return "Stolen Card";
            case "51":
                return "Insufficient Funds";
            case "54":
                return "Expired Card";
            case "55":
                return "Incorrect PIN";
            case "57":
                return "Txn Not Permitted";
            case "58":
                return "Txn Not Permitted on Terminal";
            case "61":
                return "Exceeds Withdrawal Limit";
            case "63":
                return "Security Violation";
            case "68":
                return "Response Received Too Late";
            case "91":
                return "Issuer Sys Error";
            case "94":
                return "Duplicate Transaction";
            case "96":
                return "System Error";
            default:
                return "Declined / Unknown";
        }
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10)
            return pan;
        return pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4);
    }

    private String maskTrack2(String track2) {
        if (track2 == null || track2.length() < 10)
            return track2;
        return track2.substring(0, 6) + "......" + track2.substring(track2.length() - 4);
    }
}
