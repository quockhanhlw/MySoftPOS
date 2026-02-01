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

    public void runTransaction(String de22) {
        executor.execute(() -> {
            try {
                // 1. Prepare Context
                // 1. Prepare Context with ConfigManager
                com.example.mysoftpos.utils.ConfigManager config = com.example.mysoftpos.utils.ConfigManager
                        .getInstance(getApplication());

                TransactionContext ctx = new TransactionContext();
                ctx.amount4 = config.getDefaultAmount();
                // User Requirement: F11 tăng sau mỗi giao dịch
                ctx.stan11 = config.getAndIncrementTrace();
                ctx.generateDateTime();

                // User Requirement: RRN formula
                ctx.rrn37 = TransactionContext.calculateRrn(config.getServerId(), ctx.stan11);

                // Set Constants from Config
                ctx.processingCode3 = config.getProcessingCodePurchase();
                ctx.posCondition25 = config.getPosConditionCode();
                ctx.mcc18 = config.getMcc18();
                ctx.acquirerId32 = config.getAcquirerId32();
                ctx.terminalId41 = config.getTerminalId();
                ctx.merchantId42 = config.getMerchantId();

                // Format DE 43 using helper
                ctx.merchantNameLocation43 = config.getMerchantName();
                ctx.currency49 = config.getCurrencyCode49();

                // 2. Prepare Card Data based on DE 22
                CardInputData card;

                // Mock Card Data via ConfigManager (JSON)
                String track2 = config.getTrack2(de22);

                String pan = config.getMockPan();
                String expiry = config.getMockExpiry();

                if (track2 != null && track2.contains("=")) {
                    String[] parts = track2.split("=");
                    pan = parts[0];
                    if (parts[1].length() >= 4) {
                        expiry = parts[1].substring(0, 4);
                    }
                }

                boolean isContactless = de22.startsWith("07");

                card = new CardInputData(pan, expiry, de22, track2);

                // 3. Build Message
                logMessage.postValue("Building 0200...");
                IsoMessage msg = Iso8583Builder.buildPurchaseMsg(ctx, card);

                // 4. Pack
                byte[] packed = StandardIsoPacker.pack(msg);
                logMessage.postValue("Packed: " + StandardIsoPacker.bytesToHex(packed));

                // 5. Send (Mock)
                logMessage.postValue("Sending to Host...");
                Thread.sleep(1000);
                logMessage.postValue("Response: APPROVED (Simulated)");

            } catch (Exception e) {
                logMessage.postValue("Error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}
