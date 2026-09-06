package com.ispf.driver.azureiothub;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/**
 * Minimal MQTT 3.1.1 client over plain TCP for lab loopback tests.
 * Supports CONNECT/CONNACK, SUBSCRIBE/SUBACK, PUBLISH/PUBACK (QoS 0–1), PING, DISCONNECT.
 */
final class Mqtt311Lab implements AutoCloseable {

    static final byte TYPE_CONNECT = 1;
    static final byte TYPE_CONNACK = 2;
    static final byte TYPE_PUBLISH = 3;
    static final byte TYPE_PUBACK = 4;
    static final byte TYPE_SUBSCRIBE = 8;
    static final byte TYPE_SUBACK = 9;
    static final byte TYPE_PINGREQ = 12;
    static final byte TYPE_PINGRESP = 13;
    static final byte TYPE_DISCONNECT = 14;

    private final String host;
    private final int port;
    private final int timeoutMs;
    private final String clientId;

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Thread reader;
    private volatile boolean running;
    private volatile boolean connected;

    private final AtomicInteger nextPacketId = new AtomicInteger(1);
    private final Object writeLock = new Object();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition signal = lock.newCondition();

    private final Map<Integer, byte[]> pendingAcks = new ConcurrentHashMap<>();
    private final Map<String, String> lastPayloads = new ConcurrentHashMap<>();
    private final List<BiConsumer<String, String>> listeners = new CopyOnWriteArrayList<>();
    private volatile Integer pendingConnack;
    private volatile Integer pendingSubackId;
    private volatile byte[] pendingSubackCodes;

    Mqtt311Lab(String host, int port, int timeoutMs, String clientId) {
        this.host = host;
        this.port = port;
        this.timeoutMs = Math.max(timeoutMs, 1);
        this.clientId = clientId;
    }

    void addListener(BiConsumer<String, String> listener) {
        listeners.add(listener);
    }

    String lastPayload(String topic) {
        return lastPayloads.get(topic);
    }

    Map<String, String> lastPayloads() {
        return lastPayloads;
    }

    boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    void connect() throws IOException {
        close();
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(0);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        running = true;
        reader = new Thread(this::readLoop, "mqtt311-lab-" + clientId);
        reader.setDaemon(true);
        reader.start();

        writePacket(encodeConnect(clientId));
        int rc = awaitConnack(timeoutMs);
        if (rc != 0) {
            close();
            throw new IOException("MQTT CONNACK rejected rc=" + rc);
        }
        connected = true;
    }

    void subscribe(String topicFilter, int qos) throws IOException {
        int packetId = nextPacketId();
        writePacket(encodeSubscribe(topicFilter, packetId, qos));
        awaitSuback(packetId, timeoutMs);
    }

    void publish(String topic, String payload, int qos) throws IOException {
        int packetId = qos > 0 ? nextPacketId() : 0;
        writePacket(encodePublish(topic, payload.getBytes(StandardCharsets.UTF_8), packetId, qos));
        if (qos > 0) {
            awaitPuback(packetId, timeoutMs);
        }
        lastPayloads.put(topic, payload);
    }

