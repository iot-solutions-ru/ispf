package com.ispf.driver.wisun.codec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;

/**
 * UDP CoAP session for Wi-SUN border-router style hosts (RFC 7252 CON GET).
 * <p>
 * Not Wi-SUN FAN PHY / FAN stack. JDK {@link DatagramSocket} only.
 */
public final class WisunCoapSession implements AutoCloseable {

    private final DatagramSocket socket;
    private final InetSocketAddress peer;

    public WisunCoapSession(String host, int port, int timeoutMs) throws IOException {
        DatagramSocket next = new DatagramSocket();
        next.setSoTimeout(timeoutMs);
        socket = next;
        peer = new InetSocketAddress(InetAddress.getByName(host), port);
    }

    /**
     * Sends CON GET with no token and MID 1, expects ACK 2.05 Content with the same MID.
     */
    public int getMid1() throws IOException {
        byte[] request = WisunCoapCodec.encodeConGetNoToken(1);
        socket.send(new DatagramPacket(request, request.length, peer));
        byte[] buffer = new byte[1500];
        DatagramPacket inbound = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(inbound);
        } catch (SocketTimeoutException e) {
            throw new IOException("CoAP GET timeout for " + peer, e);
        }
        byte[] reply = new byte[inbound.getLength()];
        System.arraycopy(inbound.getData(), inbound.getOffset(), reply, 0, inbound.getLength());
        int mid = WisunCoapCodec.parseAckContentMessageId(reply);
        if (mid != 1) {
            throw new IOException("CoAP message id mismatch: expected 1 got " + mid);
        }
        return mid;
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    @Override
    public void close() {
        socket.close();
    }
}
