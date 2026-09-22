package com.ispf.driver.interbus.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP session for INTERBUS length-prefixed process-image words. Not the Phoenix ASIC.
 */
public final class InterbusSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public InterbusSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public double readValue(int slot, int word) throws IOException {
        writeFully(InterbusCodec.encodeAddressedRead(slot, word));
        byte[] response = InterbusCodec.readFully(in, 4);
        return InterbusCodec.decodeProcessImageWord(response);
    }

    public void writeValue(int slot, int word, double value) throws IOException {
        int wordValue = ((int) Math.round(value)) & 0xFFFF;
        writeFully(InterbusCodec.encodeAddressedWrite(slot, word, wordValue));
        byte[] ack = InterbusCodec.readFully(in, 2);
        if (((ack[0] & 0xFF) << 8 | (ack[1] & 0xFF)) != 0) {
            throw new IOException("INTERBUS write rejected");
        }
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

    private void writeFully(byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }
}
