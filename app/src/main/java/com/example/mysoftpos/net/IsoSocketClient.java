package com.example.mysoftpos.net;

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Minimal ISO8583 TCP client: connect -> send -> read (optional) -> disconnect.
 *
 * This matches your requirement: "tạo message xong disconnect port".
 */
public final class IsoSocketClient {

    public interface ResponseHandler {
        void onResponse(byte[] response);
    }

    public void sendOnce(@NonNull String ip, int port, int timeoutMs,
                         @NonNull byte[] request,
                         ResponseHandler handler) throws IOException {

        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
            out.write(request);
            out.flush();

            if (handler != null) {
                BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
                // Read up to 4096 for demo. You likely want: read length-prefix then exact payload.
                byte[] buf = new byte[4096];
                int n = in.read(buf);
                if (n > 0) {
                    byte[] resp = new byte[n];
                    System.arraycopy(buf, 0, resp, 0, n);
                    handler.onResponse(resp);
                }
            }
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
}

