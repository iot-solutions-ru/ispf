package com.ispf.driver.ethernetpowerlink.codec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * UDP session for the Ethernet POWERLINK MN/CN lab subset
 * (not full EPSG POWERLINK MN with hard real-time).
 * <p>
 * Datagram dialect (lab on UDP — default port 6040; 3000 also accepted via config):
 * <pre>
 *   GET pdo:1                  →  VALUE 12.5
 *   SET node:1:obj:0x6000:01 7 →  OK
 * </pre>
 */
public final class EthernetPowerlinkLabSession implements AutoCloseable {

    private final DatagramSocket socket;
    private final InetSocketAddress remote;
    private final int timeoutMs;

    public EthernetPowerlinkLabSession(String host, int port, int timeoutMs) throws IOException {
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
            throw new IOException("Ethernet POWERLINK lab SET rejected: " + response);
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
            throw new IOException("Empty Ethernet POWERLINK lab response");
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ERR")) {
            throw new IOException("Ethernet POWERLINK lab GET rejected: " + response);
        }
        if (upper.startsWith("VALUE")) {
            trimmed = trimmed.substring(5).trim();
        } else if (upper.startsWith("OK") && trimmed.length() > 2) {
            trimmed = trimmed.substring(2).trim();
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            throw new IOException("Ethernet POWERLINK lab non-numeric value: " + response, e);
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
