package com.ispf.driver.zigbee.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Silicon Labs ASH (EZSP UART) session over TCP — not 802.15.4 and not a ZCL stack.
 * <p>
 * Connect sends the host RST frame and accepts a CRC-verified RSTACK. Clean-room
 * Apache-2.0 — JDK sockets only.
 */
public final class AshSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private AshCodec.AshRstack rstack;

    public AshSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        handshake();
    }

    private void handshake() throws IOException {
        AshCodec.writeHostReset(out);
        byte[] payload = AshCodec.readFramePayload(in);
        rstack = AshCodec.parseRstack(payload);
    }

    /**
     * Re-issues host RST and returns the peer RSTACK (version / reason).
     */
    public AshCodec.AshRstack reset() throws IOException {
        handshake();
        return rstack;
    }

    public AshCodec.AshRstack rstack() {
        return rstack;
    }

    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
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
