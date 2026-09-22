package com.ispf.driver.iec61850.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * IEC 61850 MMS client session over TPKT (RFC 1006) + COTP on TCP.
 * Connect sends the fixed COTP CR and expects a COTP CC; reads and writes
 * exchange COTP DT units carrying definite-length BER VisibleString / FloatingPoint.
 */
public final class Iec61850Session implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public Iec61850Session(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        connectCotp();
    }

    private void connectCotp() throws IOException {
        writeFully(Iec61850Codec.connectionRequest());
        byte[] reply = Iec61850Codec.readTpkt(in);
        Iec61850Codec.requireConnectionConfirm(reply);
    }

    public double readValue(String wireToken) throws IOException {
        byte[] request = Iec61850Codec.encodeDataTransfer(
                Iec61850Codec.encodeVisibleString(wireToken));
        writeFully(request);
        byte[] tpkt = Iec61850Codec.readTpkt(in);
        byte[] user = Iec61850Codec.unwrapDataTransfer(tpkt);
        return Iec61850Codec.decodeFloatingPoint(user);
    }

    public void writeValue(String wireToken, double value) throws IOException {
        byte[] request = Iec61850Codec.encodeDataTransfer(
                Iec61850Codec.encodeWritePayload(wireToken, (float) value));
        writeFully(request);
        byte[] tpkt = Iec61850Codec.readTpkt(in);
        byte[] user = Iec61850Codec.unwrapDataTransfer(tpkt);
        String ack = Iec61850Codec.decodeVisibleString(user);
        if (!"OK".equals(ack)) {
            throw new IOException("IEC 61850 MMS write rejected: " + ack);
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
