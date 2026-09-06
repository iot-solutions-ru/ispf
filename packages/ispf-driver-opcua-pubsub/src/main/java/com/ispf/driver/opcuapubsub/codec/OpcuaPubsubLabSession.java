package com.ispf.driver.opcuapubsub.codec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;

/**
 * UDP session for the OPC UA PubSub UADP-lab subset (request/response datagrams).
 * <p>
 * Not full OPC UA PubSub / MQTT / broker / security.
 */
public final class OpcuaPubsubLabSession implements AutoCloseable {

    private final DatagramSocket socket;
    private final InetSocketAddress remote;
    private final int timeoutMs;

    public OpcuaPubsubLabSession(String host, int port, int timeoutMs) throws IOException {
        this.timeoutMs = timeoutMs;
        remote = new InetSocketAddress(InetAddress.getByName(host), port);
        DatagramSocket next = new DatagramSocket();
        next.setSoTimeout(timeoutMs);
        next.connect(remote);
        socket = next;
    }

    public double readValue(String wireToken) throws IOException {
        send(OpcuaPubsubLabCodec.encodeGet(wireToken));
        OpcuaPubsubLabCodec.LabFrame sample = receiveExpecting(OpcuaPubsubLabCodec.MSG_SAMPLE);
        if (!wireToken.equalsIgnoreCase(sample.key())) {
            throw new IOException("UADP-lab SAMPLE key mismatch: " + sample.key());
        }
        return OpcuaPubsubLabCodec.decodeNumeric(sample);
    }

    public void writeValue(String wireToken, double value) throws IOException {
        send(OpcuaPubsubLabCodec.encodePublish(wireToken, value));
        OpcuaPubsubLabCodec.LabFrame ack = receiveExpecting(OpcuaPubsubLabCodec.MSG_ACK);
        if (!wireToken.equalsIgnoreCase(ack.key())) {
            throw new IOException("UADP-lab ACK key mismatch: " + ack.key());
        }
    }

    /** Publish a lab sample via real encode+send (same as writeValue; exposed for clarity). */
    public void publishSample(String wireToken, double value) throws IOException {
        writeValue(wireToken, value);
    }

    private void send(byte[] frame) throws IOException {
        DatagramPacket packet = new DatagramPacket(frame, frame.length);
        socket.send(packet);
    }

    private OpcuaPubsubLabCodec.LabFrame receiveExpecting(byte messageType) throws IOException {
        byte[] buf = new byte[65535];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        byte[] frame = Arrays.copyOf(packet.getData(), packet.getLength());
        OpcuaPubsubLabCodec.LabFrame parsed = OpcuaPubsubLabCodec.decode(frame);
        if (parsed.messageType() != messageType) {
            throw new IOException("UADP-lab expected msgType 0x"
                    + Integer.toHexString(messageType & 0xFF)
                    + " got 0x" + Integer.toHexString(parsed.messageType() & 0xFF));
        }
        return parsed;
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
