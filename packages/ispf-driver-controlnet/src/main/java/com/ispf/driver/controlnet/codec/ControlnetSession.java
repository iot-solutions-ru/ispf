package com.ispf.driver.controlnet.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP session for ControlNet CIP explicit messaging. Not native ControlNet coax.
 */
public final class ControlnetSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public ControlnetSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public double readValue(int cipClass, int instance, int attribute) throws IOException {
        writeFully(ControlnetCodec.encodeGetAttributeSingle(cipClass, instance, attribute));
        byte[] response = ControlnetCodec.readFully(in, 5);
        if ((response[0] & 0xFF) != ControlnetCodec.GET_ATTRIBUTE_SINGLE_REPLY) {
            throw new IOException("ControlNet expected Get_Attribute_Single reply, got "
                    + (response[0] & 0xFF));
        }
        return ControlnetCodec.decodeFloat(response, 1);
    }

    public void writeValue(int cipClass, int instance, int attribute, double value) throws IOException {
        writeFully(ControlnetCodec.encodeSetAttributeSingle(
                cipClass, instance, attribute, (float) value));
        byte[] ack = ControlnetCodec.readFully(in, 1);
        if ((ack[0] & 0xFF) != 0x90) {
            throw new IOException("ControlNet write rejected: service=" + (ack[0] & 0xFF));
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
