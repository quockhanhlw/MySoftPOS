package com.example.mysoftpos.testsuite.util;

/**
 * Interface definition for a callback to be invoked when a PIN is entered.
 */
public interface PinCallback {
    /**
     * Called when a PIN is entered.
     *
     * @param pin The entered PIN string.
     */
    void onPinEntered(String pin);
}
