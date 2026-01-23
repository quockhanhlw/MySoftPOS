package com.example.mysoftpos.data.remote;

import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class IsoNetworkClient {

    private static final String TAG = "IsoNetworkClient";
    private static final int DEFAULT_TIMEOUT_MS = 30000;
    
    // Hardcoded Configuration (Zero-Config)
    private static final String DEFAULT_SERVER_IP = "10.145.54.218";//10.145.48.70/8386
    private static final int DEFAULT_SERVER_PORT = 8583;
    
    private final String host;
    private final int port;
    private int timeoutMs = DEFAULT_TIMEOUT_MS;

    public IsoNetworkClient() {
        this.host = DEFAULT_SERVER_IP;
        this.port = DEFAULT_SERVER_PORT;
    }
    
    public IsoNetworkClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public byte[] sendAndReceive(byte[] requestData) throws IOException {
        Socket socket = null;
        try {
            Log.d(TAG, "Connecting to " + host + ":" + port);
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            // --- FRAMING LOGIC (2-Byte Binary EXCLUSIVE) ---
            // Length = Body Length ONLY
            int bodyLen = requestData.length;
            
            byte[] header = new byte[2];
            header[0] = (byte) ((bodyLen >> 8) & 0xFF);
            header[1] = (byte) (bodyLen & 0xFF);
            
            // Debug Log: EXACT BYTES SENT
            Log.d(TAG, String.format("TX Header (Bin Exclusive): [%02X %02X] (Len=%d)", header[0], header[1], bodyLen));
            Log.d(TAG, "TX Body: " + bytesToHex(requestData));
            
            OutputStream out = socket.getOutputStream();
            out.write(header); // Write Header FIRST
            out.write(requestData);
            out.flush();
            Log.d(TAG, "Data sent successfully");

            // --- RECEIVE LOGIC ---
            InputStream in = socket.getInputStream();
            
            // Read 2 bytes Header
            byte[] headerBuf = new byte[2];
            int readHeaders = 0;
            while(readHeaders < 2) {
                int count = in.read(headerBuf, readHeaders, 2 - readHeaders);
                if(count == -1) break;
                readHeaders += count;
            }
            
            if (readHeaders < 2) {
                 throw new IOException("Server closed connection / No Header received");
            }
            
            // CHECK FOR ASCII MTI (No Header Case) - e.g. '0' (0x30)
            // If headerBuf[0] is 0x30 ('0'), it's likely the start of "0200" or "0210"
            // We can't easily handle "No Header" without knowing when to stop.
            // But strict ISO over TCP usually has a header. 
            // We will proceed assuming it's a Length Header.
            
            // Parse Body Length
            int respLen = ((headerBuf[0] & 0xFF) << 8) | (headerBuf[1] & 0xFF);
            Log.d(TAG, String.format("RX Header: [%02X %02X] (Value=%d)", headerBuf[0], headerBuf[1], respLen));
            
            // Sanity Check
            if (respLen > 10000 || respLen < 0) {
                 // Might be raw ASCII? "02" -> 12338. Too big?
                 // Let's assume invalid if too huge
                 Log.w(TAG, "Header Value suspicious. Proceeding anyway.");
            }
            
            byte[] response = new byte[respLen];
            int totalRead = 0;
            try {
                while (totalRead < respLen) {
                    int count = in.read(response, totalRead, respLen - totalRead);
                    if (count == -1) break;
                    totalRead += count;
                }
            } catch (SocketTimeoutException e) {
                // HANDLING INCLUSIVE LENGTH MISMATCH
                // If Server sent Inclusive Length (Len includes the 2 bytes of header)
                // We would be expecting 2 bytes more than available.
                // e.g. Server sends header value 120. Body is 118 bytes (120 - 2).
                // We read 118 bytes. We expect 120 total. Timeout waiting for last 2.
                if (totalRead == respLen - 2) {
                    Log.w(TAG, "Timeout with (Len-2) bytes read. Assuming Inclusive Length Header from Server. Accepting data.");
                    byte[] actualData = new byte[totalRead];
                    System.arraycopy(response, 0, actualData, 0, totalRead);
                    return actualData;
                } else if (totalRead > 10) {
                     // If we read something substantial but timed out, maybe we can try to use it?
                     // Riskier, but better than full crash during debug.
                     Log.w(TAG, "Timeout with partial data (" + totalRead + "/" + respLen + "). Attempting to return partial.");
                     byte[] actualData = new byte[totalRead];
                     System.arraycopy(response, 0, actualData, 0, totalRead);
                     return actualData;
                }
                throw e; // Rethrow if truly empty or broken
            }
            
            Log.d(TAG, "RX Body: " + bytesToHex(response));
            return response;

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
                    Log.w(TAG, "Error closing socket", e);
                }
            }
        }
    }
    
    // Helper for Hex Logging
    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
