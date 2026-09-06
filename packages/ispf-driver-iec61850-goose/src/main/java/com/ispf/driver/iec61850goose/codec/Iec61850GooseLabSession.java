package com.ispf.driver.iec61850goose.codec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * UDP session for the IEC 61850 GOOSE subscribe/publish lab
 * (not VLAN/priority tagging / full ASN.1 GOOSE stack).
 * <p>
 * Datagram dialect (lab on UDP — default port 8502; 102 also accepted via config):
 * <pre>
 *   GET goose:gcb1       →  VALUE 1.0
 *   SET goID:MyGo 2.5    →  OK
 * </pre>
 */
public final class Iec61850GooseLabSession implements AutoCloseable {

    private final DatagramSocket socket;
    private final InetSocketAddress remote;
    private final int timeoutMs;

    public Iec61850GooseLabSession(String host, int port, int timeoutMs) throws IOException {
        this.timeoutMs = timeoutMs;
        remote = new InetSocketAddress(InetAddress.getByName(host), port);
        DatagramSocket next = new DatagramSocket();
        next.setSoTimeout(timeoutMs);
        next.connect(remote);
        socket = next;
    }

    public double readValue(String wireToken) throws IOException {
        String response = transact("GET " + wireToken);
        return parseValueResponse(response);
    }

    public void writeValue(String wireToken, double value) throws IOException {
        String response = transact("SET " + wireToken + " " + value);
        if (!response.trim().toUpperCase(Locale.ROOT).startsWith("OK")) {
            throw new IOException("IEC 61850 GOOSE-lab SET rejected: " + response);
        }
    }

    private String transact(String command) throws IOException {
        byte[] payload = command.getBytes(StandardCharsets.US_ASCII);
        socket.send(new DatagramPacket(payload, payload.length));
        byte[] buf = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        return new String(packet.getData(), 0, packet.getLength(), StandardCharsets.US_ASCII);
    }

    static double parseValueResponse(String response) throws IOException {
        String trimmed = response == null ? "" : response.trim();
        if (trimmed.isEmpty()) {
            throw new IOException("Empty IEC 61850 GOOSE-lab response");
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ERR")) {
            throw new IOException("IEC 61850 GOOSE-lab GET rejected: " + response);
        }
        if (upper.startsWith("VALUE")) {
            trimmed = trimmed.substring(5).trim();
        } else if (upper.startsWith("OK") && trimmed.length() > 2) {
            trimmed = trimmed.substring(2).trim();
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            throw new IOException("IEC 61850 GOOSE-lab non-numeric value: " + response, e);
        }
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
