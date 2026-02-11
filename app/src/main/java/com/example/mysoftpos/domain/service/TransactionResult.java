package com.example.mysoftpos.domain.service;

/**
 * Result of a transaction execution.
 * Returned by TransactionExecutor after send/receive completes.
 */
public class TransactionResult {
    public final String stan;
    public final String rc;
    public final String status;
    public final String reqHex;
    public final String respHex;
    public final boolean approved;

    public TransactionResult(String stan, String rc, String reqHex, String respHex) {
        this.stan = stan;
        this.rc = rc;
        this.reqHex = reqHex;
        this.respHex = respHex;
        this.approved = "00".equals(rc);
        this.status = approved ? "APPROVED" : "DECLINED";
    }
}