    String awaitPayload(String topic, long waitMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(waitMs, 1));
        lock.lock();
        try {
            while (true) {
                String value = lastPayloads.get(topic);
                if (value != null) {
                    return value;
                }
                // also match wildcard deliveries recorded under exact topic by listeners
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return lastPayloads.getOrDefault(topic, "");
                }
                signal.awaitNanos(remaining);
            }
        } finally {
            lock.unlock();
        }
    }

    String awaitMatching(java.util.function.Predicate<String> topicMatch, long waitMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(waitMs, 1));
        lock.lock();
        try {
            while (true) {
                for (Map.Entry<String, String> e : lastPayloads.entrySet()) {
                    if (topicMatch.test(e.getKey())) {
                        return e.getValue();
                    }
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    for (Map.Entry<String, String> e : lastPayloads.entrySet()) {
                        if (topicMatch.test(e.getKey())) {
                            return e.getValue();
                        }
                    }
                    return "";
                }
                signal.awaitNanos(remaining);
            }
        } finally {
            lock.unlock();
        }
    }

    private void readLoop() {
        try {
            while (running && in != null) {
                FixedHeader header = readFixedHeader(in);
                if (header == null) {
                    return;
                }
                byte[] body = in.readNBytes(header.remainingLength);
                if (body.length < header.remainingLength) {
                    return;
                }
                handlePacket(header.type, header.flags, body);
            }
        } catch (IOException ignored) {
            // closed
        } finally {
            connected = false;
            lock.lock();
            try {
                signal.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    private void handlePacket(int type, int flags, byte[] body) throws IOException {
        switch (type) {
            case TYPE_CONNACK -> {
                int rc = body.length >= 2 ? (body[1] & 0xFF) : 1;
                lock.lock();
                try {
                    pendingConnack = rc;
                    signal.signalAll();
                } finally {
                    lock.unlock();
                }
            }
            case TYPE_SUBACK -> {
                if (body.length < 2) {
                    return;
                }
                int packetId = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
                byte[] codes = new byte[Math.max(0, body.length - 2)];
                if (codes.length > 0) {
                    System.arraycopy(body, 2, codes, 0, codes.length);
                }
                lock.lock();
                try {
                    pendingSubackId = packetId;
                    pendingSubackCodes = codes;
                    signal.signalAll();
                } finally {
                    lock.unlock();
                }
            }
            case TYPE_PUBACK -> {
                if (body.length < 2) {
                    return;
                }
                int packetId = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
                lock.lock();
                try {
                    pendingAcks.put(packetId, body);
                    signal.signalAll();
                } finally {
                    lock.unlock();
                }
            }
            case TYPE_PUBLISH -> handlePublish(flags, body);
            case TYPE_PINGREQ -> writePacket(encodeSimple(TYPE_PINGRESP, 0));
            case TYPE_PINGRESP, TYPE_DISCONNECT -> {
                // ignore
            }
            default -> {
                // ignore unknown
            }
        }
    }

    private void handlePublish(int flags, byte[] body) throws IOException {
        if (body.length < 2) {
            return;
        }
        int topicLen = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
        if (body.length < 2 + topicLen) {
            return;
        }
        String topic = new String(body, 2, topicLen, StandardCharsets.UTF_8);
        int qos = (flags >> 1) & 0x03;
        int offset = 2 + topicLen;
        int packetId = 0;
        if (qos > 0) {
            if (body.length < offset + 2) {
                return;
            }
            packetId = ((body[offset] & 0xFF) << 8) | (body[offset + 1] & 0xFF);
            offset += 2;
        }
        String payload = new String(body, offset, body.length - offset, StandardCharsets.UTF_8);
        lock.lock();
        try {
            lastPayloads.put(topic, payload);
            signal.signalAll();
        } finally {
            lock.unlock();
        }
        for (BiConsumer<String, String> listener : listeners) {
            listener.accept(topic, payload);
        }
        if (qos == 1) {
            writePacket(encodePuback(packetId));
        }
    }

    private int awaitConnack(long waitMs) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMs);
        lock.lock();
        try {
            while (pendingConnack == null) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IOException("MQTT timeout waiting for CONNACK");
                }
                try {
                    signal.awaitNanos(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for CONNACK", e);
                }
            }
            int rc = pendingConnack;
            pendingConnack = null;
            return rc;
        } finally {
            lock.unlock();
        }
    }

    private void awaitSuback(int packetId, long waitMs) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMs);
        lock.lock();
        try {
            while (pendingSubackId == null || pendingSubackId != packetId) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IOException("MQTT timeout waiting for SUBACK id=" + packetId);
                }
                try {
                    signal.awaitNanos(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for SUBACK", e);
                }
            }
            byte[] codes = pendingSubackCodes;
            pendingSubackId = null;
            pendingSubackCodes = null;
            if (codes != null) {
                for (byte code : codes) {
                    if ((code & 0xFF) == 0x80) {
                        throw new IOException("MQTT SUBACK failure");
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void awaitPuback(int packetId, long waitMs) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMs);
        lock.lock();
        try {
            while (!pendingAcks.containsKey(packetId)) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IOException("MQTT timeout waiting for PUBACK id=" + packetId);
                }
                try {
                    signal.awaitNanos(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for PUBACK", e);
                }
            }
            pendingAcks.remove(packetId);
        } finally {
            lock.unlock();
        }
    }

    private void writePacket(byte[] packet) throws IOException {
        synchronized (writeLock) {
            if (out == null) {
                throw new IOException("Not connected");
            }
            out.write(packet);
            out.flush();
        }
    }

    private int nextPacketId() {
        return nextPacketId.getAndUpdate(v -> v >= 0xFFFF ? 1 : v + 1) & 0xFFFF;
    }

    @Override
    public void close() {
        connected = false;
        running = false;
        if (socket != null) {
            try {
                if (out != null) {
                    out.write(encodeSimple(TYPE_DISCONNECT, 0));
                    out.flush();
                }
            } catch (IOException ignored) {
                // best-effort
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
        if (reader != null) {
            try {
                reader.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reader = null;
        }
        socket = null;
        in = null;
        out = null;
        pendingAcks.clear();
        pendingConnack = null;
        pendingSubackId = null;
        pendingSubackCodes = null;
    }

    // --- encode / decode helpers (also used by fake broker in tests) ---

    static byte[] encodeConnect(String clientId) {
        byte[] proto = encodeUtf8("MQTT");
        byte[] id = encodeUtf8(clientId);
        ByteArrayOutputStream vh = new ByteArrayOutputStream();
        vh.writeBytes(proto);
        vh.write(4); // protocol level 3.1.1
        vh.write(0x02); // clean session
        vh.write(0);
        vh.write(60); // keep alive
        vh.writeBytes(id);
        return wrap(TYPE_CONNECT, 0, vh.toByteArray());
    }

    static byte[] encodeSubscribe(String topic, int packetId, int qos) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write((packetId >> 8) & 0xFF);
        body.write(packetId & 0xFF);
        body.writeBytes(encodeUtf8(topic));
        body.write(qos & 0x03);
        return wrap(TYPE_SUBSCRIBE, 0x02, body.toByteArray());
    }

    static byte[] encodePublish(String topic, byte[] payload, int packetId, int qos) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(encodeUtf8(topic));
        if (qos > 0) {
            body.write((packetId >> 8) & 0xFF);
            body.write(packetId & 0xFF);
        }
        body.writeBytes(payload);
        int flags = (qos & 0x03) << 1;
        return wrap(TYPE_PUBLISH, flags, body.toByteArray());
    }

    static byte[] encodePuback(int packetId) {
        return wrap(TYPE_PUBACK, 0, new byte[]{
                (byte) ((packetId >> 8) & 0xFF),
                (byte) (packetId & 0xFF)
        });
    }

    static byte[] encodeConnack(int returnCode) {
        return wrap(TYPE_CONNACK, 0, new byte[]{0, (byte) returnCode});
    }

    static byte[] encodeSuback(int packetId, int... codes) {
        byte[] body = new byte[2 + codes.length];
        body[0] = (byte) ((packetId >> 8) & 0xFF);
        body[1] = (byte) (packetId & 0xFF);
        for (int i = 0; i < codes.length; i++) {
            body[2 + i] = (byte) codes[i];
        }
        return wrap(TYPE_SUBACK, 0, body);
    }

    static byte[] encodeSimple(int type, int flags) {
        return wrap(type, flags, new byte[0]);
    }

    static byte[] wrap(int type, int flags, byte[] body) {
        byte[] rl = encodeRemainingLength(body.length);
        byte[] packet = new byte[1 + rl.length + body.length];
        packet[0] = (byte) (((type & 0x0F) << 4) | (flags & 0x0F));
        System.arraycopy(rl, 0, packet, 1, rl.length);
        System.arraycopy(body, 0, packet, 1 + rl.length, body.length);
        return packet;
    }

    static byte[] encodeUtf8(String value) {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[2 + data.length];
        out[0] = (byte) ((data.length >> 8) & 0xFF);
        out[1] = (byte) (data.length & 0xFF);
        System.arraycopy(data, 0, out, 2, data.length);
        return out;
    }

    static byte[] encodeRemainingLength(int length) {
        List<Byte> digits = new ArrayList<>(4);
        int x = length;
        do {
            int digit = x % 128;
            x /= 128;
            if (x > 0) {
                digit |= 0x80;
            }
            digits.add((byte) digit);
        } while (x > 0);
        byte[] out = new byte[digits.size()];
        for (int i = 0; i < digits.size(); i++) {
            out[i] = digits.get(i);
        }
        return out;
    }

    static FixedHeader readFixedHeader(InputStream in) throws IOException {
        int first = in.read();
        if (first < 0) {
            return null;
        }
        int type = (first >> 4) & 0x0F;
        int flags = first & 0x0F;
        int multiplier = 1;
        int remaining = 0;
        for (int i = 0; i < 4; i++) {
            int digit = in.read();
            if (digit < 0) {
                throw new IOException("Truncated remaining length");
            }
            remaining += (digit & 0x7F) * multiplier;
            if ((digit & 0x80) == 0) {
                return new FixedHeader(type, flags, remaining);
            }
            multiplier *= 128;
        }
        throw new IOException("Malformed remaining length");
    }

    record FixedHeader(int type, int flags, int remainingLength) {
    }

    record ParsedPublish(String topic, int packetId, int qos, byte[] payload) {
    }

    static ParsedPublish parsePublish(int flags, byte[] body) {
        int topicLen = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
        String topic = new String(body, 2, topicLen, StandardCharsets.UTF_8);
        int qos = (flags >> 1) & 0x03;
        int offset = 2 + topicLen;
        int packetId = 0;
        if (qos > 0) {
            packetId = ((body[offset] & 0xFF) << 8) | (body[offset + 1] & 0xFF);
            offset += 2;
        }
        byte[] payload = new byte[body.length - offset];
        System.arraycopy(body, offset, payload, 0, payload.length);
        return new ParsedPublish(topic, packetId, qos, payload);
    }

    static String parseConnectClientId(byte[] body) {
        // skip protocol name + level + flags + keepalive
        int offset = 0;
        int protoLen = ((body[offset] & 0xFF) << 8) | (body[offset + 1] & 0xFF);
        offset += 2 + protoLen + 1 + 1 + 2;
        int idLen = ((body[offset] & 0xFF) << 8) | (body[offset + 1] & 0xFF);
        return new String(body, offset + 2, idLen, StandardCharsets.UTF_8);
    }

    static String parseSubscribeTopic(byte[] body) {
        // packet id + topic + qos
        int offset = 2;
        int topicLen = ((body[offset] & 0xFF) << 8) | (body[offset + 1] & 0xFF);
        return new String(body, offset + 2, topicLen, StandardCharsets.UTF_8);
    }

    static int parsePacketId(byte[] body) {
        return ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
    }
}
