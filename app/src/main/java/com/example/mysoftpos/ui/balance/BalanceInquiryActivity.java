package com.example.mysoftpos.ui.balance;

import com.example.mysoftpos.ui.result.TransactionResultActivity;
import com.example.mysoftpos.R;

import android.content.Intent;
import android.os.Bundle;

import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.iso8583.TxnType;
import com.example.mysoftpos.ui.base.BaseCardEntryActivity;

/**
 * Balance Inquiry Activity — extends BaseCardEntryActivity.
 * Adds: DE54 balance parsing in result screen.
 */
public class BalanceInquiryActivity extends BaseCardEntryActivity {

    private String lastUsedPan;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_balance_inquiry;
    }

    @Override
    protected int getSubmitButtonId() {
        return R.id.btnSubmit;
    }

    @Override
    protected void onCardDataReady(CardInputData card) {
        String username = getIntent().getStringExtra(com.example.mysoftpos.utils.IntentKeys.USERNAME);
        if (username == null)
            username = getString(R.string.guest_user);
        long userId = getIntent().getLongExtra(com.example.mysoftpos.utils.IntentKeys.USER_ID, -1);

        this.lastUsedPan = card.getPan();
        viewModel.processTransaction(card, "0", "704", TxnType.BALANCE_INQUIRY, username, userId);
    }

    @Override
    protected void showLoading(boolean loading) {
        super.showLoading(loading);
        // Also disable submit button during loading
        android.view.View btnSubmit = findViewById(R.id.btnSubmit);
        if (btnSubmit != null)
            btnSubmit.setEnabled(!loading);
    }

    @Override
    protected void onTransactionResult(boolean success, String message, String isoResponse, String isoRequest) {
        Intent intent = new Intent(this, TransactionResultActivity.class);
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.TXN_TYPE, TxnType.BALANCE_INQUIRY.name());
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.SUCCESS, success);
        intent.putExtra(TransactionResultActivity.EXTRA_RESULT_TYPE,
                success ? TransactionResultActivity.ResultType.SUCCESS
                        : TransactionResultActivity.ResultType.TRANSACTION_FAILED);
        intent.putExtra(TransactionResultActivity.EXTRA_MESSAGE, message);

        if (isoResponse != null) {
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.RAW_RESPONSE, isoResponse);
            parseAndAddBalanceExtras(intent, isoResponse);
        }
        if (isoRequest != null) {
            intent.putExtra(com.example.mysoftpos.utils.IntentKeys.RAW_REQUEST, isoRequest);
        }

        // Masked PAN
        String panToMask = (lastUsedPan != null) ? lastUsedPan : "";
        if (panToMask.isEmpty()) {
            // Fallback: try NFC PAN from base class
            String nfcPan = getLastNfcPan();
            if (nfcPan != null) panToMask = nfcPan;
        }
        String maskedPan = panToMask.length() > 4
                ? "**** " + panToMask.substring(panToMask.length() - 4)
                : "**** 0000";
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.MASKED_PAN, maskedPan);

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss dd/MM/yyyy",
                java.util.Locale.getDefault());
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.TXN_DATE, sdf.format(new java.util.Date()));
        intent.putExtra(com.example.mysoftpos.utils.IntentKeys.TXN_ID, "TXN" + System.currentTimeMillis() % 100000000);

        startActivity(intent);
        finish();
    }

    /**
     * Parse DE 54 (Additional Amounts) from the ISO response to extract balance.
     */
    private void parseAndAddBalanceExtras(Intent intent, String isoResponse) {
        try {
            com.example.mysoftpos.iso8583.message.IsoMessage respMsg = new com.example.mysoftpos.iso8583.util.StandardIsoPacker()
                    .unpack(com.example.mysoftpos.iso8583.util.StandardIsoPacker.hexToBytes(isoResponse));
            String de54 = respMsg.getField(54);

            if (de54 == null || de54.length() < 20)
                return;

            String availableBalance = null;
            String ledgerBalance = null;
            String currency = null;

            for (int i = 0; i + 20 <= de54.length(); i += 20) {
                String block = de54.substring(i, i + 20);
                String amtType = block.substring(2, 4);
                String curr = block.substring(4, 7);
                char sign = block.charAt(7);
                String rawAmt = block.substring(8, 20);

                if (currency == null)
                    currency = curr;

                if (rawAmt.startsWith("E")) {
                    rawAmt = "OVERFLOW";
                }

                String formattedAmt = rawAmt;
                if (!"OVERFLOW".equals(rawAmt)) {
                    try {
                        long val = Long.parseLong(rawAmt);
                        if ("704".equals(curr))
                            val = val / 100;
                        formattedAmt = (sign == 'D') ? "-" + val : String.valueOf(val);
                    } catch (NumberFormatException e) {
                        formattedAmt = rawAmt;
                    }
                }

                if ("02".equals(amtType))
                    availableBalance = formattedAmt;
                else if ("01".equals(amtType))
                    ledgerBalance = formattedAmt;
            }

            if (availableBalance != null) {
                intent.putExtra(com.example.mysoftpos.utils.IntentKeys.AMOUNT, availableBalance);
                intent.putExtra(com.example.mysoftpos.utils.IntentKeys.BALANCE_TYPE, "Available");
            } else if (ledgerBalance != null) {
                intent.putExtra(com.example.mysoftpos.utils.IntentKeys.AMOUNT, ledgerBalance);
                intent.putExtra(com.example.mysoftpos.utils.IntentKeys.BALANCE_TYPE, "Ledger");
            }
            if (currency != null)
                intent.putExtra(com.example.mysoftpos.utils.IntentKeys.CURRENCY, currency);

        } catch (Throwable e) {
            android.util.Log.e("BalanceInquiry", "Parse DE54", e);
            com.example.mysoftpos.utils.logging.FileLogger.logString(this, "ERROR",
                    "Failed to parse DE54: " + e.getMessage());
        }
    }
}
