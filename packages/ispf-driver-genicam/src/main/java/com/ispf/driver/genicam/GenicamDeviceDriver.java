package com.ispf.driver.genicam;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GenICam feature lab driver — ASCII feature get/set over TCP (GigE Vision control lab).
 * <p>
 * Lab dialect (not full GVCP/GVSP streaming, not XML SFNC browser): newline commands
 * {@code GET &lt;Feature&gt;} / {@code SET &lt;Feature&gt; &lt;value&gt;}.
 * Point mapping is a feature name ({@code Width}, {@code Gain}, {@code AcquisitionMode}).
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only. Not GenTL producer / vendor camera SDK.
 */
public class GenicamDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("genicamValue")
            .field("value", FieldType.STRING)
            .field("feature", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "genicam",
            "GenICam Driver",
            "0.1.0",
            "GenICam feature TCP ASCII lab: GET/SET (not GVCP/GVSP stream / GenTL)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "3956",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 3956;
    private int timeoutMs = 3000;
    private Socket socket;
    private final Map<String, String> features = new ConcurrentHashMap<>();
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
        try {
            Socket next = new Socket();
            next.connect(new InetSocketAddress(host, port), timeoutMs);
            next.setSoTimeout(timeoutMs);
            next.setTcpNoDelay(true);
            socket = next;
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "GenICam feature lab connected to " + host + ":" + port);
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("GenICam connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        features.clear();
        closeSocket();
    }

    @Override
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        ensureConnected();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String feature = normalizeFeature(entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId : entry.getValue());
            features.put(pointId, feature);
            String raw = transact("GET " + feature);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", extractValue(raw),
                    "feature", feature,
                    "raw", raw
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        String feature = features.getOrDefault(pointId, normalizeFeature(pointId));
        String next = extractRecordValue(value);
        String raw = transact("SET " + feature + " " + next);
        String parsed = extractValue(raw);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", parsed.isBlank() ? next : parsed,
                "feature", feature,
                "raw", raw
        )));
    }

    private synchronized String transact(String command) throws DriverException {
        try {
            writeLine(socket.getOutputStream(), command);
            String line = readLine(socket.getInputStream());
            if (line == null) {
                throw new IOException("EOF from GenICam lab");
            }
            return line.trim();
        } catch (IOException e) {
            throw new DriverException("GenICam I/O failed for " + host + ":" + port, e);
        }
    }

    static String normalizeFeature(String mapping) {
        String t = mapping.trim();
        String upper = t.toUpperCase(Locale.ROOT);
        if (upper.startsWith("GET ")) {
            return t.substring(4).trim();
        }
        if (upper.startsWith("SET ")) {
            return t.substring(4).trim().split("\\s+")[0];
        }
        return t;
    }

    static String extractValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.startsWith("VALUE ")) {
            return raw.substring(6).trim();
        }
        if (upper.startsWith("OK ")) {
            return raw.substring(3).trim();
        }
        return raw.trim();
    }

    private static String extractRecordValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Object raw = value.firstRow().get("value");
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }

    private void closeSocket() {
        Socket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // best-effort
            }
        }
    }

    static void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                if (buf.size() == 0) {
                    return null;
                }
                break;
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buf.write(b);
            }
        }
        return buf.toString(StandardCharsets.US_ASCII);
    }
}
