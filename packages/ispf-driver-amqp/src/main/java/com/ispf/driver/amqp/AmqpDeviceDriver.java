package com.ispf.driver.amqp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AMQP <strong>0-9-1 lab subset</strong> client over TCP (default port 5672) — clean-room ISPF,
 * Apache-2.0, JDK sockets only.
 * <p>
 * Honesty boundary: this is <strong>not</strong> AMQP 1.0, not a full RabbitMQ / Qpid feature set,
 * and not a Proton/Netty client. Implemented wire methods only:
 * <ul>
 *   <li>Protocol header {@code AMQP\0\0\9\1}</li>
 *   <li>{@code connection.start} / {@code start-ok} / {@code tune} / {@code tune-ok} /
 *       {@code open} / {@code open-ok}</li>
 *   <li>{@code channel.open} / {@code open-ok}</li>
 *   <li>Write: {@code basic.publish} to the <em>default exchange</em> with routing key = point
 *       mapping; message body = record {@code value}</li>
 *   <li>Read: {@code basic.get} (no-ack) for queue name = point mapping; body → {@code value}</li>
 * </ul>
 * Missing on purpose: SASL beyond PLAIN/AMQPLAIN token passthrough in start-ok, confirms,
 * consumers/{@code basic.deliver}, exchanges/queues declare, heartbeats, TLS, publisher confirms.
 */
public class AmqpDeviceDriver implements DeviceDriver {

    private static final byte[] PROTOCOL_HEADER = new byte[]{'A', 'M', 'Q', 'P', 0, 0, 9, 1};
    private static final int FRAME_METHOD = 1;
    private static final int FRAME_HEADER = 2;
    private static final int FRAME_BODY = 3;
    private static final int FRAME_END = 0xCE;

    private static final int CLASS_CONNECTION = 10;
    private static final int CLASS_CHANNEL = 20;
    private static final int CLASS_BASIC = 60;

    private static final int CONNECTION_START = 10;
    private static final int CONNECTION_START_OK = 11;
    private static final int CONNECTION_TUNE = 30;
    private static final int CONNECTION_TUNE_OK = 31;
    private static final int CONNECTION_OPEN = 40;
    private static final int CONNECTION_OPEN_OK = 41;
    private static final int CONNECTION_CLOSE = 50;
    private static final int CONNECTION_CLOSE_OK = 51;

    private static final int CHANNEL_OPEN = 10;
    private static final int CHANNEL_OPEN_OK = 11;
    private static final int CHANNEL_CLOSE = 40;
    private static final int CHANNEL_CLOSE_OK = 41;

