package com.example.mysoftpos.data;

import java.io.IOException;

public interface CardTransceiver {
    /**
     * Send command APDU and receive response APDU.
     *
     * @param commandApdu The command APDU to send.
     * @return The response APDU received.
     * @throws IOException If communication fails.
     */
    byte[] transceive(byte[] commandApdu) throws IOException;

    /**
     * Check if the transceiver is currently connected.
     *
     * @return true if connected, false otherwise.
     */
    boolean isConnected();

    /**
     * Close the connection.
     */
    void close();
}
