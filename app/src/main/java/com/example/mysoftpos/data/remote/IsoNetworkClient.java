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

    public byte[] sendAndReceive(byte[] requestData) throws IOException {
        Log.d(TAG, "Connecting to " + host + ":" + port);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs); // CRITICAL: Read timeout

            // --- SEND: 4-BYTE ASCII HEADER (User Spec) ---
            int bodyLen = requestData.length;
            String lengthStr = String.format(Locale.US, "%04d", bodyLen);
            byte[] header = lengthStr.getBytes(StandardCharsets.US_ASCII);

            Log.d(TAG, String.format("TX Header: [%s] Body: %s", lengthStr, bytesToHex(requestData)));

            OutputStream out = socket.getOutputStream();
            out.write(header); // 4 bytes ASCII
            out.write(requestData); // Body
            out.flush();
            Log.d(TAG, "Data sent successfully");

            // --- RECEIVE: ADAPTIVE HEADER HANDLING ---
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
        // Read first 2 bytes to sniff header type
        byte[] pfx = new byte[2];
        int read = 0;
        while (read < 2) {
            int c = in.read(pfx, read, 2 - read);
            if (c == -1)
                throw new IOException("Server closed during prefix read");
            read += c;
        }

        int bodyLength;
        byte[] body;

        // CHECK: BINARY HEADER? (Starts with 0x00 or high byte of length)
        // ISO msgs > 0 bytes. 0x00 is definitely binary.
        // 0x30 ('0') is ASCII.
        if (pfx[0] == 0x00 || (pfx[0] & 0xFF) < 0x30) {
            // ---> 2-BYTE BINARY HEADER DETECTED <---
            // Length = [pfx0][pfx1]
            bodyLength = ((pfx[0] & 0xFF) << 8) | (pfx[1] & 0xFF);
            Log.d(TAG, String.format("RX Header (Binary): [%02X %02X] Len=%d", pfx[0], pfx[1], bodyLength));

            // Read Body
            body = new byte[bodyLength];
            int totalRead = 0;
            while (totalRead < bodyLength) {
                int c = in.read(body, totalRead, bodyLength - totalRead);
                if (c == -1)
                    throw new IOException("Server closed during body read");
                totalRead += c;
            }

        } else {
            // ---> ASSUME 4-BYTE ASCII HEADER <---
            // We have pfx[0], pfx[1] (ASCII). Need next 2 bytes.
            byte[] suffix = new byte[2];
            read = 0;
            while (read < 2) {
                int c = in.read(suffix, read, 2 - read);
                if (c == -1)
                    throw new IOException("Server closed during suffix read");
                read += c;
            }

            // Full Header: pfx + suffix
            String headerStr = new String(new byte[] { pfx[0], pfx[1], suffix[0], suffix[1] },
                    StandardCharsets.US_ASCII);
            try {
                bodyLength = Integer.parseInt(headerStr);
            } catch (NumberFormatException e) {
                throw new IOException("Unknown Header Format. Hex: " + bytesToHex(pfx) + bytesToHex(suffix));
            }

            Log.d(TAG, String.format("RX Header (ASCII): [%s] Len=%d", headerStr, bodyLength));

            // Read Body
            body = new byte[bodyLength];
            int totalRead = 0;
            while (totalRead < bodyLength) {
                int c = in.read(body, totalRead, bodyLength - totalRead);
                if (c == -1)
                    throw new IOException("Server closed during body read");
                totalRead += c;
            }
        }

        Log.d(TAG, "RX Body Clean: " + bytesToHex(body));
        return body;
    }

    // Helper for Hex Logging
    private String bytesToHex(byte[] bytes) {
        if (bytes == null)
            return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
