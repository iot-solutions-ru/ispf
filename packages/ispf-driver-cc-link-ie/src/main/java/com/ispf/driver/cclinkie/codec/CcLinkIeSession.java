package com.ispf.driver.cclinkie.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP session for MELSEC SLMP 3E binary device access (CC-Link IE gateway path).
 */
public final class CcLinkIeSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final int networkNo;
    private final int pcNo;
    private final int ioNo;
    private final int stationNo;
    private final int monitoringTimer;

    public CcLinkIeSession(
            String host,
            int port,
            int timeoutMs,
            int networkNo,
            int pcNo,
            int ioNo,
            int stationNo,
            int monitoringTimer
    ) throws IOException {
        this.networkNo = networkNo;
        this.pcNo = pcNo;
        this.ioNo = ioNo;
        this.stationNo = stationNo;
        this.monitoringTimer = monitoringTimer;
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public int readWord(String deviceCode, int address) throws IOException {
        int code = Slmp3eCodec.deviceCodeByte(deviceCode);
        byte[] request = Slmp3eCodec.buildRequest(
                networkNo, pcNo, ioNo, stationNo, monitoringTimer,
                Slmp3eCodec.CMD_BATCH_READ, code, address, 1, null);
        byte[] payload = transact(request);
        ensureOk(payload);
        return Slmp3eCodec.extractWords(payload, 1)[0] & 0xFFFF;
    }

    public void writeWord(String deviceCode, int address, int word) throws IOException {
        int code = Slmp3eCodec.deviceCodeByte(deviceCode);
        byte[] request = Slmp3eCodec.buildRequest(
                networkNo, pcNo, ioNo, stationNo, monitoringTimer,
                Slmp3eCodec.CMD_BATCH_WRITE, code, address, 1, new int[] { word & 0xFFFF });
        byte[] payload = transact(request);
        ensureOk(payload);
    }

    private static void ensureOk(byte[] payload) throws IOException {
        int endCode = (payload[0] & 0xFF) | ((payload[1] & 0xFF) << 8);
        if (endCode != 0) {
            throw new IOException("SLMP end code 0x" + Integer.toHexString(endCode));
        }
    }

    private byte[] transact(byte[] request) throws IOException {
        out.write(request);
        out.flush();
        byte[] header = readFully(9);
        if ((header[0] & 0xFF) != 0xD0 || header[1] != 0x00) {
            throw new IOException("Unexpected SLMP response subheader");
        }
        int length = Slmp3eCodec.responseDataLength(header);
        byte[] payload = readFully(length);
        Slmp3eCodec.parseResponse(header, payload);
        return payload;
    }

    private byte[] readFully(int length) throws IOException {
        byte[] buf = new byte[length];
        int off = 0;
        while (off < length) {
            int n = in.read(buf, off, length - off);
            if (n < 0) {
                throw new EOFException("EOF reading SLMP frame");
            }
            off += n;
        }
        return buf;
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
