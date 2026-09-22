package com.ispf.driver.profinet.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP session for PROFINET DCP Identify carried as an Ethernet payload over a gateway socket.
 * Not PROFINET RT/IRT.
 */
public final class ProfinetSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public ProfinetSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public double readValue(int slot, int subslot) throws IOException {
        writeFully(ProfinetCodec.encodeIdentifyRequest(slot, subslot));
        byte[] response = ProfinetCodec.readFully(in, 4);
        return ProfinetCodec.decodeFloat(response);
    }

    public void writeValue(int slot, int subslot, double value) throws IOException {
        writeFully(ProfinetCodec.encodeIdentifyRequest(slot, subslot, (float) value));
        byte[] ack = ProfinetCodec.readFully(in, 1);
        if ((ack[0] & 0xFF) != 0x00) {
            throw new IOException("PROFINET write rejected: status=" + (ack[0] & 0xFF));
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
