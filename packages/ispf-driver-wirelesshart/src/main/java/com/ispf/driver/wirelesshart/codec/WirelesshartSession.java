package com.ispf.driver.wirelesshart.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Synchronous HART-IP TCP session to a WirelessHART gateway (initiate + pass-through).
 */
public final class WirelesshartSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final AtomicInteger sequence = new AtomicInteger(1);

    public WirelesshartSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        initiate();
    }

    private void initiate() throws IOException {
        int seq = sequence.getAndIncrement();
        writeFully(WirelesshartCodec.encodeSessionInitiate(seq));
        WirelesshartCodec.Message response = WirelesshartCodec.decode(readFrame());
        if (response.messageType() == WirelesshartCodec.MSG_NAK) {
            throw new IOException("HART-IP session initiate NAK status=" + response.status());
        }
        if (response.messageId() != WirelesshartCodec.ID_SESSION_INITIATE
                || response.messageType() != WirelesshartCodec.MSG_RESPONSE) {
            throw new IOException("HART-IP expected session initiate response");
        }
    }

    public float readPrimaryVariable(int deviceAddress, int command) throws IOException {
        int seq = sequence.getAndIncrement();
        byte[] hartRequest = WirelesshartCodec.encodeHartCommand(deviceAddress, command);
        writeFully(WirelesshartCodec.encodePassThroughRequest(seq, hartRequest));
        WirelesshartCodec.Message response = WirelesshartCodec.decode(readFrame());
        if (response.messageType() == WirelesshartCodec.MSG_NAK) {
            throw new IOException("HART-IP pass-through NAK status=" + response.status());
        }
        if (response.messageId() != WirelesshartCodec.ID_PASS_THROUGH) {
            throw new IOException("HART-IP expected pass-through response, id=" + response.messageId());
        }
        return WirelesshartCodec.extractPv(response.body());
    }

    public void writeValue(int deviceAddress, int command, float value) throws IOException {
        int seq = sequence.getAndIncrement();
        byte[] hartRequest = WirelesshartCodec.encodeHartWrite(deviceAddress, command, value);
        writeFully(WirelesshartCodec.encodePassThroughRequest(seq, hartRequest));
        WirelesshartCodec.Message response = WirelesshartCodec.decode(readFrame());
        if (response.messageType() == WirelesshartCodec.MSG_NAK) {
            throw new IOException("HART-IP write NAK status=" + response.status());
        }
        if (response.messageId() != WirelesshartCodec.ID_PASS_THROUGH) {
            throw new IOException("HART-IP expected pass-through write response, id=" + response.messageId());
        }
    }

    private void writeFully(byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }

    private byte[] readFrame() throws IOException {
        byte[] header = readFully(WirelesshartCodec.HEADER_LENGTH);
        int byteCount = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
        byte[] payload = byteCount == 0 ? new byte[0] : readFully(byteCount);
        byte[] frame = new byte[WirelesshartCodec.HEADER_LENGTH + payload.length];
        System.arraycopy(header, 0, frame, 0, WirelesshartCodec.HEADER_LENGTH);
        System.arraycopy(payload, 0, frame, WirelesshartCodec.HEADER_LENGTH, payload.length);
        return frame;
    }

    private byte[] readFully(int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new EOFException("EOF reading HART-IP frame");
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
