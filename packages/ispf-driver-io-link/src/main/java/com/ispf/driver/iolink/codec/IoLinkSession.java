package com.ispf.driver.iolink.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP session for IO-Link ISDU requests via a master gateway. Not the IO-Link PHY.
 */
public final class IoLinkSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public IoLinkSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public double readValue(int port, int index, int subindex) throws IOException {
        writeFully(IoLinkCodec.encodeIsduRead(port, index, subindex));
        byte[] response = IoLinkCodec.readFully(in, 4);
        return IoLinkCodec.decodeFloat(response);
    }

    public void writeValue(int port, int index, int subindex, double value) throws IOException {
        writeFully(IoLinkCodec.encodeIsduWrite(port, index, subindex, (float) value));
        byte[] ack = IoLinkCodec.readFully(in, 1);
        if ((ack[0] & 0xFF) != 0x00) {
            throw new IOException("IO-Link write rejected: status=" + (ack[0] & 0xFF));
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
