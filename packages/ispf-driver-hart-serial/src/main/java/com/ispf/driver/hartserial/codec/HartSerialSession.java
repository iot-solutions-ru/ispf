package com.ispf.driver.hartserial.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Synchronous HART serial-gateway TCP session: preamble plus short-frame PV reads over a byte stream.
 */
public final class HartSerialSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public HartSerialSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public float readPrimaryVariable(int deviceAddress, int command) throws IOException {
        writeFully(HartSerialCodec.encodeRequest(deviceAddress, command));
        byte[] responsePdu = HartSerialCodec.readHartPdu(in);
        return HartSerialCodec.extractPv(responsePdu);
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
