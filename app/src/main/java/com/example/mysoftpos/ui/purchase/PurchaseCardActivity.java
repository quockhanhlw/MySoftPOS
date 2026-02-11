package com.example.mysoftpos.ui.purchase;

import com.example.mysoftpos.ui.result.TransactionResultActivity;
import com.example.mysoftpos.R;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.ui.base.BaseCardEntryActivity;

/**
 * Purchase Card Activity — extends BaseCardEntryActivity.
 * Adds: NFC reader, amount display, Purchase-specific result screen.
 */
public class PurchaseCardActivity extends BaseCardEntryActivity implements NfcAdapter.ReaderCallback {

    private NfcAdapter nfcAdapter;
    private String amount;
    private String currency;
    private String currencyCode;
    private TxnType txnType;
    private TextView tvAmountDisplay;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_purchase_card;
    }

    @Override
    protected int getSubmitButtonId() {
        return R.id.btnSubmitManual;
    }

    @Override
    protected void onCreateExtra(Bundle savedInstanceState) {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        // Intent Data
        String typeStr = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.TXN_TYPE);
        amount = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.AMOUNT);
        currency = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.CURRENCY);
        currencyCode = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.CURRENCY_CODE);
        if (amount == null)
            amount = "0";
        if (currency == null)
            currency = "VND";
        if (currencyCode == null)
            currencyCode = "704";
        txnType = "BALANCE_INQUIRY".equals(typeStr) ? TxnType.BALANCE_INQUIRY : TxnType.PURCHASE;

        // Amount Display
        tvAmountDisplay = findViewById(R.id.tvAmountDisplay);
        if (txnType == TxnType.BALANCE_INQUIRY) {
            findViewById(R.id.amountContainer).setVisibility(View.GONE);
        } else {
            tvAmountDisplay.setText(formatAmount(amount));
        }
    }

    @Override
    protected void onCardDataReady(CardInputData card) {
        String username = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME);
        if (username == null)
            username = getString(R.string.guest_user);
        viewModel.processTransaction(card, amount, currencyCode, txnType, username);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null && getCurrentMode() == 1) {
            nfcAdapter.enableReaderMode(this, this,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                            | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableReaderMode(this);
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null)
            return;

        String trk2 = configManager.getTrack2("022");
        String mockPan = configManager.getMockPan();
        String mockExp = configManager.getMockExpiry();

        if (trk2 != null && trk2.contains("=")) {
            String[] parts = trk2.split("=");
            mockPan = parts[0];
            if (parts[1].length() >= 4)
                mockExp = parts[1].substring(0, 4);
        }

        CardInputData mockData = new CardInputData(mockPan, mockExp, "022", trk2);
        runOnUiThread(() -> {
            Toast.makeText(this, getString(R.string.msg_card_detected), Toast.LENGTH_SHORT).show();
            onCardDataReady(mockData);
        });
    }

    @Override
    protected void onTransactionResult(boolean success, String msg, String isoResp, String isoReq) {
        Intent intent = new Intent(this, TransactionResultActivity.class);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.TXN_TYPE, txnType.name());
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.SUCCESS, success);
        intent.putExtra(TransactionResultActivity.EXTRA_RESULT_TYPE,
                success ? TransactionResultActivity.ResultType.SUCCESS
                        : TransactionResultActivity.ResultType.TRANSACTION_FAILED);
        intent.putExtra(TransactionResultActivity.EXTRA_MESSAGE, msg);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.AMOUNT, amount);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.CURRENCY, currency);

        // Masked PAN
        String maskedPan = "**** 0000";
        if (getCurrentMode() == 0) {
            String rawPan = etPan.getText().toString();
            if (rawPan.length() > 4)
                maskedPan = "**** " + rawPan.substring(rawPan.length() - 4);
        } else {
            String rawPan = configManager.getMockPan();
            String trk2 = configManager.getTrack2("022");
            if (trk2 != null && trk2.contains("=")) {
                rawPan = trk2.split("=")[0];
            }
            if (rawPan != null && rawPan.length() > 4)
                maskedPan = "**** " + rawPan.substring(rawPan.length() - 4);
        }
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.MASKED_PAN, maskedPan);

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault());
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.TXN_DATE, sdf.format(new java.util.Date()));
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.TXN_ID, "TXN" + System.currentTimeMillis() % 100000000);

        if (isoResp != null)
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.RAW_RESPONSE, isoResp);
        if (isoReq != null)
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.RAW_REQUEST, isoReq);
        startActivity(intent);
        finish();
    }

    private String formatAmount(String amt) {
        try {
            long val = Long.parseLong(amt);
            return String.format(Locale.ROOT, "%,d", val);
        } catch (NumberFormatException e) {
            return amt;
        }
    }
}
