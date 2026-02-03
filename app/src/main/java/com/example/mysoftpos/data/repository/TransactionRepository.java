package com.example.mysoftpos.data.repository;

import androidx.lifecycle.LiveData;
import com.example.mysoftpos.data.local.entity.TransactionEntity;
import com.example.mysoftpos.iso8583.message.IsoMessage;
import com.example.mysoftpos.domain.model.CardInputData;
import java.util.List;

public interface TransactionRepository {
    // DB Operations
    LiveData<List<TransactionEntity>> getAllTransactions();

    void saveTransaction(TransactionEntity transaction);

    void updateTransactionStatus(String traceNumber, String status);

    void updateTransactionResponse(String traceNumber, String responseHex, String status);

    // ISO Operations (Future: Move from Activity to here)
    // void sendIsoMessage(IsoMessage message, Callback callback);

    void updateTransactionResponseHex(String traceNumber, String responseHex);
}





