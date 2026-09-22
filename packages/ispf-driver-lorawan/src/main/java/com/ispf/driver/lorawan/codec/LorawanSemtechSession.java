package com.ispf.driver.lorawan.codec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Network-server side Semtech UDP session: binds a {@link DatagramSocket}, acknowledges
 * {@code PUSH_DATA}/{@code PULL_DATA}, caches uplinks by DevEUI, and sends {@code PULL_RESP}
 * downlinks to the last gateway that pulled.
 */
public final class LorawanSemtechSession implements AutoCloseable {

    private final DatagramSocket socket;
    private final int timeoutMs;
    private final Map<String, LorawanSemtechCodec.Uplink> uplinks = new ConcurrentHashMap<>();
    private final AtomicInteger downlinkToken = new AtomicInteger(1);
    private volatile SocketAddress lastGateway;

    public LorawanSemtechSession(String host, int port, int timeoutMs) throws IOException {
        this.timeoutMs = timeoutMs;
        socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(host, port));
        socket.setSoTimeout(timeoutMs);
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    public int localPort() {
        return socket.getLocalPort();
    }

    /**
     * Receive datagrams until an uplink for {@code deveui} is available (or timeout).
     */
    public LorawanSemtechCodec.Uplink readUplink(String deveui) throws IOException {
        String key = deveui.toUpperCase(Locale.ROOT);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            LorawanSemtechCodec.Uplink cached = uplinks.get(key);
            if (cached != null) {
                return cached;
            }
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new SocketTimeoutException("No PUSH_DATA uplink for DevEUI " + key);
            }
            socket.setSoTimeout((int) Math.min(remaining, timeoutMs));
            receiveOnce();
        }
    }

    public void writeValue(String deveui, float value) throws IOException {
        SocketAddress gateway = lastGateway;
        if (gateway == null) {
            // Allow write after at least one exchange by draining briefly for PULL_DATA.
            long deadline = System.currentTimeMillis() + Math.min(timeoutMs, 500);
            while (lastGateway == null && System.currentTimeMillis() < deadline) {
                try {
                    receiveOnce();
                } catch (SocketTimeoutException ignored) {
                    break;
                }
            }
            gateway = lastGateway;
        }
        if (gateway == null) {
            throw new IOException("No gateway address for PULL_RESP (await PULL_DATA first)");
        }
        int token = downlinkToken.getAndUpdate(v -> v == 0xFFFF ? 1 : v + 1);
        String json = LorawanSemtechCodec.downlinkJson(deveui.toUpperCase(Locale.ROOT), value);
        byte[] frame = LorawanSemtechCodec.encodePullResp(token, json);
        socket.send(new DatagramPacket(frame, frame.length, gateway));
    }

    private void receiveOnce() throws IOException {
        byte[] buf = new byte[65535];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        byte[] datagram = Arrays.copyOf(packet.getData(), packet.getLength());
        LorawanSemtechCodec.SemtechPacket parsed = LorawanSemtechCodec.decode(datagram);
        lastGateway = packet.getSocketAddress();
        switch (parsed.identifier()) {
            case LorawanSemtechCodec.PUSH_DATA -> {
                LorawanSemtechCodec.Uplink uplink = LorawanSemtechCodec.parseUplinkJson(parsed.json());
                String deveui = uplink.deveui().isBlank() ? "" : uplink.deveui();
                if (!deveui.isBlank()) {
                    uplinks.put(deveui, uplink);
                }
                byte[] ack = LorawanSemtechCodec.encodePushAck(parsed.token());
                socket.send(new DatagramPacket(ack, ack.length, packet.getSocketAddress()));
            }
            case LorawanSemtechCodec.PULL_DATA -> {
                byte[] ack = LorawanSemtechCodec.encodePullAck(parsed.token());
                socket.send(new DatagramPacket(ack, ack.length, packet.getSocketAddress()));
            }
            default -> {
                // ignore unexpected identifiers from peer
            }
        }
    }

    @Override
    public void close() {
        socket.close();
        uplinks.clear();
        lastGateway = null;
    }
}
