package com.ispf.driver.mqttsn;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * MQTT-SN driver — UDP subset of MQTT-SN 1.2 (CONNECT/CONNACK, REGISTER/REGACK,
 * PUBLISH/PUBACK, SUBSCRIBE/SUBACK).
 * <p>
 * Point mapping is the MQTT-SN topic name. {@code readPoints} issues SUBSCRIBE and returns the
 * last PUBLISH payload for that topic (waiting up to {@code timeoutMs}). {@code writePoint}
 * REGISTERs the topic when needed and PUBLISHes QoS 1.
 * Clean-room ISPF code, Apache-2.0 — JDK {@link DatagramSocket} only.
 */
public class MqttSnDeviceDriver implements DeviceDriver {

    static final byte MSG_CONNECT = 0x04;
    static final byte MSG_CONNACK = 0x05;
    static final byte MSG_REGISTER = 0x0A;
    static final byte MSG_REGACK = 0x0B;
    static final byte MSG_PUBLISH = 0x0C;
    static final byte MSG_PUBACK = 0x0D;
    static final byte MSG_SUBSCRIBE = 0x12;
    static final byte MSG_SUBACK = 0x13;
    static final byte MSG_DISCONNECT = 0x18;

    static final byte RC_ACCEPTED = 0x00;
    static final byte FLAG_CLEAN_SESSION = 0x04;
    static final byte FLAG_QOS1 = 0x20;

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("mqttSnValue")
            .field("value", FieldType.STRING)
            .field("topic", FieldType.STRING)
            .field("topicId", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "mqtt-sn",
            "MQTT-SN Driver",
            "0.1.0",
            "MQTT-SN 1.2 UDP client: CONNECT, REGISTER/SUBSCRIBE, PUBLISH QoS1",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "1883",
                    "timeoutMs", "3000",
                    "clientId", "ispf-mqtt-sn",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 1883;
    private int timeoutMs = 3000;
    private String clientId = "ispf-mqtt-sn";

    private DatagramSocket socket;
    private InetSocketAddress gateway;
    private Thread receiver;
    private volatile boolean connected;
    private volatile boolean running;

    private final AtomicInteger nextMsgId = new AtomicInteger(1);
    private final Map<String, Integer> topicIds = new ConcurrentHashMap<>();
    private final Map<Integer, String> topicNames = new ConcurrentHashMap<>();
    private final Map<String, String> lastPayloads = new ConcurrentHashMap<>();
    private final Map<String, String> pointTopics = new ConcurrentHashMap<>();

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition signal = lock.newCondition();
    private volatile Integer pendingMsgId;
    private volatile Byte pendingType;
    private volatile byte[] pendingBody;
    private volatile Integer pendingTopicId;
    private volatile Byte pendingReturnCode;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            case "clientId" -> clientId = value.trim();
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            gateway = new InetSocketAddress(host, port);
            socket = new DatagramSocket();
            socket.setSoTimeout(Math.max(timeoutMs, 100));
            running = true;
            receiver = new Thread(this::receiveLoop, "mqtt-sn-udp-" + clientId);
            receiver.setDaemon(true);
            receiver.start();

            byte[] connect = encodeConnect(clientId, (short) Math.max(1, timeoutMs / 1000));
            expect(MSG_CONNACK, 0, () -> send(connect));
            if (pendingReturnCode == null || pendingReturnCode != RC_ACCEPTED) {
                throw new DriverException("MQTT-SN CONNACK rejected: " + pendingReturnCode);
            }
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "MQTT-SN connected to " + host + ":" + port);
        } catch (DriverException e) {
            disconnect();
            throw e;
        } catch (IOException e) {
            disconnect();
            throw new DriverException("MQTT-SN connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        running = false;
        if (socket != null && !socket.isClosed()) {
            try {
                send(encodeDisconnect());
            } catch (Exception ignored) {
                // best-effort
            }
            socket.close();
        }
        if (receiver != null) {
            try {
                receiver.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            receiver = null;
        }
        socket = null;
        topicIds.clear();
        topicNames.clear();
        lastPayloads.clear();
        pointTopics.clear();
        clearPending();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        pointTopics.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String topic = entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId
                    : entry.getValue().trim();
            pointTopics.put(pointId, topic);
            int topicId = subscribe(topic);
            String payload = awaitPayload(topic, timeoutMs);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", payload == null ? "" : payload,
                    "topic", topic,
                    "topicId", topicId
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String topic = pointTopics.getOrDefault(pointId, pointId);
        String payload = extractValue(value);
        int topicId = register(topic);
        publish(topicId, payload);
        lastPayloads.put(topic, payload);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", payload,
                "topic", topic,
                "topicId", topicId
        )));
    }

    private int subscribe(String topic) throws DriverException {
        int msgId = nextMsgId();
        byte[] frame = encodeSubscribe(topic, msgId);
        expect(MSG_SUBACK, msgId, () -> send(frame));
        if (pendingReturnCode == null || pendingReturnCode != RC_ACCEPTED) {
            throw new DriverException("MQTT-SN SUBACK rejected for topic " + topic);
        }
        int topicId = pendingTopicId == null ? 0 : pendingTopicId;
        if (topicId > 0) {
            topicIds.put(topic, topicId);
            topicNames.put(topicId, topic);
        }
        return topicId;
    }

    private int register(String topic) throws DriverException {
        Integer existing = topicIds.get(topic);
        if (existing != null && existing > 0) {
            return existing;
        }
        int msgId = nextMsgId();
        byte[] frame = encodeRegister(topic, msgId);
        expect(MSG_REGACK, msgId, () -> send(frame));
        if (pendingReturnCode == null || pendingReturnCode != RC_ACCEPTED) {
            throw new DriverException("MQTT-SN REGACK rejected for topic " + topic);
        }
        int topicId = pendingTopicId == null ? 0 : pendingTopicId;
        if (topicId <= 0) {
            throw new DriverException("MQTT-SN REGACK missing topic id for " + topic);
        }
        topicIds.put(topic, topicId);
        topicNames.put(topicId, topic);
        return topicId;
    }

    private void publish(int topicId, String payload) throws DriverException {
        int msgId = nextMsgId();
        byte[] frame = encodePublish(topicId, msgId, payload);
        expect(MSG_PUBACK, msgId, () -> send(frame));
        if (pendingReturnCode == null || pendingReturnCode != RC_ACCEPTED) {
            throw new DriverException("MQTT-SN PUBACK rejected for topicId " + topicId);
        }
    }

    private String awaitPayload(String topic, long waitMs) throws DriverException {
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
                try {
                    signal.awaitNanos(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new DriverException("Interrupted waiting for MQTT-SN PUBLISH on " + topic, e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void expect(byte msgType, int msgId, IORunnable sendAction) throws DriverException {
        lock.lock();
        try {
            clearPendingUnlocked();
            pendingType = msgType;
            pendingMsgId = msgId;
            try {
                sendAction.run();
            } catch (IOException e) {
                clearPendingUnlocked();
                throw new DriverException("MQTT-SN send failed", e);
            }
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(timeoutMs, 1));
            while (pendingBody == null) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    clearPendingUnlocked();
                    throw new DriverException("MQTT-SN timeout waiting for msgType=0x"
                            + Integer.toHexString(msgType & 0xFF));
                }
                try {
                    signal.awaitNanos(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    clearPendingUnlocked();
                    throw new DriverException("Interrupted waiting for MQTT-SN response", e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void receiveLoop() {
        byte[] buf = new byte[2048];
        while (running && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                handleFrame(copyOf(packet.getData(), packet.getLength()));
            } catch (IOException e) {
                if (!running || socket == null || socket.isClosed()) {
                    return;
                }
            }
        }
    }

    private void handleFrame(byte[] frame) {
        ParsedMessage msg = parse(frame);
        if (msg == null) {
            return;
        }
        if (msg.type == MSG_PUBLISH) {
            handlePublish(msg.body);
            return;
        }
        lock.lock();
        try {
            if (pendingType != null && pendingType == msg.type) {
                if (matchesPending(msg)) {
                    pendingBody = msg.body;
                    signal.signalAll();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean matchesPending(ParsedMessage msg) {
        ByteBuffer body = ByteBuffer.wrap(msg.body);
        switch (msg.type) {
            case MSG_CONNACK -> {
                pendingReturnCode = body.remaining() > 0 ? body.get() : -1;
                return true;
            }
            case MSG_REGACK, MSG_PUBACK -> {
                if (body.remaining() < 5) {
                    return false;
                }
                pendingTopicId = body.getShort() & 0xFFFF;
                int msgId = body.getShort() & 0xFFFF;
                pendingReturnCode = body.get();
                return pendingMsgId != null && pendingMsgId == msgId;
            }
            case MSG_SUBACK -> {
                if (body.remaining() < 6) {
                    return false;
                }
                body.get(); // flags
                pendingTopicId = body.getShort() & 0xFFFF;
                int msgId = body.getShort() & 0xFFFF;
                pendingReturnCode = body.get();
                return pendingMsgId != null && pendingMsgId == msgId;
            }
            default -> {
                return false;
            }
        }
    }

    private void handlePublish(byte[] body) {
        if (body.length < 5) {
            return;
        }
        ByteBuffer buf = ByteBuffer.wrap(body);
        buf.get(); // flags
        int topicId = buf.getShort() & 0xFFFF;
        buf.getShort(); // msgId
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        String payload = new String(data, StandardCharsets.UTF_8);
        String topic = topicNames.get(topicId);
        if (topic == null) {
            topic = "topicId:" + topicId;
        }
        lock.lock();
        try {
            lastPayloads.put(topic, payload);
            signal.signalAll();
        } finally {
            lock.unlock();
        }
        for (Map.Entry<String, String> entry : pointTopics.entrySet()) {
            if (topic.equals(entry.getValue())) {
                driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                        "value", payload,
                        "topic", topic,
                        "topicId", topicId
                )));
            }
        }
    }

    private void send(byte[] frame) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("Socket closed");
        }
        socket.send(new DatagramPacket(frame, frame.length, gateway));
    }

    private int nextMsgId() {
        int id = nextMsgId.getAndUpdate(v -> v >= 0xFFFF ? 1 : v + 1);
        return id & 0xFFFF;
    }

    private void clearPending() {
        lock.lock();
        try {
            clearPendingUnlocked();
        } finally {
            lock.unlock();
        }
    }

    private void clearPendingUnlocked() {
        pendingMsgId = null;
        pendingType = null;
        pendingBody = null;
        pendingTopicId = null;
        pendingReturnCode = null;
    }

    static byte[] encodeConnect(String clientId, short durationSeconds) {
        byte[] id = clientId.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(FLAG_CLEAN_SESSION);
        out.write(0x01); // protocol id
        out.write((durationSeconds >> 8) & 0xFF);
        out.write(durationSeconds & 0xFF);
        out.writeBytes(id);
        return wrap(MSG_CONNECT, out.toByteArray());
    }

    static byte[] encodeDisconnect() {
        return wrap(MSG_DISCONNECT, new byte[0]);
    }

    static byte[] encodeRegister(String topic, int msgId) {
        byte[] name = topic.getBytes(StandardCharsets.UTF_8);
        ByteBuffer body = ByteBuffer.allocate(4 + name.length);
        body.putShort((short) 0); // topic id filled by gateway
        body.putShort((short) msgId);
        body.put(name);
        return wrap(MSG_REGISTER, body.array());
    }

    static byte[] encodeSubscribe(String topic, int msgId) {
        byte[] name = topic.getBytes(StandardCharsets.UTF_8);
        ByteBuffer body = ByteBuffer.allocate(3 + name.length);
        body.put(FLAG_QOS1); // QoS1, normal topic name
        body.putShort((short) msgId);
        body.put(name);
        return wrap(MSG_SUBSCRIBE, body.array());
    }

    static byte[] encodePublish(int topicId, int msgId, String payload) {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuffer body = ByteBuffer.allocate(5 + data.length);
        body.put(FLAG_QOS1);
        body.putShort((short) topicId);
        body.putShort((short) msgId);
        body.put(data);
        return wrap(MSG_PUBLISH, body.array());
    }

    static byte[] wrap(byte msgType, byte[] body) {
        int total = 2 + body.length;
        if (total < 256) {
            byte[] frame = new byte[total];
            frame[0] = (byte) total;
            frame[1] = msgType;
            System.arraycopy(body, 0, frame, 2, body.length);
            return frame;
        }
        int ext = 4 + body.length;
        byte[] frame = new byte[ext];
        frame[0] = 0x01;
        frame[1] = (byte) ((ext >> 8) & 0xFF);
        frame[2] = (byte) (ext & 0xFF);
        frame[3] = msgType;
        System.arraycopy(body, 0, frame, 4, body.length);
        return frame;
    }

    static ParsedMessage parse(byte[] frame) {
        if (frame == null || frame.length < 2) {
            return null;
        }
        int offset;
        int length;
        if ((frame[0] & 0xFF) == 0x01) {
            if (frame.length < 4) {
                return null;
            }
            length = ((frame[1] & 0xFF) << 8) | (frame[2] & 0xFF);
            offset = 3;
        } else {
            length = frame[0] & 0xFF;
            offset = 1;
        }
        if (frame.length < length || offset >= frame.length) {
            return null;
        }
        byte type = frame[offset];
        int bodyStart = offset + 1;
        byte[] body = copyOf(frame, bodyStart, length - bodyStart);
        return new ParsedMessage(type, body);
    }

    private static byte[] copyOf(byte[] src, int length) {
        return copyOf(src, 0, length);
    }

    private static byte[] copyOf(byte[] src, int from, int length) {
        byte[] out = new byte[Math.max(length, 0)];
        if (length > 0) {
            System.arraycopy(src, from, out, 0, Math.min(length, src.length - from));
        }
        return out;
    }

    private static String extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "payload", "data", "text", "raw")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return String.valueOf(candidate);
            }
        }
        if (row.size() == 1) {
            return String.valueOf(row.values().iterator().next());
        }
        return row.toString();
    }

    @FunctionalInterface
    private interface IORunnable {
        void run() throws IOException;
    }

    record ParsedMessage(byte type, byte[] body) {
    }
}
