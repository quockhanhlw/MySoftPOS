package com.example.mysoftpos.data.remote;

import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Handles ISO 8583 TCP Networking with TPDU Framing.
 * Protocol: [2-byte Length] + [5-byte TPDU] + [ISO Data]
 * Length Value = 5 (TPDU) + ISO Data Length.
 */
public class IsoNetworkClient {

    private static final String TAG = "IsoNetworkClient";
    private static final int DEFAULT_TIMEOUT_MS = 15000;
    
    // Hardcoded Configuration (Zero-Config)
    private static final String SERVER_IP = "10.145.54.158";
    private static final int SERVER_PORT = 8583;
    
    // Standard NII/TPDU header (60 00 00 00 00)
    private static final byte[] TPDU = new byte[]{0x60, 0x00, 0x00, 0x00, 0x00};
    
    private int timeoutMs = DEFAULT_TIMEOUT_MS;

    public IsoNetworkClient() {
    }

    /**
     * Sends ISO Data and receives Response.
     * Wraps data in TPDU + Length Header.
     * Unwraps Response TPDU + Length Header.
     * 
     * @param isoData Raw ISO 8583 bytes (MTI + Bitmap + Fields)
     * @return Raw ISO 8583 Response bytes (MTI + Bitmap + Fields)
     * @throws IOException on network failure
     */
    public byte[] sendAndReceive(byte[] isoData) throws IOException {
        Socket socket = null;
        try {
            Log.d(TAG, "Connecting to " + SERVER_IP + ":" + SERVER_PORT);
            socket = new Socket();
            socket.connect(new InetSocketAddress(SERVER_IP, SERVER_PORT), timeoutMs);
            
            // --- SENDER LOGIC ---
            OutputStream out = socket.getOutputStream();
            
            // 1. Calculate Total Body Length (TPDU + ISO Data)
            int bodyLen = TPDU.length + isoData.length;
            
            // 2. Prepare Header (2 Bytes)
            byte[] header = new byte[2];
            header[0] = (byte) ((bodyLen >> 8) & 0xFF);
            header[1] = (byte) (bodyLen & 0xFF);
            
            // 3. Write [Header] + [TPDU] + [ISO Data]
            out.write(header);
            out.write(TPDU);
            out.write(isoData);
            out.flush();
            
            Log.d(TAG, String.format("TX: Header=[%02X %02X] (Len=%d) + TPDU + Data(%d bytes)", 
                    header[0], header[1], bodyLen, isoData.length));
            
            // --- RECEIVER LOGIC ---
            InputStream in = socket.getInputStream();
            
            // 1. Read Header (2 Bytes)
            byte[] headerBuf = new byte[2];
            readFully(in, headerBuf);
            
            int respLen = ((headerBuf[0] & 0xFF) << 8) | (headerBuf[1] & 0xFF);
            Log.d(TAG, String.format("RX: Header=[%02X %02X] (Len=%d)", headerBuf[0], headerBuf[1], respLen));
            
            if (respLen <= 5 || respLen > 8192) {
                throw new IOException("Invalid Response Length: " + respLen + " (Must be > 5 for TPDU)");
            }
            
            // 2. Read Body (TPDU + ISO Data)
            byte[] bodyBuf = new byte[respLen];
            readFully(in, bodyBuf);
            
            // 3. Strip TPDU (First 5 bytes)
            // Verify TPDU? usually ignored in response or just logged.
            // We assume standard 5 bytes.
            int isoLen = respLen - 5;
            byte[] isoResponse = new byte[isoLen];
            System.arraycopy(bodyBuf, 5, isoResponse, 0, isoLen);
            
            Log.d(TAG, "RX: TPDU Stripped. ISO Data size: " + isoLen);
            return isoResponse;

        } catch (SocketTimeoutException e) {
            Log.e(TAG, "Timeout waiting for response", e);
            throw e;
        } catch (IOException e) {
            Log.e(TAG, "Network Error", e);
            throw e;
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }
    
    private void readFully(InputStream in, byte[] buffer) throws IOException {
        int total = 0;
        int len = buffer.length;
        while (total < len) {
            int count = in.read(buffer, total, len - total);
            if (count == -1) {
                 throw new IOException("Unexpected End of Stream (read " + total + " of " + len + ")");
            }
            total += count;
        }
    }
}
