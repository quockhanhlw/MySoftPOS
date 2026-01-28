package com.example.mysoftpos.testsuite;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mysoftpos.data.local.AppDatabase;
import com.example.mysoftpos.data.remote.IsoNetworkClient;
import com.example.mysoftpos.iso8583.IsoMessage;
import com.example.mysoftpos.iso8583.SmartIsoBuilder;
import com.example.mysoftpos.iso8583.TransactionContext;
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
 * REFACTORED RUNNER ACTIVITY
 * Uses SmartIsoBuilder and Dynamic Templates.
 * No hardcoded field logic here.
 */
public class RunnerActivity extends AppCompatActivity {

    private ConfigManager config;
    private TestResultDao testResultDao;
    private ExecutorService executor;
    private SmartIsoBuilder isoBuilder;

    // ... UI declarations omitted for brevity (same as TestRunnerActivity) ...

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // setContentView(...) // Use existing layout
        
        config = ConfigManager.getInstance(this);
        testResultDao = AppDatabase.getInstance(this).testResultDao();
        executor = Executors.newSingleThreadExecutor();
        isoBuilder = new SmartIsoBuilder(this);
        
        // ... initViews() ...
    }

    private void runTest(TestCase testCase, CardProfile card, String amount) {
        // 1. Generate Transaction Data
        String stan = config.getAndIncrementTrace();
        
        executor.execute(() -> {
             try {
                 // 2. Prepare Input Map (User Inputs)
                 Map<String, Object> inputs = new HashMap<>();
                 inputs.put(SmartIsoBuilder.KEY_PAN, card.getPan());
                 inputs.put(SmartIsoBuilder.KEY_EXPIRY, card.getExpiryDate());
                 inputs.put(SmartIsoBuilder.KEY_TRACK2, card.getTrack2());
                 inputs.put(SmartIsoBuilder.KEY_AMOUNT, amount);
                 inputs.put(SmartIsoBuilder.KEY_STAN, stan);
                 inputs.put(SmartIsoBuilder.KEY_POS_MODE, testCase.getPosEntryMode());
                 
                 // Inject Mock EMV if needed (Logic Logic moved to Builder or here? 
                 // Builder handles logic if tags provided. We provide tags.)
                 if (testCase.getPosEntryMode().startsWith("05") || testCase.getPosEntryMode().startsWith("07")) {
                      inputs.put(SmartIsoBuilder.KEY_EMV_TAGS, getMockEmvTags());
                 }

                 // 3. Build Message via Smart Builder
                 // Single Call - No manual setField()
                 IsoMessage msg = isoBuilder.build("0200", inputs);
                 
                 byte[] packed = StandardIsoPacker.pack(msg);
                 
                 // 4. DB Insert Pending
                 TestResult result = createPendingResult(testCase, card, stan, packed);
                 long id = testResultDao.insert(result);
                 result.id = id;

                 // 5. Send Network
                 IsoNetworkClient client = new IsoNetworkClient(config.getServerIp(), config.getServerPort());
                 byte[] respBytes = client.sendAndReceive(packed);
                 
                 // 6. Valid response
                 result.status = "SUCCESS";
                 result.responseHex = StandardIsoPacker.bytesToHex(respBytes);
                 testResultDao.update(result);
                 
                 // UI Update logic...
                 
             } catch (Exception e) {
                 // Error handling logic...
             }
        });
    }

    private TestResult createPendingResult(TestCase tc, CardProfile cp, String stan, byte[] packed) {
        TestResult r = new TestResult();
        r.testCaseId = tc.getId();
        r.stan = stan;
        r.requestHex = StandardIsoPacker.bytesToHex(packed);
        r.status = "PENDING";
        return r;
    }
    
    private Map<String, String> getMockEmvTags() {
        Map<String, String> m = new HashMap<>();
         m.put("9F26", "E293520E69D8C61D");
         // ... populate others ...
         return m;
    }
}
