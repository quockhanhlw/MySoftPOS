package com.example.mysoftpos.data.remote;

import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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

    // Separate timeouts for better control on slow networks
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000; // 10s to establish TCP
    private static final int DEFAULT_READ_TIMEOUT_MS = 30000; // 30s to wait for Napas response

    private int connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
    private int readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;

    public IsoNetworkClient() {
    }

    /** Configure timeouts (e.g., shorter for reversal, longer for slow networks) */
    public IsoNetworkClient setConnectTimeout(int ms) {
        this.connectTimeoutMs = ms;
        return this;
    }

    public IsoNetworkClient setReadTimeout(int ms) {
        this.readTimeoutMs = ms;
        return this;
    }

    public byte[] sendAndReceive(String host, int port, byte[] requestData) throws IOException {
        long startTime = System.currentTimeMillis();
        Log.d(TAG, "Connecting to " + host + ":" + port);

        try (Socket socket = new Socket()) {
            // === TCP Socket Optimizations ===
            socket.setTcpNoDelay(true); // Disable Nagle's: send immediately, don't buffer small packets
            socket.setKeepAlive(true); // Detect dead connections on slow networks
            socket.setSendBufferSize(4096); // ISO msgs are small (~300 bytes), 4KB is enough
            socket.setReceiveBufferSize(4096);

            // Separate connect vs read timeout
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);

            long connectTime = System.currentTimeMillis() - startTime;
            Log.d(TAG, "Connected in " + connectTime + "ms");

            // === SEND: Header + Body in single write (avoid 2 TCP packets) ===
            int bodyLen = requestData.length;
            String lengthStr = String.format(Locale.US, "%04d", bodyLen);
            byte[] header = lengthStr.getBytes(StandardCharsets.US_ASCII);

            // Combine header + body into one buffer → single TCP packet
            byte[] combined = new byte[header.length + requestData.length];
            System.arraycopy(header, 0, combined, 0, header.length);
            System.arraycopy(requestData, 0, combined, header.length, requestData.length);

            Log.d(TAG, String.format("TX Header: [%s] Body: %d bytes", lengthStr, bodyLen));

            OutputStream out = new BufferedOutputStream(socket.getOutputStream(), 4096);
            out.write(combined); // Single write = single TCP packet
            out.flush();

            long sendTime = System.currentTimeMillis() - startTime;
            Log.d(TAG, "Data sent in " + sendTime + "ms");

            // === RECEIVE: Buffered stream for efficient reads ===
            InputStream in = new BufferedInputStream(socket.getInputStream(), 4096);
            byte[] response = readAdaptiveResponse(in);

            long totalTime = System.currentTimeMillis() - startTime;
            Log.d(TAG, "Total round-trip: " + totalTime + "ms (connect=" + connectTime
                    + "ms, response=" + (totalTime - sendTime) + "ms)");

            return response;

        } catch (SocketTimeoutException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            Log.e(TAG, "Timeout after " + elapsed + "ms", e);
            throw e;
        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            Log.e(TAG, "Network Error after " + elapsed + "ms", e);
            throw e;
        }
    }

    private byte[] readAdaptiveResponse(InputStream in) throws IOException {
        // Read first 2 bytes to sniff header type
        byte[] pfx = new byte[2];
        readFully(in, pfx, 0, 2);

        int bodyLength;

        // CHECK: BINARY HEADER? (Starts with 0x00 or high byte of length)
        if (pfx[0] == 0x00 || (pfx[0] & 0xFF) < 0x30) {
            // 2-BYTE BINARY HEADER
            bodyLength = ((pfx[0] & 0xFF) << 8) | (pfx[1] & 0xFF);
            Log.d(TAG, String.format("RX Header (Binary): [%02X %02X] Len=%d", pfx[0], pfx[1], bodyLength));
        } else {
            // 4-BYTE ASCII HEADER — read remaining 2 bytes
            byte[] suffix = new byte[2];
            readFully(in, suffix, 0, 2);

            String headerStr = new String(new byte[] { pfx[0], pfx[1], suffix[0], suffix[1] },
                    StandardCharsets.US_ASCII);
            try {
                bodyLength = Integer.parseInt(headerStr);
            } catch (NumberFormatException e) {
                throw new IOException("Unknown Header Format. Hex: " + bytesToHex(pfx) + bytesToHex(suffix));
            }
            Log.d(TAG, String.format("RX Header (ASCII): [%s] Len=%d", headerStr, bodyLength));
        }

        // Validate body length to prevent OOM on corrupted data
        if (bodyLength <= 0 || bodyLength > 65535) {
            throw new IOException("Invalid body length: " + bodyLength);
        }

        // Read body
        byte[] body = new byte[bodyLength];
        readFully(in, body, 0, bodyLength);

        Log.d(TAG, "RX Body: " + bodyLength + " bytes");
        return body;
    }

    /** Read exactly 'len' bytes or throw IOException */
    private void readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        int totalRead = 0;
        while (totalRead < len) {
            int c = in.read(buf, off + totalRead, len - totalRead);
            if (c == -1) {
                throw new IOException("Connection closed after reading " + totalRead + "/" + len + " bytes");
            }
            totalRead += c;
        }
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null)
            return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
