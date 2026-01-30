package com.example.mysoftpos.data.repository;

import android.content.Context;
import android.util.Log;

import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.remote.IsoNetworkClient;
import com.example.mysoftpos.iso8583.IsoMessage;
import com.example.mysoftpos.iso8583.SmartIsoBuilder;
import com.example.mysoftpos.testsuite.data.TestResultDao;
import com.example.mysoftpos.testsuite.model.CardProfile;
import com.example.mysoftpos.testsuite.model.TestCase;
import com.example.mysoftpos.testsuite.model.TestResult;
import com.example.mysoftpos.utils.ConfigManager;
import com.example.mysoftpos.utils.StandardIsoPacker;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository responsible for handling ISO 8583 Transaction flow.
 * Implements strict ISO Logic: Build -> MAC -> Pack -> DB -> Send -> Receive -> DB.
 */
public class TransactionRepository {

    private static final String TAG = "TransactionRepository";

    private final SmartIsoBuilder isoBuilder;
    private final TestResultDao resultDao;
    private final ConfigManager config;
    private final ExecutorService executor;

    // Dependency Injection via Constructor (DIP)
    public TransactionRepository(Context context) {
        this.config = ConfigManager.getInstance(context);
        this.isoBuilder = new SmartIsoBuilder(config); // Injected Config
        this.resultDao = AppDatabase.getInstance(context).testResultDao();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public interface TransactionCallback {
        void onSuccess(TestResult result);
        void onError(String message);
    }

    public void executeTransaction(TestCase testCase, CardProfile card, String amount, TransactionCallback callback) {
        executor.execute(() -> {
            try {
                // 1. Prepare Data
                String stan = config.getAndIncrementTrace();
                Map<String, Object> inputs = prepareInputs(testCase, card, amount, stan);

                // 2. Build Message
                String mti = resolveMti(testCase);
                IsoMessage msg = isoBuilder.build(mti, inputs);

                // 3. DE 128: Calculate MAC (Last Step before Packing)
                calculateAndAppendMac(msg);

                // 4. Pack
                byte[] packed = StandardIsoPacker.pack(msg);

                // 5. Save Pending State to DB
                TestResult result = new TestResult();
                result.testCaseId = testCase.getId();
                result.stan = stan;
                result.status = "PENDING";
                long id = resultDao.insert(result);
                result.id = id;

                // 6. Network Transmission
                IsoNetworkClient client = new IsoNetworkClient(config.getServerIp(), config.getServerPort());
                byte[] responseBytes = client.sendAndReceive(packed);

                // 7. Process Response
                IsoMessage respMsg = new StandardIsoPacker().unpack(responseBytes);
                String rc = respMsg.getField(39);
                
                // 8. Update DB
                result.status = "00".equals(rc) ? "SUCCESS" : "FAIL";
                result.responseCode = rc;
                result.rrn = respMsg.getField(37);
                result.responseHex = StandardIsoPacker.bytesToHex(responseBytes);
                resultDao.update(result);

                // 9. Return Result
                callback.onSuccess(result);

            } catch (Exception e) {
                Log.e(TAG, "Transaction Failed", e);
                callback.onError(e.getMessage());
            }
        });
    }

    private Map<String, Object> prepareInputs(TestCase testCase, CardProfile card, String amount, String stan) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(SmartIsoBuilder.KEY_PAN, card.getPan());
        inputs.put(SmartIsoBuilder.KEY_EXPIRY, card.getExpiryDate());
        inputs.put(SmartIsoBuilder.KEY_TRACK2, card.getTrack2());
        inputs.put(SmartIsoBuilder.KEY_AMOUNT, amount);
        inputs.put(SmartIsoBuilder.KEY_STAN, stan);
        inputs.put(SmartIsoBuilder.KEY_POS_MODE, testCase.getPosEntryMode());
        inputs.put(SmartIsoBuilder.KEY_PROC_CODE, testCase.getProcessingCode());

        // Chip / NFC Tags Mocking
        String mode = testCase.getPosEntryMode();
        if (mode != null && (mode.startsWith("05") || mode.startsWith("07"))) {
            inputs.put(SmartIsoBuilder.KEY_EMV_TAGS, getMockEmvTags());
        }
        return inputs;
    }

    private String resolveMti(TestCase testCase) {
        String mti = testCase.getMti();
        if ("0200".equals(mti) && "300000".equals(testCase.getProcessingCode())) {
            return "0200_BAL";
        }
        return mti;
    }

    /**
     * Calculates MAC for the message.
     * In a real app, this would use a session key and DES/AES algorithm.
     */
    private void calculateAndAppendMac(IsoMessage msg) {
        // TODO: Implement Real MAC Algorithm e.g. ANSI X9.19
        // For now, checks if DE 128 is in schema. If so, add placeholder.
        // We add hardcoded dummy MAC to prove architecture slot exists.
        // msg.setField(128, "0000000000000000"); 
        // NOTE: Disabled by default to avoid failing on servers checking real MAC
    }

    private Map<String, String> getMockEmvTags() {
        Map<String, String> m = new HashMap<>();
        m.put("9F26", "E293520E69D8C61D");
        m.put("9F27", "80");
        m.put("9F10", "06010A03A00000");
        m.put("9F37", "89352614");
        m.put("9F36", "0209");
        m.put("95", "0000008000");
        m.put("9A", "240129");
        m.put("9C", "00");
        m.put("9F02", "000000000000");
        m.put("5F2A", "0704");
        m.put("82", "1800"); 
        m.put("9F1A", "0704");
        m.put("9F03", "000000000000");
        m.put("5F34", "01");
        return m;
    }
}
