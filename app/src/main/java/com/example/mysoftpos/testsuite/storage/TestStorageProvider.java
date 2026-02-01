package com.example.mysoftpos.testsuite.storage;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TestStorageProvider {

    private final Context context;

    public TestStorageProvider(Context context) {
        this.context = context;
    }

    /**
     * Saves raw ISO data to a binary file.
     * 
     * @param type     "request" or "response"
     * @param suiteId  ID of the suite (folder)
     * @param caseName Name of the case (ignored for filename, used for metadata if
     *                 needed)
     * @param data     Raw bytes
     * @return Absolute path to the saved file
     */
    public String saveIsoFile(String type, long suiteId, String caseName, byte[] data) throws IOException {
        if (data == null)
            return null;

        // Create Suite Directory
        File suiteDir = new File(context.getFilesDir(), "test_suites/" + suiteId);
        if (!suiteDir.exists()) {
            suiteDir.mkdirs();
        }

        // Generate Filename: req_TIMESTAMP.bin or res_TIMESTAMP.bin
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = String.format("%s_%s.bin", type, timeStamp);

        File dest = new File(suiteDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(data);
        }

        return dest.getAbsolutePath();
    }
}
