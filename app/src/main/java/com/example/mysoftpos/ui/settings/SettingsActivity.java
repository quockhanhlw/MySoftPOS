package com.example.mysoftpos.ui.settings;

import com.example.mysoftpos.R;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
public class SettingsActivity extends AppCompatActivity {
    private TextInputEditText etServerIp;
    private TextInputEditText etPort;
    private TextInputEditText etTimeout;
    private TextInputEditText etTerminalId;
    private TextInputEditText etMerchantId;
    private CheckBox cbEncryptPin;
    private MaterialButton btnSaveConfig;
    private MaterialButton btnPingTest;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "softpos_config";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        etServerIp = findViewById(R.id.etServerIp);
        etPort = findViewById(R.id.etPort);
        etTimeout = findViewById(R.id.etTimeout);
        etTerminalId = findViewById(R.id.etTerminalId);
        etMerchantId = findViewById(R.id.etMerchantId);
        cbEncryptPin = findViewById(R.id.cbEncryptPin);
        btnSaveConfig = findViewById(R.id.btnSaveConfig);
        btnPingTest = findViewById(R.id.btnPingTest);
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadConfiguration();
        btnSaveConfig.setOnClickListener(v -> saveConfiguration());
        btnPingTest.setOnClickListener(v -> performPingTest());
    }
    private void loadConfiguration() {
        etServerIp.setText(sharedPreferences.getString("server_ip", ""));
        etPort.setText(String.valueOf(sharedPreferences.getInt("server_port", 8888))); // Changed to getInt
        etTimeout.setText(sharedPreferences.getString("timeout", "30000"));
        etTerminalId.setText(sharedPreferences.getString("terminal_id", ""));
        etMerchantId.setText(sharedPreferences.getString("merchant_id", ""));
        cbEncryptPin.setChecked(sharedPreferences.getBoolean("encrypt_pin", true));
    }
    private void saveConfiguration() {
        String serverIp = etServerIp.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();
        String timeout = etTimeout.getText().toString().trim();
        String terminalId = etTerminalId.getText().toString().trim();
        String merchantId = etMerchantId.getText().toString().trim();
        if (serverIp.isEmpty()) {
            etServerIp.setError("Vui long nhap IP Server");
            return;
        }
        if (portStr.isEmpty()) {
            etPort.setError("Vui long nhap Port");
            return;
        }
        int port = Integer.parseInt(portStr);

        if (terminalId.isEmpty()) {
            etTerminalId.setError("Vui long nhap Terminal ID");
            return;
        }
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("server_ip", serverIp);
        editor.putInt("server_port", port); // Changed to putInt and "server_port"
        editor.putString("timeout", timeout);
        editor.putString("terminal_id", terminalId);
        editor.putString("merchant_id", merchantId);
        editor.putBoolean("encrypt_pin", cbEncryptPin.isChecked());
        editor.apply();
        Toast.makeText(this, "Da luu cau hinh thanh cong!", Toast.LENGTH_SHORT).show();
        finish();
    }
    private void performPingTest() {
        String serverIp = etServerIp.getText().toString().trim();
        String port = etPort.getText().toString().trim();
        if (serverIp.isEmpty() || port.isEmpty()) {
            Toast.makeText(this, "Vui long nhap day du thong tin ket noi", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Dang thuc hien Ping Test...", Toast.LENGTH_LONG).show();
    }
}






