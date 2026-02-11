package com.example.mysoftpos.domain.service;

import android.content.Context;
import com.example.mysoftpos.data.remote.IsoNetworkClient;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.TransactionContext;
import com.example.mysoftpos.iso8583.builder.Iso8583Builder;
import com.example.mysoftpos.iso8583.message.IsoMessage;
import com.example.mysoftpos.iso8583.util.PinBlockGenerator;
import com.example.mysoftpos.iso8583.util.StandardIsoPacker;
import com.example.mysoftpos.utils.PanUtils;
import com.example.mysoftpos.utils.config.ConfigManager;
import com.example.mysoftpos.utils.logging.FileLogger;

/**
 * Centralized transaction execution: build ISO → pack → send → receive → unpack
 * → result.
 *
 * Replaces duplicated logic in RunnerViewModel, MultiThreadRunnerActivity,
 * PurchaseViewModel.
 * Does NOT handle UI state, DB saves, or auto-reversal — those responsibilities
 * stay with callers.
 */
public class TransactionExecutor {

    private final IsoNetworkClient isoNetworkClient;

    public TransactionExecutor(IsoNetworkClient isoNetworkClient) {
        this.isoNetworkClient = isoNetworkClient;
    }

    public interface LogCallback {
        void log(String message);
    }

    private static final LogCallback NOOP = msg -> {
    };

    // Kept static as it's a pure utility function
    public static TransactionContext buildContext(Context appContext, String txnType) {
        ConfigManager config = ConfigManager.getInstance(appContext);

        TransactionContext ctx = new TransactionContext();
        ctx.stan11 = config.getAndIncrementTrace();
        ctx.generateDateTime();
        ctx.rrn37 = TransactionContext.calculateRrn(config.getServerId(), ctx.stan11);

        if ("BALANCE".equals(txnType)) {
            ctx.processingCode3 = "300000";
            ctx.amount4 = "000000000000";
        } else {
            ctx.processingCode3 = "000000";
            ctx.amount4 = TransactionContext.formatAmount12("12345");
        }

        ctx.posCondition25 = config.getPosConditionCode();
        ctx.mcc18 = config.getMcc18();
        ctx.acquirerId32 = config.getAcquirerId32();
        ctx.terminalId41 = config.getTerminalId();
        ctx.merchantId42 = config.getMerchantId();
        ctx.merchantNameLocation43 = config.getMerchantName();
        ctx.currency49 = config.getCurrencyCode49();
        ctx.ip = config.getServerIp();
        ctx.port = config.getServerPort();

        return ctx;
    }

    // Kept static as it's a pure utility function
    public static CardInputData prepareCard(Context appContext, String de22, String pan,
            String expiry, String track2, String pinData,
            TransactionContext ctx, LogCallback logger) {
        if (logger == null)
            logger = NOOP;

        // Extract PAN/Expiry from Track2 if not provided
        if ((pan == null || pan.isEmpty()) && track2 != null) {
            String[] parts = track2.split("[=D]");
            if (parts.length > 0)
                pan = parts[0];
            if (parts.length > 1 && parts[1].length() >= 4) {
                expiry = parts[1].substring(0, 4);
            }
        }

        // Fallback
        if (pan == null) {
            pan = ConfigManager.getInstance(appContext).getMockPan();
        }

        CardInputData card = new CardInputData(pan, expiry, de22, track2);

        // PIN Block calculation
        if (pinData != null && !pinData.isEmpty() && pan != null) {
            try {
                String clearBlock = PinBlockGenerator.calculateClearBlock(pinData, pan);
                ctx.pinBlock52 = clearBlock;
                ctx.encryptPin = true;
                card.setPinBlock(clearBlock);
                logger.log("PIN Block: " + clearBlock);
            } catch (Exception e) {
                logger.log("PIN Error: " + e.getMessage());
                ctx.pinBlock52 = null;
            }
        }

        return card;
    }

    /**
     * Execute transaction: build ISO → pack → send → receive → unpack → return
     * result.
     *
     * @param appContext Application context for logging
     * @param ctx        Prepared TransactionContext
     * @param card       Prepared CardInputData
     * @param txnType    "BALANCE" or "PURCHASE"
     * @param logger     Callback for step-by-step logging (nullable)
     * @param logTag     Tag prefix for file logging (e.g. "SEND", "SEND [T1 022]")
     * @return TransactionResult with RC, hex data, approval status
     * @throws Exception on network/parse errors
     */
    public TransactionResult execute(Context appContext, TransactionContext ctx,
            CardInputData card, String txnType,
            LogCallback logger, String logTag) throws Exception {
        if (logger == null)
            logger = NOOP;
        if (logTag == null)
            logTag = "";

        // 1. Build Message
        IsoMessage msg;
        if ("BALANCE".equals(txnType)) {
            msg = Iso8583Builder.buildBalanceMsg(ctx, card);
        } else {
            msg = Iso8583Builder.buildPurchaseMsg(ctx, card);
        }

        logger.log("Built " + msg.getMti() + " | STAN=" + ctx.stan11);

        // 2. Pack
        byte[] packed = StandardIsoPacker.pack(msg);
        String reqHex = StandardIsoPacker.bytesToHex(packed);

        FileLogger.logTestSuitePacket(appContext, logTag + " SEND", packed);
        FileLogger.logTestSuiteString(appContext, logTag + " SEND DETAIL",
                StandardIsoPacker.logIsoMessage(msg));

        // 3. Send
        logger.log("Sending to " + ctx.ip + ":" + ctx.port + "...");

        // Use injected client
        byte[] responseBytes = isoNetworkClient.sendAndReceive(ctx.ip, ctx.port, packed);

        FileLogger.logTestSuitePacket(appContext, logTag + " RECV", responseBytes);

        // 4. Unpack
        IsoMessage respMsg = new StandardIsoPacker().unpack(responseBytes);
        String respHex = StandardIsoPacker.bytesToHex(responseBytes);

        FileLogger.logTestSuiteString(appContext, logTag + " RECV DETAIL",
                StandardIsoPacker.logIsoMessage(respMsg));

        // 5. Result
        String rc = respMsg.getField(39);

        return new TransactionResult(ctx.stan11, rc, reqHex, respHex);
    }
}
