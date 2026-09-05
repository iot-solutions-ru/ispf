package com.ispf.driver.redis;

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
 * Redis driver — RESP GET/SET over TCP for key/value telemetry points.
 * <p>
 * Point mapping is the Redis key. Write maps the record {@code value} field (or sole field) to SET.
 * Clean-room ISPF code, Apache-2.0 — no third-party Redis client.
 */
public class RedisDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("redisValue")
            .field("value", FieldType.STRING)
            .field("key", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "redis",
            "Redis Driver",
            "0.1.0",
            "Polls Redis keys via RESP GET and writes via SET",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "6379",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 6379;
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
        driverObject.log(DriverLogLevel.INFO, "Redis ready for " + host + ":" + port);
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
            String key = entry.getValue() == null || entry.getValue().isBlank() ? pointId : entry.getValue();
            points.put(pointId, key);
            String value = command("GET", key);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", value == null ? "" : value,
                    "key", key
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String key = points.getOrDefault(pointId, pointId);
        String payload = extractValue(value);
        command("SET", key, payload);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", payload,
                "key", key
        )));
    }

    private String command(String... parts) throws DriverException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(encode(parts));
            out.flush();
            return decode(in);
        } catch (IOException e) {
            throw new DriverException("Redis " + parts[0] + " failed for " + host + ":" + port, e);
        }
    }

    static byte[] encode(String... parts) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        writeAscii(buf, "*" + parts.length + "\r\n");
        for (String part : parts) {
            byte[] raw = part.getBytes(StandardCharsets.UTF_8);
            writeAscii(buf, "$" + raw.length + "\r\n");
            buf.writeBytes(raw);
            writeAscii(buf, "\r\n");
        }
        return buf.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream buf, String text) {
        buf.writeBytes(text.getBytes(StandardCharsets.US_ASCII));
    }

    static String decode(InputStream in) throws IOException {
        int prefix = in.read();
        if (prefix < 0) {
            throw new IOException("Empty Redis response");
        }
        return switch ((char) prefix) {
            case '+' -> readLine(in);
            case '-' -> throw new IOException("Redis error: " + readLine(in));
            case ':' -> readLine(in);
            case '$' -> {
                int len = Integer.parseInt(readLine(in));
                if (len < 0) {
                    yield null;
                }
                byte[] data = in.readNBytes(len);
                if (data.length < len) {
                    throw new IOException("Truncated bulk string");
                }
                in.read(); // \r
                in.read(); // \n
                yield new String(data, StandardCharsets.UTF_8);
            }
            case '*' -> throw new IOException("Unexpected Redis array response");
            default -> throw new IOException("Unsupported Redis reply type: " + (char) prefix);
        };
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int ch = in.read();
            if (ch < 0) {
                throw new IOException("EOF reading Redis line");
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
