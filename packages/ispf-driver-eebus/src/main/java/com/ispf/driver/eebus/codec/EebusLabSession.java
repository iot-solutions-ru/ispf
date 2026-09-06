package com.ispf.driver.eebus.codec;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * TCP session for the EEBus SHIP/SPINE-lite over TCP lab (ASCII lines).
 * <p>
 * Not a full EEBus SHIP TLS stack / official EEBus SDK.
 */
public final class EebusLabSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public EebusLabSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public float readValue(String token) throws IOException {
        writeFully(EebusLabCodec.encodeGetBytes(token));
        String response = readLine();
        return EebusLabCodec.parseOkValue(response);
    }

    public void writeValue(String token, float value) throws IOException {
        writeFully(EebusLabCodec.encodeSetBytes(token, value));
        String response = readLine();
        EebusLabCodec.parseOkAck(response);
    }

    private void writeFully(byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(64);
        while (true) {
            int b = in.read();
            if (b < 0) {
                if (buffer.size() == 0) {
                    throw new EOFException("EOF reading EEBus lab line");
                }
                break;
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buffer.write(b);
            }
        }
        return buffer.toString(StandardCharsets.US_ASCII);
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
