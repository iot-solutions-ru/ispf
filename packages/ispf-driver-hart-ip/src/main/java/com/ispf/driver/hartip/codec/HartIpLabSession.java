package com.ispf.driver.hartip.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Synchronous HART-IP TCP lab session (initiate + pass-through reads).
 */
public final class HartIpLabSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final AtomicInteger sequence = new AtomicInteger(1);

    public HartIpLabSession(String host, int port, int timeoutMs) throws IOException {
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
        writeFully(HartIpLabCodec.encodeSessionInitiate(seq));
        HartIpLabCodec.HartIpMessage response = HartIpLabCodec.decode(readFrame());
        if (response.messageType() == HartIpLabCodec.MSG_NAK) {
            throw new IOException("HART-IP session initiate NAK status=" + response.status());
        }
        if (response.messageId() != HartIpLabCodec.ID_SESSION_INITIATE
                || response.messageType() != HartIpLabCodec.MSG_RESPONSE) {
            throw new IOException("HART-IP expected session initiate response");
        }
    }

    public float readPrimaryVariable(int deviceAddress, int command) throws IOException {
        int seq = sequence.getAndIncrement();
        byte[] hartRequest = HartIpLabCodec.encodeHartCommand(deviceAddress, command);
        writeFully(HartIpLabCodec.encodePassThroughRequest(seq, hartRequest));
        HartIpLabCodec.HartIpMessage response = HartIpLabCodec.decode(readFrame());
        if (response.messageType() == HartIpLabCodec.MSG_NAK) {
            throw new IOException("HART-IP pass-through NAK status=" + response.status());
        }
        if (response.messageId() != HartIpLabCodec.ID_PASS_THROUGH) {
            throw new IOException("HART-IP expected pass-through response, id=" + response.messageId());
        }
        return HartIpLabCodec.extractPv(response.payload());
    }

    private void writeFully(byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }

    private byte[] readFrame() throws IOException {
        byte[] header = readFully(10);
        int byteCount = ((header[8] & 0xFF) << 8) | (header[9] & 0xFF);
        byte[] payload = byteCount == 0 ? new byte[0] : readFully(byteCount);
        byte[] frame = new byte[10 + payload.length];
        System.arraycopy(header, 0, frame, 0, 10);
        System.arraycopy(payload, 0, frame, 10, payload.length);
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
