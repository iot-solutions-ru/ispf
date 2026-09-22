package com.ispf.driver.iec61850sv.codec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;

/**
 * UDP session carrying IEC 61850-9-2 SAV headers (APPID/Length/Reserved + ASDU).
 * Point names map onto float values inside the ASDU.
 */
public final class Iec61850SvSession implements AutoCloseable {

    private final DatagramSocket socket;
    private final InetSocketAddress remote;
    private final int timeoutMs;

    public Iec61850SvSession(String host, int port, int timeoutMs) throws IOException {
        this.timeoutMs = timeoutMs;
        remote = new InetSocketAddress(InetAddress.getByName(host), port);
        DatagramSocket next = new DatagramSocket();
        next.setSoTimeout(timeoutMs);
        next.connect(remote);
        socket = next;
    }

    public double readValue(String wireToken) throws IOException {
        byte[] request = Iec61850SvCodec.encodeReadRequest(wireToken);
        byte[] reply = transact(request);
        return Iec61850SvCodec.decodeFloatAsdu(reply);
    }

    public void writeValue(String wireToken, double value) throws IOException {
        byte[] request = Iec61850SvCodec.encodeWriteRequest(wireToken, (float) value);
        byte[] reply = transact(request);
        byte[] asdu = Iec61850SvCodec.asdu(reply);
        if (asdu.length < 1 || asdu[0] != Iec61850SvCodec.OP_ACK) {
            throw new IOException("IEC 61850 SV write was not acknowledged");
        }
    }

    private byte[] transact(byte[] request) throws IOException {
        socket.send(new DatagramPacket(request, request.length));
        byte[] buf = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        return Arrays.copyOf(packet.getData(), packet.getLength());
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    public int timeoutMs() {
        return timeoutMs;
    }

    @Override
    public void close() {
        socket.close();
    }
}
