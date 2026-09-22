package com.ispf.driver.devicenet.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP session for DeviceNet CIP explicit messaging. Not the DeviceNet CAN PHY.
 */
public final class DeviceNetSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public DeviceNetSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public double readValue(int cipClass, int instance, int attribute) throws IOException {
        writeFully(DeviceNetCodec.encodeGetAttributeSingle(cipClass, instance, attribute));
        byte[] response = DeviceNetCodec.readFully(in, 5);
        if ((response[0] & 0xFF) != DeviceNetCodec.GET_ATTRIBUTE_SINGLE_REPLY) {
            throw new IOException("DeviceNet expected Get_Attribute_Single reply, got "
                    + (response[0] & 0xFF));
        }
        return DeviceNetCodec.decodeFloat(response, 1);
    }

    public void writeValue(int cipClass, int instance, int attribute, double value) throws IOException {
        writeFully(DeviceNetCodec.encodeSetAttributeSingle(
                cipClass, instance, attribute, (float) value));
        byte[] ack = DeviceNetCodec.readFully(in, 1);
        if ((ack[0] & 0xFF) != 0x90) {
            throw new IOException("DeviceNet write rejected: service=" + (ack[0] & 0xFF));
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
