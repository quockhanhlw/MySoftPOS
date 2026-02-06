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

    public void runTransaction(String de22, String track2Data, String panData, String expiryData,
            String pinBlockData, String txnType) {
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

                if ("BALANCE".equals(txnType)) {
                    ctx.processingCode3 = "300000";
                    // Use config prompt if available? Or Hardcode as per user request
                    // User said: "nếu là Balance Inquiry thì DE 3 là 300000"
                } else {
                    ctx.processingCode3 = "000000";
                }
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
                // Pass PIN block to Context
                // Start Update: Calculate Clear PIN Block (ISO 9564 Format 0) if Raw PIN is
                // present
                if (pinBlockData != null && !pinBlockData.isEmpty()) {
                    // We need the PAN to calculate the block.
                    // Note: panData input might be null if Track2 is used, so use the derived 'pan'
                    // later?
                    // actually 'pan' variable is defined BELOW this block (line 80).
                    // Let's resolve PAN first or move this logic down.
                    // Moving logic down to after card data preparation is cleaner.
                    ctx.encryptPin = true;
                } else {
                    ctx.encryptPin = false;
                    ctx.pinBlock52 = null;
                }

                // 2. Prepare Card Data based on Inputs
                String pan = panData;
                String expiry = expiryData;

                // Extract PAN/Expiry from Track 2 if not provided
                if ((pan == null || pan.isEmpty()) && track2Data != null) {
                    // FIX: Set DE 25 to 80 for Fallback Modes (79, 80 only) - User confirmed 902 is
                    // Normal.
                    if (de22.startsWith("80") || de22.startsWith("79")) {
                        ctx.posCondition25 = "80";
                    }
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

                // Start Update: Calculate PIN Block logic moved here to access resolved 'pan'
                if (pinBlockData != null && !pinBlockData.isEmpty() && pan != null) {
                    try {
                        String clearBlock = com.example.mysoftpos.iso8583.util.PinBlockGenerator
                                .calculateClearBlock(pinBlockData, pan);
                        ctx.pinBlock52 = clearBlock;
                        card.setPinBlock(clearBlock); // Update Card model too
                        logMessage.postValue("Generated PIN Block (Format 0): " + clearBlock);
                    } catch (Exception e) {
                        logMessage.postValue("Error generating PIN Block: " + e.getMessage());
                        ctx.pinBlock52 = null;
                    }
                } else {
                    card.setPinBlock(null);
                    ctx.pinBlock52 = null;
                }

                // 3. Build Message
                logMessage
                        .postValue("Building " + ("BALANCE".equals(txnType) ? "Balance Inquiry" : "Purchase") + "...");

                IsoMessage msg;
                if ("BALANCE".equals(txnType)) {
                    // Start Update: Force Amount to 0 for Context consistency (though builder does
                    // it too)
                    ctx.amount4 = "000000000000";
                    msg = Iso8583Builder.buildBalanceMsg(ctx, card);
                } else {
                    msg = Iso8583Builder.buildPurchaseMsg(ctx, card);
                }

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
            String pan = maskPan(card.getPan());
            String rawPan = card.getPan(); // internal usage for bin/last4 extraction

            // Derive Data
            String bin = (rawPan != null && rawPan.length() >= 6) ? rawPan.substring(0, 6) : "";
            String last4 = (rawPan != null && rawPan.length() >= 4) ? rawPan.substring(rawPan.length() - 4) : "";
            String scheme = "TestCard";
            if (rawPan != null) {
                if (rawPan.startsWith("4"))
                    scheme = "Visa";
                else if (rawPan.startsWith("5"))
                    scheme = "Mastercard";
                else if (rawPan.startsWith("9704"))
                    scheme = "Napas";
            }

            dbManager.saveTransaction(
                    ctx.stan11,
                    ctx.amount4,
                    status,
                    reqHex,
                    respHex,
                    System.currentTimeMillis(),
                    ctx.merchantId42,
                    ctx.merchantNameLocation43,
                    ctx.terminalId41,
                    pan, // Masked PAN
                    bin,
                    last4,
                    scheme,
                    "TEST_SUITE_USER");

            logMessage.postValue("Transaction saved to History (Trace: " + ctx.stan11 + ")");
        } catch (Exception e) {
            logMessage.postValue("Error saving to DB: " + e.getMessage());
            e.printStackTrace();
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
