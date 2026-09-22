package com.ispf.driver.azureiothub;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * MQTT 3.1.1 client over TCP (CONNECT/CONNACK, SUBSCRIBE/SUBACK, PUBLISH/PUBACK, PING, DISCONNECT).
 * <p>
 * Packet codec only — TLS certificate paths are configuration on the driver; this class speaks
 * MQTT bytes on a plain socket and does not implement Azure IoT Hub SAS tokens or AMQP.
 */
final class Mqtt311Client implements AutoCloseable {

    static final int TYPE_CONNECT = 1;
    static final int TYPE_CONNACK = 2;
    static final int TYPE_PUBLISH = 3;
    static final int TYPE_PUBACK = 4;
    static final int TYPE_SUBSCRIBE = 8;
    static final int TYPE_SUBACK = 9;
    static final int TYPE_PINGREQ = 12;
    static final int TYPE_PINGRESP = 13;
    static final int TYPE_DISCONNECT = 14;

    /** Fixed header first byte for CONNECT (type 1, flags 0). */
    static final int CONNECT_FIXED_HEADER = 0x10;
    /** Successful CONNACK wire form: type 2, remaining length 2, accepted. */
    static final byte[] CONNACK_SUCCESS = new byte[]{0x20, 0x02, 0x00, 0x00};
    /** Fixed header first byte for PUBLISH QoS 0 (type 3, flags 0). */
    static final int PUBLISH_QOS0_FIXED_HEADER = 0x30;

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

    private final Map<Integer, Integer> pendingAcks = new ConcurrentHashMap<>();
    private final Map<String, String> lastPayloads = new ConcurrentHashMap<>();
    private final List<BiConsumer<String, String>> listeners = new CopyOnWriteArrayList<>();
    private volatile Integer pendingConnack;
    private volatile Integer pendingSubackId;
    private volatile int[] pendingSubackCodes;

