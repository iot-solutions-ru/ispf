package com.ispf.driver.isa100.codec;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * TCP session for the ISA100 gateway ASCII/JSON lab (port 4840).
 * <p>
 * Not an ISA100.11a RF / Wireless Compliance Institute stack.
 */
public final class Isa100LabSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public Isa100LabSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public float readValue(String path) throws IOException {
        writeFully(Isa100LabCodec.encodeGet(path));
        return Isa100LabCodec.parseValue(readLine());
    }

    public void writeValue(String path, float value) throws IOException {
        writeFully(Isa100LabCodec.encodeSet(path, value));
        Isa100LabCodec.parseOkAck(readLine());
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
                    throw new EOFException("EOF reading ISA100 lab line");
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
