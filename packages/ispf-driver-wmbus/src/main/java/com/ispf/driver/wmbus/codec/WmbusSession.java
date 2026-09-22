package com.ispf.driver.wmbus.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;

/**
 * Wireless M-Bus TCP serial-server session.
 * <p>
 * Exchanges EN 13757-4 Format-A link frames (with CRC-16) over a TCP socket.
 * A poll sends {@code REQ_UD2}; the peer replies with a full CI {@code 0x7A} telegram.
 */
public final class WmbusSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public WmbusSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public WmbusCodec.ParsedTelegram pollMeter(int meterIndex) throws IOException {
        writeFully(WmbusCodec.encodePollRequest(0x0000, meterIndex & 0xFFFFFFFFL, 0, 0));
        return WmbusCodec.parse(WmbusCodec.readFrame(in));
    }

    public WmbusCodec.ParsedTelegram pollDeviceId(String hexId) throws IOException {
        String cleaned = hexId.trim().toUpperCase(Locale.ROOT);
        long deviceId = Long.parseLong(cleaned, 16);
        writeFully(WmbusCodec.encodePollRequest(0xFFFF, deviceId, 0, 0));
        return WmbusCodec.parse(WmbusCodec.readFrame(in));
    }

    private void writeFully(byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
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
