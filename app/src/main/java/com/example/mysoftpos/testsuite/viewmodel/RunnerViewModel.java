package com.example.mysoftpos.testsuite.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.Iso8583Builder;
import com.example.mysoftpos.iso8583.IsoMessage;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.utils.StandardIsoPacker;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RunnerViewModel extends AndroidViewModel {

    private final MutableLiveData<String> logMessage = new MutableLiveData<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public RunnerViewModel(Application application) {
        super(application);
    }

    public LiveData<String> getLogMessage() {
        return logMessage;
    }

    public void runTransaction(String de22, String track2Data, String de55Data, String panData, String expiryData,
            String pinBlockData) {
        executor.execute(() -> {
            try {
                // 1. Prepare Context with ConfigManager
                com.example.mysoftpos.utils.ConfigManager config = com.example.mysoftpos.utils.ConfigManager
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
                card.setRawIccData(de55Data);
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

                    sb.append(String.format("DE %03d: %s\n", field, val));
                }
                sb.append("---------------------------\n");
                logMessage.postValue(sb.toString());

                // 4. Pack
                byte[] packed = StandardIsoPacker.pack(msg);
                logMessage.postValue(
                        "\nPacked Hex (" + packed.length + " bytes):\n" + StandardIsoPacker.bytesToHex(packed));

                // 5. Send (Real Network)
                logMessage.postValue("\nSending to Host (" + ctx.ip + ":" + ctx.port + ")...");
                com.example.mysoftpos.data.remote.IsoNetworkClient client = new com.example.mysoftpos.data.remote.IsoNetworkClient(
                        ctx.ip, ctx.port);

                byte[] responseBytes = client.sendAndReceive(packed);
                logMessage.postValue("Received " + responseBytes.length + " bytes.");

                // 6. Unpack Response
                IsoMessage respMsg = new StandardIsoPacker().unpack(responseBytes);

                // Log Response Details
                StringBuilder sbResp = new StringBuilder();
                sbResp.append("\n--- RESPONSE MESSAGE DETAILS ---\n");
                sbResp.append("MTI: ").append(respMsg.getMti()).append("\n");

                java.util.TreeSet<Integer> respFields = new java.util.TreeSet<>(respMsg.getFieldNumbers());
                for (Integer field : respFields) {
                    String val = respMsg.getField(field);
                    sbResp.append(String.format("DE %03d: %s\n", field, val));
                }
                sbResp.append("----------------------------\n");
                logMessage.postValue(sbResp.toString());

                logMessage.postValue("\nResponse Hex:\n" + StandardIsoPacker.bytesToHex(responseBytes));

                // Summary result
                String rc = respMsg.getField(39);
                if ("00".equals(rc)) {
                    logMessage.postValue("\nRESULT: APPROVED (00)");
                } else {
                    logMessage.postValue("\nRESULT: DECLINED (RC=" + rc + ")");
                }

            } catch (java.net.SocketTimeoutException e) {
                logMessage.postValue("\nError: Timeout waiting for response.");
            } catch (Exception e) {
                logMessage.postValue("\nError: " + e.getMessage());
                e.printStackTrace();
            }
        });
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
