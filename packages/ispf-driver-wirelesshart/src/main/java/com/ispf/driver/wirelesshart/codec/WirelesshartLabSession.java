package com.ispf.driver.wirelesshart.codec;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * TCP session for the WirelessHART gateway lab (port 5094, cmd/PV style).
 * <p>
 * Not an 802.15.4 WirelessHART radio / HCF stack.
 */
public final class WirelesshartLabSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public WirelesshartLabSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public float readPrimaryVariable(int deviceAddress, int command) throws IOException {
        writeFully(WirelesshartLabCodec.encodeGet(deviceAddress, command));
        return WirelesshartLabCodec.parseOkValue(readLine());
    }

    public void writeValue(int deviceAddress, int command, float value) throws IOException {
        writeFully(WirelesshartLabCodec.encodeSet(deviceAddress, command, value));
        WirelesshartLabCodec.parseOkAck(readLine());
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
                    throw new EOFException("EOF reading WirelessHART lab line");
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
