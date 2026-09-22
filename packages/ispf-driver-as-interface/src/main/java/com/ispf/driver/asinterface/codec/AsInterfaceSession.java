package com.ispf.driver.asinterface.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP session for AS-Interface master-call telegrams. Not the AS-i PHY.
 * <p>
 * Read uses command/data with bit7 clear; write sets bit7 so the peer can store outputs.
 */
public final class AsInterfaceSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public AsInterfaceSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public double readValue(int address, int command) throws IOException {
        writeFully(AsInterfaceCodec.encodeMasterCall(address, command & 0x7F));
        byte[] response = AsInterfaceCodec.readFully(in, 1);
        return response[0] & 0xFF;
    }

    public void writeValue(int address, int data) throws IOException {
        writeFully(AsInterfaceCodec.encodeMasterCall(address, 0x80 | (data & 0x7F)));
        byte[] ack = AsInterfaceCodec.readFully(in, 1);
        if ((ack[0] & 0xFF) != 0x00) {
            throw new IOException("AS-Interface write rejected: status=" + (ack[0] & 0xFF));
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
