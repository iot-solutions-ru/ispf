package com.ispf.driver.pulsar;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Apache Pulsar–compatible lab driver using a clean-room TCP text framing protocol.
 * <p>
 * This is a <strong>lab subset</strong> for CI and twin work — not the Apache Pulsar binary
 * protocol, and not a Pulsar client library. Each TCP session exchanges newline-terminated
 * UTF-8 commands:
 * <ul>
 *   <li>{@code PUB <topic> <payload>} — publish (write)</li>
 *   <li>{@code GET <topic>} — fetch last payload for topic (read)</li>
 * </ul>
 * Broker replies with {@code OK}, {@code MSG <topic> <payload>}, {@code NIL}, or {@code ERR ...}.
 * Clean-room ISPF code, Apache-2.0 — no Apache Pulsar client dependency.
 */
public class PulsarDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("pulsarValue")
            .field("value", FieldType.STRING)
            .field("topic", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "pulsar",
            "Apache Pulsar Lab Driver",
            "0.1.0",
            "Lab TCP PUB/GET text framing for topic payloads (not full Pulsar binary protocol)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "6650",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 6650;
    private int timeoutMs = 3000;
    private final Map<String, String> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

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
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Pulsar lab broker ready for " + host + ":" + port);
    }

    @Override
    public void disconnect() {
        connected = false;
        points.clear();
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
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String topic = entry.getValue() == null || entry.getValue().isBlank() ? pointId : entry.getValue().trim();
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
        String reply = transact("GET " + topic);
        if (reply.equals("NIL") || reply.isBlank()) {
            return "";
        }
        if (reply.startsWith("MSG ")) {
            String rest = reply.substring(4);
            int space = rest.indexOf(' ');
            if (space < 0) {
                return "";
            }
            return rest.substring(space + 1);
        }
        if (reply.startsWith("ERR ")) {
            throw new DriverException("Pulsar lab GET error: " + reply.substring(4));
        }
        throw new DriverException("Unexpected Pulsar lab reply: " + reply);
    }

    private void publish(String topic, String payload) throws DriverException {
        String reply = transact("PUB " + topic + " " + payload);
        if (!"OK".equals(reply)) {
            if (reply.startsWith("ERR ")) {
                throw new DriverException("Pulsar lab PUB error: " + reply.substring(4));
            }
            throw new DriverException("Unexpected Pulsar lab reply: " + reply);
        }
    }

    private String transact(String command) throws DriverException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            return readLine(in);
        } catch (IOException e) {
            throw new DriverException("Pulsar lab I/O failed for " + host + ":" + port, e);
        }
    }

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int ch = in.read();
            if (ch < 0) {
                if (line.size() == 0) {
                    throw new IOException("EOF reading Pulsar lab reply");
                }
                break;
            }
            if (ch == '\n') {
                break;
            }
            if (ch != '\r') {
                line.write(ch);
            }
        }
        return line.toString(StandardCharsets.UTF_8);
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
}
