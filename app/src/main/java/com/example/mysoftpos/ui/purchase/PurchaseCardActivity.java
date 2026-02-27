package com.example.mysoftpos.ui.purchase;

import com.example.mysoftpos.ui.result.TransactionResultActivity;
import com.example.mysoftpos.R;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.ui.base.BaseCardEntryActivity;

/**
 * Purchase Card Activity — extends BaseCardEntryActivity.
 * NFC reading is handled by BaseCardEntryActivity via onTagDiscovered → ReadCardDataUseCase.
 * Adds: amount display, Purchase-specific result screen.
 */
public class PurchaseCardActivity extends BaseCardEntryActivity {

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
            // Manual entry
            String rawPan = etPan.getText().toString();
            if (rawPan.length() > 4)
                maskedPan = "**** " + rawPan.substring(rawPan.length() - 4);
        } else {
            // NFC - use real PAN from card read
            String nfcPan = getLastNfcPan();
            if (nfcPan != null && nfcPan.length() > 4) {
                maskedPan = "**** " + nfcPan.substring(nfcPan.length() - 4);
            }
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
