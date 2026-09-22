package com.ispf.driver.ethernetpowerlink.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP session for Ethernet POWERLINK basic frames over a gateway. Not a hard real-time MN.
 */
public final class EthernetPowerlinkSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public EthernetPowerlinkSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public double readValue(int destination) throws IOException {
        writeFully(EthernetPowerlinkCodec.encodePReq(
                destination, EthernetPowerlinkCodec.MN_NODE, new byte[4]));
        byte[] response = EthernetPowerlinkCodec.readFully(in, 7);
        if ((response[0] & 0xFF) != EthernetPowerlinkCodec.PRES) {
            throw new IOException("POWERLINK expected PRes, got " + (response[0] & 0xFF));
        }
        return EthernetPowerlinkCodec.decodeFloat(response, 3);
    }

    public void writeValue(int destination, double value) throws IOException {
        writeFully(EthernetPowerlinkCodec.encodePReq(
                destination,
                EthernetPowerlinkCodec.MN_NODE,
                EthernetPowerlinkCodec.encodeFloat((float) value)));
        byte[] ack = EthernetPowerlinkCodec.readFully(in, 1);
        if ((ack[0] & 0xFF) != 0x00) {
            throw new IOException("POWERLINK write rejected: status=" + (ack[0] & 0xFF));
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