    private static final int BASIC_PUBLISH = 40;
    private static final int BASIC_GET = 70;
    private static final int BASIC_GET_OK = 71;
    private static final int BASIC_GET_EMPTY = 72;

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("amqpValue")
            .field("value", FieldType.STRING)
            .field("queue", FieldType.STRING)
            .field("routingKey", FieldType.STRING)
            .field("empty", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "amqp",
            "AMQP 0-9-1 Lab Driver",
            "0.1.0",
            "AMQP 0-9-1 lab subset (header/start/tune/open/channel/basic.publish+get) — "
                    + "NOT AMQP 1.0, NOT full RabbitMQ feature set",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5672",
                    "timeoutMs", "3000",
                    "virtualHost", "/",
                    "username", "guest",
                    "password", "guest"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5672;
    private int timeoutMs = 3000;
    private String virtualHost = "/";
    private String username = "guest";
    private String password = "guest";

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private final Object ioLock = new Object();
    private final Map<String, String> points = new ConcurrentHashMap<>();
    private volatile boolean connected;
    private int channelMax = 2047;
    private int frameMax = 131072;
    private int heartbeat = 0;

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
            case "virtualHost", "vhost" -> virtualHost = value.trim();
            case "username", "user" -> username = value.trim();
            case "password", "pass" -> password = value.trim();
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(timeoutMs);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            out.write(PROTOCOL_HEADER);
            out.flush();
            handshake();
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "AMQP 0-9-1 connected to " + host + ":" + port + " vhost=" + virtualHost);
        } catch (IOException e) {
            disconnect();
            throw new DriverException("AMQP connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        points.clear();
        if (out != null && socket != null && !socket.isClosed()) {
            try {
                synchronized (ioLock) {
                    writeMethod(0, CLASS_CONNECTION, CONNECTION_CLOSE, buf -> {
                        writeShort(buf, 200);
                        writeShortstr(buf, "OK");
                        writeShort(buf, 0);
                        writeShort(buf, 0);
                    });
                    // Best-effort close-ok; ignore failures while tearing down.
                    try {
                        Frame closeOk = readFrame();
                        // drain optional reply
                        if (closeOk != null) {
                            // no-op
                        }
                    } catch (IOException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        }
        closeQuietly(socket);
        socket = null;
        in = null;
        out = null;
    }

    @Override
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        ensureConnected();
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String queue = entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId : entry.getValue().trim();
            points.put(pointId, queue);
            GetResult got = basicGet(queue);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", got.body == null ? "" : got.body,
                    "queue", queue,
                    "routingKey", got.routingKey == null ? queue : got.routingKey,
                    "empty", got.empty ? "true" : "false"
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        String routingKey = points.getOrDefault(pointId, pointId);
        String payload = extractValue(value);
        basicPublish(routingKey, payload);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", payload,
                "queue", routingKey,
                "routingKey", routingKey,
                "empty", "false"
        )));
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }

    private void handshake() throws IOException {
        Frame start = expectMethod(0, CLASS_CONNECTION, CONNECTION_START);
        // version-major, version-minor, server-properties, mechanisms, locales
        DataInputStream args = new DataInputStream(new java.io.ByteArrayInputStream(start.payload, 4, start.payload.length - 4));
        args.readUnsignedByte();
        args.readUnsignedByte();
        skipTable(args);
        skipLongstr(args);
        skipLongstr(args);

        writeMethod(0, CLASS_CONNECTION, CONNECTION_START_OK, buf -> {
            writeTable(buf, Map.of("product", "ispf-driver-amqp", "version", "0.1.0"));
            writeShortstr(buf, "PLAIN");
            byte[] response = ("\0" + username + "\0" + password).getBytes(StandardCharsets.UTF_8);
            writeLongstr(buf, response);
            writeShortstr(buf, "en_US");
        });

        Frame tune = expectMethod(0, CLASS_CONNECTION, CONNECTION_TUNE);
        DataInputStream tuneArgs = new DataInputStream(new java.io.ByteArrayInputStream(tune.payload, 4, tune.payload.length - 4));
        channelMax = tuneArgs.readUnsignedShort();
        frameMax = tuneArgs.readInt();
        heartbeat = tuneArgs.readUnsignedShort();
        if (channelMax == 0) {
            channelMax = 2047;
        }
        if (frameMax == 0) {
            frameMax = 131072;
        }

        writeMethod(0, CLASS_CONNECTION, CONNECTION_TUNE_OK, buf -> {
            writeShort(buf, channelMax);
            writeLong(buf, frameMax);
            writeShort(buf, heartbeat);
        });

        writeMethod(0, CLASS_CONNECTION, CONNECTION_OPEN, buf -> {
            writeShortstr(buf, virtualHost);
            writeShortstr(buf, ""); // reserved
            buf.write(0); // insist bit
        });
        expectMethod(0, CLASS_CONNECTION, CONNECTION_OPEN_OK);

        writeMethod(1, CLASS_CHANNEL, CHANNEL_OPEN, buf -> writeShortstr(buf, ""));
        expectMethod(1, CLASS_CHANNEL, CHANNEL_OPEN_OK);
    }

    private void basicPublish(String routingKey, String body) throws DriverException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        try {
            synchronized (ioLock) {
                writeMethod(1, CLASS_BASIC, BASIC_PUBLISH, buf -> {
                    writeShort(buf, 0); // ticket
                    writeShortstr(buf, ""); // default exchange
                    writeShortstr(buf, routingKey);
                    buf.write(0); // mandatory/immediate bits
                });
                writeContentHeader(1, CLASS_BASIC, payload.length);
                writeBodyFrames(1, payload);
            }
        } catch (IOException e) {
            connected = false;
            throw new DriverException("AMQP basic.publish failed for routing key " + routingKey, e);
        }
    }

    private GetResult basicGet(String queue) throws DriverException {
        try {
            synchronized (ioLock) {
                writeMethod(1, CLASS_BASIC, BASIC_GET, buf -> {
                    writeShort(buf, 0);
                    writeShortstr(buf, queue);
                    buf.write(1); // no-ack = true
                });
                Frame method = readFrame();
                ensureMethod(method);
                int classId = readClassId(method.payload);
                int methodId = readMethodId(method.payload);
                if (classId == CLASS_BASIC && methodId == BASIC_GET_EMPTY) {
                    return new GetResult(true, "", queue);
                }
                if (classId == CLASS_CONNECTION && methodId == CONNECTION_CLOSE) {
                    throw new IOException("connection.close during basic.get");
                }
                if (classId == CLASS_CHANNEL && methodId == CHANNEL_CLOSE) {
                    throw new IOException("channel.close during basic.get");
                }
                if (!(classId == CLASS_BASIC && methodId == BASIC_GET_OK)) {
                    throw new IOException("Unexpected method " + classId + "/" + methodId);
                }
                DataInputStream args = new DataInputStream(new java.io.ByteArrayInputStream(
                        method.payload, 4, method.payload.length - 4));
                args.readLong(); // delivery-tag
                args.readUnsignedByte(); // redelivered bit octet
                skipShortstr(args); // exchange
                String rk = readShortstr(args);
                args.readInt(); // message-count

                Frame header = readFrame();
                if (header.type != FRAME_HEADER) {
                    throw new IOException("Expected content header, got type " + header.type);
                }
                DataInputStream hp = new DataInputStream(new java.io.ByteArrayInputStream(header.payload));
                hp.readUnsignedShort(); // class-id
                hp.readUnsignedShort(); // weight
                long bodySize = hp.readLong();

                ByteArrayOutputStream body = new ByteArrayOutputStream();
                long remaining = bodySize;
                while (remaining > 0) {
                    Frame bodyFrame = readFrame();
                    if (bodyFrame.type != FRAME_BODY) {
                        throw new IOException("Expected body frame, got type " + bodyFrame.type);
                    }
                    body.write(bodyFrame.payload);
                    remaining -= bodyFrame.payload.length;
                }
                return new GetResult(false, body.toString(StandardCharsets.UTF_8), rk);
            }
        } catch (IOException e) {
            connected = false;
            throw new DriverException("AMQP basic.get failed for queue " + queue, e);
        }
    }

    private Frame expectMethod(int channel, int classId, int methodId) throws IOException {
        Frame frame = readFrame();
        ensureMethod(frame);
        if (frame.channel != channel) {
            throw new IOException("Expected channel " + channel + ", got " + frame.channel);
        }
        int gotClass = readClassId(frame.payload);
        int gotMethod = readMethodId(frame.payload);
        if (gotClass == CLASS_CONNECTION && gotMethod == CONNECTION_CLOSE) {
            throw new IOException("Server sent connection.close");
        }
        if (gotClass != classId || gotMethod != methodId) {
            throw new IOException("Expected method " + classId + "/" + methodId
                    + ", got " + gotClass + "/" + gotMethod);
        }
        return frame;
    }

    private static void ensureMethod(Frame frame) throws IOException {
        if (frame.type != FRAME_METHOD) {
            throw new IOException("Expected method frame, got type " + frame.type);
        }
        if (frame.payload.length < 4) {
            throw new IOException("Method payload too short");
        }
    }

    private void writeMethod(int channel, int classId, int methodId, FrameWriter args) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(payload);
        writeShort(dos, classId);
        writeShort(dos, methodId);
        args.write(dos);
        dos.flush();
        writeFrame(FRAME_METHOD, channel, payload.toByteArray());
    }

    private void writeContentHeader(int channel, int classId, long bodySize) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(payload);
        writeShort(dos, classId);
        writeShort(dos, 0); // weight
        dos.writeLong(bodySize);
        writeShort(dos, 0); // property flags = none
        dos.flush();
        writeFrame(FRAME_HEADER, channel, payload.toByteArray());
    }

    private void writeBodyFrames(int channel, byte[] body) throws IOException {
        if (body.length == 0) {
            return; // AMQP 0-9-1: no body frames when content body-size is 0
        }
        int max = Math.max(8, frameMax - 8);
        int offset = 0;
        while (offset < body.length) {
            int len = Math.min(max, body.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(body, offset, chunk, 0, len);
            writeFrame(FRAME_BODY, channel, chunk);
            offset += len;
        }
    }

    private void writeFrame(int type, int channel, byte[] payload) throws IOException {
        out.writeByte(type);
        out.writeShort(channel);
        out.writeInt(payload.length);
        out.write(payload);
        out.writeByte(FRAME_END);
        out.flush();
    }

    private Frame readFrame() throws IOException {
        int type = in.readUnsignedByte();
        int channel = in.readUnsignedShort();
        int size = in.readInt();
        if (size < 0 || size > Math.max(frameMax, 1024 * 1024)) {
            throw new IOException("Invalid AMQP frame size " + size);
        }
        byte[] payload = in.readNBytes(size);
        if (payload.length != size) {
            throw new EOFException("Truncated AMQP frame payload");
        }
        int end = in.readUnsignedByte();
        if (end != FRAME_END) {
            throw new IOException("Bad AMQP frame end marker: " + end);
        }
        return new Frame(type, channel, payload);
    }

    private static int readClassId(byte[] payload) {
        return ((payload[0] & 0xff) << 8) | (payload[1] & 0xff);
    }

    private static int readMethodId(byte[] payload) {
        return ((payload[2] & 0xff) << 8) | (payload[3] & 0xff);
    }

    private static void writeShort(DataOutputStream out, int value) throws IOException {
        out.writeShort(value);
    }

    private static void writeLong(DataOutputStream out, int value) throws IOException {
        out.writeInt(value);
    }

    private static void writeShortstr(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 255) {
            throw new IOException("shortstr too long");
        }
        out.writeByte(bytes.length);
        out.write(bytes);
    }

    private static void writeLongstr(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void writeTable(DataOutputStream out, Map<String, String> table) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        DataOutputStream t = new DataOutputStream(raw);
        for (Map.Entry<String, String> e : table.entrySet()) {
            writeShortstr(t, e.getKey());
            t.writeByte('S'); // longstr field
            writeLongstr(t, e.getValue().getBytes(StandardCharsets.UTF_8));
        }
        t.flush();
        byte[] bytes = raw.toByteArray();
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void skipTable(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0) {
            throw new IOException("negative table size");
        }
        in.skipNBytes(size);
    }

    private static void skipLongstr(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0) {
            throw new IOException("negative longstr size");
        }
        in.skipNBytes(size);
    }

    private static void skipShortstr(DataInputStream in) throws IOException {
        int size = in.readUnsignedByte();
        in.skipNBytes(size);
    }

    private static String readShortstr(DataInputStream in) throws IOException {
        int size = in.readUnsignedByte();
        byte[] bytes = in.readNBytes(size);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "payload", "body", "data", "text", "raw")) {
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

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    @FunctionalInterface
    private interface FrameWriter {
        void write(DataOutputStream out) throws IOException;
    }

    private record Frame(int type, int channel, byte[] payload) {
    }

    private record GetResult(boolean empty, String body, String routingKey) {
    }
}
