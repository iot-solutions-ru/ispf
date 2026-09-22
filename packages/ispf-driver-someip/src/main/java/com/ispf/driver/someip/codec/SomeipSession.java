package com.ispf.driver.someip.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP SOME/IP session: sends REQUEST frames and reads RESPONSE ({@code messageType=0x80}).
 */
public final class SomeipSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final int clientId;
    private final AtomicInteger sessionId = new AtomicInteger(1);

    public SomeipSession(String host, int port, int clientId, int timeoutMs) throws IOException {
        this.clientId = clientId & 0xFFFF;
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        socket.setTcpNoDelay(true);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }

    public synchronized byte[] request(int service, int method, byte[] payload, boolean expectResponse)
            throws IOException {
        int session = sessionId.getAndUpdate(v -> v == 0xFFFF ? 1 : v + 1);
        byte messageType = expectResponse ? SomeipCodec.MSG_REQUEST : SomeipCodec.MSG_REQUEST_NO_RETURN;
        byte[] frame = SomeipCodec.encodeFrame(
                service, method, clientId, session, messageType, SomeipCodec.E_OK, payload);
        out.write(frame);
        out.flush();
        if (!expectResponse) {
            return payload == null ? new byte[0] : payload;
        }
        byte[] response = SomeipCodec.readTcpFrame(in);
        SomeipCodec.SomeipFrame parsed = SomeipCodec.decodeFrame(response);
        if (parsed.service() != (service & 0xFFFF) || parsed.method() != (method & 0xFFFF)) {
            throw new IOException("SOME/IP response service/method mismatch");
        }
        if (parsed.messageType() != SomeipCodec.MSG_RESPONSE) {
            throw new IOException("SOME/IP expected RESPONSE 0x80, got 0x"
                    + Integer.toHexString(parsed.messageType() & 0xFF));
        }
        if (parsed.returnCode() != SomeipCodec.E_OK) {
            throw new IOException("SOME/IP return code 0x"
                    + Integer.toHexString(parsed.returnCode() & 0xFF));
        }
        return parsed.payload();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
