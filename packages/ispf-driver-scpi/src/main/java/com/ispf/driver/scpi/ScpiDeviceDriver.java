package com.ispf.driver.scpi;

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
 * SCPI instrument driver — IEEE 488.2-style ASCII commands over a raw TCP socket.
 * <p>
 * Point mapping is the SCPI query or command (for example {@code *IDN?}, {@code MEAS:VOLT:DC?},
 * {@code VOLT}). Reads send the mapped query and capture one response line. Writes send a set
 * command built from the mapping and the record {@code value} field (see {@link #writePoint}).
 * Opens one TCP session on {@link #connect()} and reuses it until {@link #disconnect()}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only; no proprietary instrument stacks.
 */
public class ScpiDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("scpiValue")
            .field("value", FieldType.STRING)
            .field("command", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "scpi",
            "SCPI Driver",
            "0.1.0",
            "Polls IEEE 488.2-style SCPI queries over TCP and writes ASCII set commands",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5025",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5025;
    private int timeoutMs = 3000;
    private Socket socket;
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
        try {
            Socket next = new Socket();
            next.connect(new InetSocketAddress(host, port), timeoutMs);
            next.setSoTimeout(timeoutMs);
            next.setTcpNoDelay(true);
            socket = next;
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "SCPI connected to " + host + ":" + port);
        } catch (IOException e) {
            closeSocket();
            throw new DriverException("SCPI connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        points.clear();
        closeSocket();
    }

    @Override
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String command = entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId
                    : entry.getValue().trim();
            points.put(pointId, command);
            // Poll mappings ending in '?' are queried; non-query mappings are registered for writes only.
            String response = isQuery(command) ? query(command) : "";
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", response == null ? "" : response,
                    "command", command
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String mapping = points.getOrDefault(pointId, pointId);
        String payload = extractValue(value);
        String command = buildWriteCommand(mapping, payload);
        if (isQuery(command)) {
            String response = query(command);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", response == null ? "" : response,
                    "command", command
            )));
        } else {
            send(command);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", payload,
                    "command", command
            )));
        }
    }

    /**
     * Builds a set command from a point mapping and write payload.
     * Supports {@code {value}} substitution; otherwise appends the payload to a non-query mapping.
     */
    static String buildWriteCommand(String mapping, String payload) {
        String map = mapping == null ? "" : mapping.trim();
        String body = payload == null ? "" : payload;
        if (map.contains("{value}")) {
            return map.replace("{value}", body);
        }
        if (map.isBlank()) {
            return body;
        }
        if (isQuery(map)) {
            String base = map.substring(0, map.length() - 1).trim();
            return base.isBlank() ? body : base + " " + body;
        }
        return map + " " + body;
    }

    static boolean isQuery(String command) {
        return command != null && command.trim().endsWith("?");
    }

    private synchronized String query(String command) throws DriverException {
        try {
            writeLine(socket.getOutputStream(), command);
            return readLine(socket.getInputStream());
        } catch (IOException e) {
            throw new DriverException("SCPI query failed for " + host + ":" + port + " (" + command + ")", e);
        }
    }

    private synchronized void send(String command) throws DriverException {
        try {
            writeLine(socket.getOutputStream(), command);
        } catch (IOException e) {
            throw new DriverException("SCPI write failed for " + host + ":" + port + " (" + command + ")", e);
        }
    }

    private void closeSocket() {
        Socket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // disconnect is best-effort
            }
        }
    }

    static void writeLine(OutputStream out, String command) throws IOException {
        out.write((command.trim() + "\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int ch = in.read();
            if (ch < 0) {
                if (line.size() == 0) {
                    throw new IOException("EOF reading SCPI response");
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
        return line.toString(StandardCharsets.US_ASCII).trim();
    }

    private static String extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "payload", "data", "text", "raw", "command")) {
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
