package com.ispf.driver.pulsar;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Apache Pulsar–shaped TCP driver — lab binary framing of a BaseCommand whose type is
 * CONNECT, then length-prefixed lab topic payloads on the same socket.
 * <p>
 * This is <strong>not</strong> a protobuf broker — no CommandProducer / CommandSend,
 * no full Pulsar client library. JDK sockets, Apache-2.0.
 */
public class PulsarDeviceDriver implements DeviceDriver {

    /**
     * totalSize=10, commandSize=2, BaseCommand.type=CONNECT (enum 2): tag 0x08 value 0x02.
     */
    static final byte[] PULSAR_CONNECT = {
            0x00, 0x00, 0x00, 0x0A, 0x00, 0x00, 0x00, 0x02, 0x08, 0x02
    };

    /** Same sizes with type=CONNECTED (enum 3). */
    static final byte[] PULSAR_CONNECTED = {
            0x00, 0x00, 0x00, 0x0A, 0x00, 0x00, 0x00, 0x02, 0x08, 0x03
    };

    private static final byte OP_GET = 0x01;
    private static final byte OP_PUB = 0x02;
    private static final byte OP_MSG = 0x03;
    private static final byte OP_NIL = 0x04;
    private static final byte OP_OK = 0x05;
    private static final byte OP_ERR = 0x06;

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("pulsarValue")
            .field("value", FieldType.STRING)
            .field("topic", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "pulsar",
            "Apache Pulsar Lab Driver",
            "0.2.0",
            "lab binary header plus BaseCommand CONNECT; not a protobuf broker",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "6650",
                    "timeoutMs", "3000"
            ),
            DriverMaturity.BETA,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 6650;
    private int timeoutMs = 3000;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private final Map<String, String> points = new ConcurrentHashMap<>();

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
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            Socket next = new Socket();
            next.connect(new InetSocketAddress(host, port), timeoutMs);
            next.setTcpNoDelay(true);
            next.setSoTimeout(timeoutMs);
            InputStream nextIn = next.getInputStream();
            OutputStream nextOut = next.getOutputStream();
            nextOut.write(PULSAR_CONNECT);
            nextOut.flush();
            byte[] reply = readFully(nextIn, PULSAR_CONNECTED.length);
            if (!Arrays.equals(PULSAR_CONNECTED, reply)) {
                next.close();
                throw new DriverException("Pulsar lab connect failed: CONNECTED mismatch");
            }
            socket = next;
            in = nextIn;
            out = nextOut;
            driverObject.log(DriverLogLevel.INFO,
                    "Pulsar lab CONNECT to " + host + ":" + port
                            + " (not a protobuf broker; not CommandProducer / CommandSend)");
        } catch (DriverException e) {
            throw e;
        } catch (IOException e) {
            disconnect();
            throw new DriverException("Pulsar lab connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // best-effort
            }
        }
        socket = null;
        in = null;
        out = null;
        points.clear();
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String topic = entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId : entry.getValue().trim();
            points.put(pointId, topic);
            String payload = getTopic(topic);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", payload == null ? "" : payload,
                    "topic", topic
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String topic = points.getOrDefault(pointId, pointId);
        String payload = extractValue(value);
        publish(topic, payload);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", payload,
                "topic", topic
        )));
    }

    private String getTopic(String topic) throws DriverException {
        try {
            writeLab(OP_GET, topic, new byte[0]);
            LabReply reply = readLab();
            if (reply.op == OP_NIL) {
                return "";
            }
            if (reply.op == OP_MSG) {
                return new String(reply.payload, StandardCharsets.UTF_8);
            }
            if (reply.op == OP_ERR) {
                throw new DriverException("Pulsar lab GET error: "
                        + new String(reply.payload, StandardCharsets.UTF_8));
            }
            throw new DriverException("Unexpected Pulsar lab reply op=" + (reply.op & 0xFF));
        } catch (IOException e) {
            throw new DriverException("Pulsar lab GET failed for " + topic, e);
        }
    }

    private void publish(String topic, String payload) throws DriverException {
        try {
            writeLab(OP_PUB, topic, payload.getBytes(StandardCharsets.UTF_8));
            LabReply reply = readLab();
            if (reply.op == OP_OK) {
                return;
            }
            if (reply.op == OP_ERR) {
                throw new DriverException("Pulsar lab PUB error: "
                        + new String(reply.payload, StandardCharsets.UTF_8));
            }
            throw new DriverException("Unexpected Pulsar lab reply op=" + (reply.op & 0xFF));
        } catch (IOException e) {
            throw new DriverException("Pulsar lab PUB failed for " + topic, e);
        }
    }

    /**
     * Length-prefixed lab topic payload on the CONNECT socket — not CommandProducer / CommandSend.
     * Layout: [2 BE total][op 1][2 BE topicLen][topic][2 BE payloadLen][payload]
     */
    private void writeLab(byte op, String topic, byte[] payload) throws IOException {
        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
        if (topicBytes.length > 0xFFFF || payload.length > 0xFFFF) {
            throw new IOException("Pulsar lab topic/payload too long");
        }
        int body = 1 + 2 + topicBytes.length + 2 + payload.length;
        out.write((body >> 8) & 0xFF);
        out.write(body & 0xFF);
        out.write(op);
        out.write((topicBytes.length >> 8) & 0xFF);
        out.write(topicBytes.length & 0xFF);
        out.write(topicBytes);
        out.write((payload.length >> 8) & 0xFF);
        out.write(payload.length & 0xFF);
        out.write(payload);
        out.flush();
    }

    private LabReply readLab() throws IOException {
        byte[] header = readFully(in, 2);
        int bodyLen = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
        byte[] body = readFully(in, bodyLen);
        if (bodyLen < 5) {
            throw new IOException("Pulsar lab reply too short");
        }
        byte op = body[0];
        int topicLen = ((body[1] & 0xFF) << 8) | (body[2] & 0xFF);
        int offset = 3 + topicLen;
        if (offset + 2 > bodyLen) {
            throw new IOException("Pulsar lab reply truncated topic");
        }
        int payloadLen = ((body[offset] & 0xFF) << 8) | (body[offset + 1] & 0xFF);
        offset += 2;
        if (offset + payloadLen > bodyLen) {
            throw new IOException("Pulsar lab reply truncated payload");
        }
        byte[] payload = Arrays.copyOfRange(body, offset, offset + payloadLen);
        return new LabReply(op, payload);
    }

    static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = in.read(buf, offset, length - offset);
            if (n < 0) {
                throw new EOFException("EOF reading Pulsar lab bytes, need " + length + " got " + offset);
            }
            offset += n;
        }
        return buf;
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

    /** Lab reply carrier — not a Java record (payload is byte[]). */
    private static final class LabReply {
        final byte op;
        final byte[] payload;

        LabReply(byte op, byte[] payload) {
            this.op = op;
            this.payload = payload;
        }
    }
}
