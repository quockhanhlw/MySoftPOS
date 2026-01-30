package com.example.mysoftpos.data.remote;

import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Low-level TCP Client for ISO 8583 communication.
 * Responsibility: Send bytes -> Receive bytes. 
 * Does NOT know about ISO parsing.
 */
public class IsoNetworkClient {

    private static final String TAG = "IsoNetworkClient";
    private static final int DEFAULT_TIMEOUT_MS = 30000;
    
    private final String host;
    private final int port;
    private int timeoutMs = DEFAULT_TIMEOUT_MS;

    public IsoNetworkClient(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    public void setTimeout(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public byte[] sendAndReceive(byte[] requestData) throws IOException {
        Log.d(TAG, "Connecting to " + host + ":" + port);
        
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeoutMs); 
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            
            // --- SEND: 4-BYTE ASCII HEADER ---
            // Naming: "Length Header" is compliant with common TCP Framing
            int bodyLen = requestData.length;
            String lengthStr = String.format(Locale.US, "%04d", bodyLen);
            byte[] header = lengthStr.getBytes(StandardCharsets.US_ASCII);
            
            Log.d(TAG, String.format("TX Header: [%s] BodyLen: %d", lengthStr, bodyLen));
            
            OutputStream out = socket.getOutputStream();
            out.write(header);      
            out.write(requestData); 
            out.flush();

            // --- RECEIVE ---
            InputStream in = socket.getInputStream();
            return readAdaptiveResponse(in);
            
        } catch (SocketTimeoutException e) {
            Log.e(TAG, "Timeout waiting for response", e);
            throw e;
        } catch (IOException e) {
            Log.e(TAG, "Network Error", e);
            throw e;
        }
    }

    private byte[] readAdaptiveResponse(InputStream in) throws IOException {
        // Read first 2 bytes to detect format (ASCII vs Binary)
        byte[] pfx = new byte[2];
        readFully(in, pfx, 2);
        
        int bodyLength;
        byte[] body;
        
        // CHECK: BINARY HEADER? (Starts with 0x00 or high byte < 0x30 aka '0')
        if (pfx[0] == 0x00 || (pfx[0] & 0xFF) < 0x30) {
            // Binary Header (2 bytes)
            bodyLength = ((pfx[0] & 0xFF) << 8) | (pfx[1] & 0xFF);
            Log.d(TAG, "RX Header (Binary). Len=" + bodyLength);
            
        } else {
            // ASCII Header (4 bytes). We have first 2. Need next 2.
            byte[] suffix = new byte[2];
            readFully(in, suffix, 2);
            
            String headerStr = new String(new byte[]{pfx[0], pfx[1], suffix[0], suffix[1]}, StandardCharsets.US_ASCII);
            try {
                bodyLength = Integer.parseInt(headerStr);
            } catch (NumberFormatException e) {
                 throw new IOException("Invalid Header: " + headerStr);
            }
            Log.d(TAG, "RX Header (ASCII): " + headerStr + " Len=" + bodyLength);
        }
        
        // Read Body
        body = new byte[bodyLength];
        readFully(in, body, bodyLength);
        
        return body;
    }
    
    // Helper to allow cleaner read loop
    private void readFully(InputStream in, byte[] buffer, int length) throws IOException {
        int totalRead = 0;
        while(totalRead < length) {
            int c = in.read(buffer, totalRead, length - totalRead);
            if (c == -1) throw new IOException("Server closed connection prematurely");
            totalRead += c;
        }
    }
}
