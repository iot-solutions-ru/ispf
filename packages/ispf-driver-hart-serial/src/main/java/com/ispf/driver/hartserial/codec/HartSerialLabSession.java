package com.ispf.driver.hartserial.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Synchronous HART serial-gateway TCP lab session (length-prefixed HART PDU pass-through reads).
 * <p>
 * Speaks to a TCP serial gateway lab — not an FSK modem.
 */
public final class HartSerialLabSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public HartSerialLabSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public float readPrimaryVariable(int deviceAddress, int command) throws IOException {
        byte[] hartRequest = HartSerialLabCodec.encodeHartCommand(deviceAddress, command);
        writeFully(HartSerialLabCodec.wrapPdu(hartRequest));
        byte[] responsePdu = HartSerialLabCodec.unwrapPdu(readFrame());
        return HartSerialLabCodec.extractPv(responsePdu);
    }

    private void writeFully(byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }

    private byte[] readFrame() throws IOException {
        byte[] header = readFully(2);
        int length = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
        byte[] payload = length == 0 ? new byte[0] : readFully(length);
        byte[] frame = new byte[2 + payload.length];
        System.arraycopy(header, 0, frame, 0, 2);
        System.arraycopy(payload, 0, frame, 2, payload.length);
        return frame;
    }

    private byte[] readFully(int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new EOFException("EOF reading HART serial-gateway frame");
            }
            offset += read;
        }
        return buffer;
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