    Mqtt311Client(String host, int port, int timeoutMs, String clientId) {
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
        reader = new Thread(this::readLoop, "mqtt311-" + clientId);
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

    String awaitMatching(Predicate<String> topicMatch, long waitMs) throws InterruptedException {
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
                handlePacket(header, body);
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

    private void handlePacket(FixedHeader header, byte[] body) throws IOException {
        switch (header.type) {
            case TYPE_CONNACK -> {
                int rc = isConnackSuccessPacket(header.firstByte(), header.remainingLength(), body)
                        ? 0
                        : parseConnackReturnCode(body);
                if (rc == 0 && !isConnackSuccessPacket(header.firstByte(), header.remainingLength(), body)) {
                    rc = 1;
                }
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
                int[] codes = new int[Math.max(0, body.length - 2)];
                for (int i = 0; i < codes.length; i++) {
                    codes[i] = body[2 + i] & 0xFF;
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
                    pendingAcks.put(packetId, packetId);
                    signal.signalAll();
                } finally {
                    lock.unlock();
                }
            }
            case TYPE_PUBLISH -> handlePublish(header.flags, body);
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
        ParsedPublish pub = parsePublish(flags, body);
        if (pub == null) {
            return;
        }
        lock.lock();
        try {
            lastPayloads.put(pub.topic(), pub.payloadText());
            signal.signalAll();
        } finally {
            lock.unlock();
        }
        for (BiConsumer<String, String> listener : listeners) {
            listener.accept(pub.topic(), pub.payloadText());
        }
        if (pub.qos() == 1) {
            writePacket(encodePuback(pub.packetId()));
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
            int[] codes = pendingSubackCodes;
            pendingSubackId = null;
            pendingSubackCodes = null;
            if (codes != null) {
                for (int code : codes) {
                    if (code == 0x80) {
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

    /**
     * MQTT 3.1.1 CONNECT: fixed header {@code 0x10}, remaining length, protocol name
     * {@code 00 04 4D 51 54 54}, level {@code 0x04}, connect flags, keepalive, client id string.
     */
    static byte[] encodeConnect(String clientId) {
        byte[] id = encodeUtf8(clientId == null ? "" : clientId);
        ByteArrayOutputStream vh = new ByteArrayOutputStream(10 + id.length);
        vh.write(0x00);
        vh.write(0x04);
        vh.write(0x4D); // M
        vh.write(0x51); // Q
        vh.write(0x54); // T
        vh.write(0x54); // T
        vh.write(0x04); // protocol level 3.1.1
        vh.write(0x02); // clean session
        vh.write(0x00); // keepalive MSB
        vh.write(60);   // keepalive LSB (60 s)
        vh.writeBytes(id);
        byte[] variable = vh.toByteArray();
        byte[] rl = encodeRemainingLength(variable.length);
        byte[] packet = new byte[1 + rl.length + variable.length];
        packet[0] = (byte) CONNECT_FIXED_HEADER;
        System.arraycopy(rl, 0, packet, 1, rl.length);
        System.arraycopy(variable, 0, packet, 1 + rl.length, variable.length);
        return packet;
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
        byte[] data = payload == null ? new byte[0] : payload;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(encodeUtf8(topic));
        if (qos > 0) {
            body.write((packetId >> 8) & 0xFF);
            body.write(packetId & 0xFF);
        }
        body.writeBytes(data);
        int flags = (qos & 0x03) << 1;
        byte[] packet = wrap(TYPE_PUBLISH, flags, body.toByteArray());
        if (qos == 0 && (packet[0] & 0xFF) != PUBLISH_QOS0_FIXED_HEADER) {
            throw new IllegalStateException("PUBLISH QoS0 fixed header must be 0x30");
        }
        return packet;
    }

    static byte[] encodePuback(int packetId) {
        return wrap(TYPE_PUBACK, 0, new byte[]{
                (byte) ((packetId >> 8) & 0xFF),
                (byte) (packetId & 0xFF)
        });
    }

    /** Successful CONNACK: {@code 20 02 00 00}. */
    static byte[] encodeConnack(int returnCode) {
        if (returnCode == 0) {
            return Arrays.copyOf(CONNACK_SUCCESS, CONNACK_SUCCESS.length);
        }
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

    /**
     * CONNACK success is exactly {@code 20 02 00 00}. Return code is the fourth octet when the
     * remaining length is 2; any other shape is treated as rejection.
     */
    static int parseConnackReturnCode(byte[] body) {
        if (body == null || body.length != 2) {
            return 1;
        }
        return body[1] & 0xFF;
    }

    static boolean isConnackSuccessPacket(int firstByte, int remainingLength, byte[] body) {
        return (firstByte & 0xFF) == 0x20
                && remainingLength == 2
                && body != null
                && body.length == 2
                && body[0] == 0x00
                && body[1] == 0x00;
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
                return new FixedHeader(type, flags, remaining, first);
            }
            multiplier *= 128;
        }
        throw new IOException("Malformed remaining length");
    }

    static final class FixedHeader {
        private final int type;
        private final int flags;
        private final int remainingLength;
        private final int firstByte;

        FixedHeader(int type, int flags, int remainingLength) {
            this(type, flags, remainingLength, ((type & 0x0F) << 4) | (flags & 0x0F));
        }

        FixedHeader(int type, int flags, int remainingLength, int firstByte) {
            this.type = type;
            this.flags = flags;
            this.remainingLength = remainingLength;
            this.firstByte = firstByte;
        }

        int type() {
            return type;
        }

        int flags() {
            return flags;
        }

        int remainingLength() {
            return remainingLength;
        }

        int firstByte() {
            return firstByte;
        }
    }

    static final class ParsedPublish {
        private final String topic;
        private final int packetId;
        private final int qos;
        private final String payloadText;

        ParsedPublish(String topic, int packetId, int qos, String payloadText) {
            this.topic = topic;
            this.packetId = packetId;
            this.qos = qos;
            this.payloadText = payloadText == null ? "" : payloadText;
        }

        String topic() {
            return topic;
        }

        int packetId() {
            return packetId;
        }

        int qos() {
            return qos;
        }

        String payloadText() {
            return payloadText;
        }
    }

    static ParsedPublish parsePublish(int flags, byte[] body) {
        if (body == null || body.length < 2) {
            return null;
        }
        int topicLen = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
        if (body.length < 2 + topicLen) {
            return null;
        }
        String topic = new String(body, 2, topicLen, StandardCharsets.UTF_8);
        int qos = (flags >> 1) & 0x03;
        int offset = 2 + topicLen;
        int packetId = 0;
        if (qos > 0) {
            if (body.length < offset + 2) {
                return null;
            }
            packetId = ((body[offset] & 0xFF) << 8) | (body[offset + 1] & 0xFF);
            offset += 2;
        }
        String payload = new String(body, offset, body.length - offset, StandardCharsets.UTF_8);
        return new ParsedPublish(topic, packetId, qos, payload);
    }

    static String parseConnectClientId(byte[] body) {
        int offset = 0;
        int protoLen = ((body[offset] & 0xFF) << 8) | (body[offset + 1] & 0xFF);
        offset += 2 + protoLen + 1 + 1 + 2;
        int idLen = ((body[offset] & 0xFF) << 8) | (body[offset + 1] & 0xFF);
        return new String(body, offset + 2, idLen, StandardCharsets.UTF_8);
    }

    static String parseSubscribeTopic(byte[] body) {
        int offset = 2;
        int topicLen = ((body[offset] & 0xFF) << 8) | (body[offset + 1] & 0xFF);
        return new String(body, offset + 2, topicLen, StandardCharsets.UTF_8);
    }

    static int parsePacketId(byte[] body) {
        return ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
    }
}
