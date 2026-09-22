package com.ispf.driver.ethercat.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP session carrying one or more EtherCAT datagrams per request (serial/raw gateway).
 */
public final class EthercatSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public EthercatSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public int readLogicalUint16(int idx, int ado) throws IOException {
        byte[] request = EthercatCodec.buildDatagram(
                EthercatCodec.CMD_LRD, idx, 0, ado, false, 0, new int[] { 0, 0 }, 0);
        EthercatCodec.ParsedDatagram response = transact(request);
        if (response.workingCounter() < 1) {
            throw new IOException("EtherCAT LRD working counter is 0");
        }
        return EthercatCodec.readUint16Le(response.data());
    }

    public void writeLogicalUint16(int idx, int ado, int value) throws IOException {
        byte[] request = EthercatCodec.buildDatagram(
                EthercatCodec.CMD_LWR, idx, 0, ado, false, 0,
                EthercatCodec.uint16LeBytes(value), 0);
        EthercatCodec.ParsedDatagram response = transact(request);
        if (response.workingCounter() < 1) {
            throw new IOException("EtherCAT LWR working counter is 0");
        }
    }

    private EthercatCodec.ParsedDatagram transact(byte[] request) throws IOException {
        out.write(request);
        out.flush();
        byte[] header = readFully(10);
        int lengthField = (header[6] & 0xFF) | ((header[7] & 0xFF) << 8);
        int dataLen = lengthField & 0x7FF;
        byte[] rest = readFully(dataLen + 2);
        byte[] frame = new byte[10 + rest.length];
        System.arraycopy(header, 0, frame, 0, 10);
        System.arraycopy(rest, 0, frame, 10, rest.length);
        return EthercatCodec.parse(frame);
    }

    private byte[] readFully(int length) throws IOException {
        byte[] buf = new byte[length];
        int off = 0;
        while (off < length) {
            int n = in.read(buf, off, length - off);
            if (n < 0) {
                throw new EOFException("EOF reading EtherCAT datagram");
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
