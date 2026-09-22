package com.ispf.driver.ocpp;

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
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OCPP 1.6 JSON Charge Point client — BootNotification, Heartbeat, StatusNotification subset.
 * <p>
 * Transport is <strong>RFC 6455 WebSocket</strong> with {@code Sec-WebSocket-Protocol: ocpp1.6}
 * and OCPP 1.6 JSON CALL/CALLRESULT text frames. Point mapping:
 * {@code boot} / {@code BootNotification}, {@code heartbeat} / {@code Heartbeat},
 * {@code status} / {@code StatusNotification}, or {@code status:&lt;ConnectorId&gt;}.
 * Write updates connector status and sends StatusNotification.
 * <p>
 * Public OCPP 1.6 JSON schema only (Open Charge Alliance). Clean-room ISPF code, Apache-2.0 —
 * JDK sockets only; no third-party OCPP stack.
 */
public class OcppDeviceDriver implements DeviceDriver {

    private static final String WS_KEY = "dGhlIHNhbXBsZSBub25jZQ==";
    private static final String WS_ACCEPT = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=";
    private static final String WS_PATH = "/ocpp";

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("ocppValue")
            .field("value", FieldType.STRING)
            .field("action", FieldType.STRING)
            .field("status", FieldType.STRING)
            .field("currentTime", FieldType.STRING)
            .field("connectorId", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ocpp",
            "OCPP Driver",
            "0.1.0",
            "OCPP 1.6 JSON Charge Point subset (BootNotification/Heartbeat/StatusNotification) over WebSocket",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "9000",
                    "timeoutMs", "3000",
                    "chargePointVendor", "ISPF",
                    "chargePointModel", "ISPF-CP",
                    "chargePointSerialNumber", "CP-001",
                    "connectorId", "1",
                    "connectorStatus", "Available",
                    "pollIntervalMs", "30000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 9000;
    private int timeoutMs = 3000;
    private String chargePointVendor = "ISPF";
    private String chargePointModel = "ISPF-CP";
    private String chargePointSerialNumber = "CP-001";
    private int connectorId = 1;
    private String connectorStatus = "Available";
    private final Map<String, String> points = new ConcurrentHashMap<>();
    private final AtomicLong nextUniqueId = new AtomicLong(1);
    private volatile boolean connected;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private String bootStatus = "";
    private String lastCurrentTime = "";

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
            case "chargePointVendor" -> chargePointVendor = value.trim();
            case "chargePointModel" -> chargePointModel = value.trim();
            case "chargePointSerialNumber" -> chargePointSerialNumber = value.trim();
            case "connectorId" -> connectorId = Integer.parseInt(value.trim());
            case "connectorStatus" -> connectorStatus = value.trim();
            default -> { }
        }
    }

    @Override
    public synchronized void connect() throws DriverException {
        if (connected) {
            return;
        }
        try {
            openWebSocket();
            Map<String, String> boot = exchange("BootNotification", Map.of(
                    "chargePointVendor", chargePointVendor,
                    "chargePointModel", chargePointModel,
                    "chargePointSerialNumber", chargePointSerialNumber
            ));
            bootStatus = boot.getOrDefault("status", "");
            lastCurrentTime = boot.getOrDefault("currentTime", Instant.now().toString());
            if (!"Accepted".equalsIgnoreCase(bootStatus) && !"Pending".equalsIgnoreCase(bootStatus)) {
                throw new DriverException("OCPP BootNotification rejected: status=" + bootStatus);
            }
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "OCPP 1.6 WebSocket Charge Point ready for " + host + ":" + port
                            + " (boot=" + bootStatus + ")");
        } catch (DriverException e) {
            closeQuietly();
            throw e;
        } catch (IOException e) {
            closeQuietly();
            throw new DriverException("OCPP connect failed for " + host + ":" + port, e);
        }
    }

    /** Same-package tests: mark transport ready after {@link #openWebSocket()} without BootNotification. */
    synchronized void connectedForTest() {
        connected = true;
    }

    /**
     * Opens the TCP socket and completes the RFC 6455 client handshake (no OCPP CALL yet).
     * Same-package tests use this to send a first CALL with uniqueId {@code "1"}.
     */
    synchronized void openWebSocket() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        socket.setTcpNoDelay(true);
        in = socket.getInputStream();
        out = socket.getOutputStream();

        String upgrade =
                "GET " + WS_PATH + " HTTP/1.1\r\n"
                        + "Host: " + host + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: " + WS_KEY + "\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "Sec-WebSocket-Protocol: ocpp1.6\r\n"
                        + "\r\n";
        out.write(upgrade.getBytes(StandardCharsets.US_ASCII));
        out.flush();

        String statusLine = readHttpLine(in);
        if (statusLine == null || !statusLine.toUpperCase(Locale.ROOT).contains("101")) {
            throw new IOException("WebSocket handshake failed: " + statusLine);
        }
        String acceptHeader = null;
        while (true) {
            String line = readHttpLine(in);
            if (line == null || line.isEmpty()) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if ("Sec-WebSocket-Accept".equalsIgnoreCase(name)) {
                    acceptHeader = value;
                }
            }
        }
        if (!WS_ACCEPT.equals(acceptHeader)) {
            throw new IOException("Invalid Sec-WebSocket-Accept: " + acceptHeader);
        }
    }

    @Override
    public synchronized void disconnect() {
        connected = false;
        closeQuietly();
        points.clear();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public synchronized void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String mapping = entry.getValue() == null || entry.getValue().isBlank() ? pointId : entry.getValue();
            points.put(pointId, mapping);
            driverObject.updateVariable(pointId, readMapped(mapping));
        }
    }

    @Override
    public synchronized void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String mapping = points.getOrDefault(pointId, pointId);
        String status = extractValue(value);
        if (status == null || status.isBlank()) {
            throw new DriverException("OCPP write requires a connector status value");
        }
        connectorStatus = status.trim();
        int cid = parseConnectorId(mapping);
        Map<String, String> response = exchange("StatusNotification", Map.of(
                "connectorId", cid,
                "errorCode", "NoError",
                "status", connectorStatus
        ));
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", connectorStatus,
                "action", "StatusNotification",
                "status", connectorStatus,
                "currentTime", response.getOrDefault("currentTime", lastCurrentTime),
                "connectorId", cid
        )));
    }

    private DataRecord readMapped(String mapping) throws DriverException {
        String kind = mapping.trim();
        String lower = kind.toLowerCase(Locale.ROOT);
        if (lower.equals("boot") || lower.equals("bootnotification")) {
            Map<String, String> boot = exchange("BootNotification", Map.of(
                    "chargePointVendor", chargePointVendor,
                    "chargePointModel", chargePointModel,
                    "chargePointSerialNumber", chargePointSerialNumber
            ));
            bootStatus = boot.getOrDefault("status", bootStatus);
            lastCurrentTime = boot.getOrDefault("currentTime", lastCurrentTime);
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", bootStatus,
                    "action", "BootNotification",
                    "status", bootStatus,
                    "currentTime", lastCurrentTime,
                    "connectorId", connectorId
            ));
        }
        if (lower.equals("heartbeat") || lower.equals("hb")) {
            Map<String, String> hb = exchange("Heartbeat", Map.of());
            lastCurrentTime = hb.getOrDefault("currentTime", lastCurrentTime);
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", lastCurrentTime,
                    "action", "Heartbeat",
                    "status", bootStatus,
                    "currentTime", lastCurrentTime,
                    "connectorId", connectorId
            ));
        }
        int cid = parseConnectorId(kind);
        Map<String, String> st = exchange("StatusNotification", Map.of(
                "connectorId", cid,
                "errorCode", "NoError",
                "status", connectorStatus
        ));
        if (st.containsKey("currentTime")) {
            lastCurrentTime = st.get("currentTime");
        }
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", connectorStatus,
                "action", "StatusNotification",
                "status", connectorStatus,
                "currentTime", lastCurrentTime,
                "connectorId", cid
        ));
    }

    private int parseConnectorId(String mapping) {
        String lower = mapping.toLowerCase(Locale.ROOT).trim();
        if (lower.equals("status") || lower.equals("statusnotification")) {
            return connectorId;
        }
        if (lower.startsWith("status:")) {
            return Integer.parseInt(lower.substring("status:".length()).trim());
        }
        if (lower.startsWith("statusnotification:")) {
            return Integer.parseInt(lower.substring("statusnotification:".length()).trim());
        }
        try {
            return Integer.parseInt(mapping.trim());
        } catch (NumberFormatException e) {
            return connectorId;
        }
    }

    private Map<String, String> exchange(String action, Map<String, ?> payload) throws DriverException {
        String uniqueId = Long.toString(nextUniqueId.getAndIncrement());
        String call = OcppJson.call(uniqueId, action, payload);
        try {
            writeMaskedText(call);
            String responseText = readTextFrame();
            OcppJson.ParsedMessage parsed = OcppJson.parse(responseText);
            if (parsed.type() == 4) {
                throw new DriverException("OCPP CallError for " + action + ": "
                        + parsed.payload().getOrDefault("errorCode", "?")
                        + " " + parsed.payload().getOrDefault("errorDescription", ""));
            }
            if (parsed.type() != 3) {
                throw new DriverException("OCPP expected CALLRESULT for " + action + ", got type=" + parsed.type());
            }
            if (!uniqueId.equals(parsed.uniqueId())) {
                throw new DriverException("OCPP uniqueId mismatch for " + action);
            }
            return parsed.payload();
        } catch (DriverException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new DriverException("OCPP " + action + " failed for " + host + ":" + port, e);
        }
    }

    private void writeMaskedText(String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        if (payload.length > 125) {
            throw new IOException("OCPP WebSocket text frame too large: " + payload.length);
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream(6 + payload.length);
        buf.write(0x81);
        buf.write(0x80 | payload.length);
        buf.write(0);
        buf.write(0);
        buf.write(0);
        buf.write(0);
        buf.write(payload);
        out.write(buf.toByteArray());
        out.flush();
    }

    private String readTextFrame() throws IOException {
        while (true) {
            int b0 = in.read();
            if (b0 < 0) {
                throw new IOException("CSMS closed connection");
            }
            int opcode = b0 & 0x0F;
            int b1 = in.read();
            if (b1 < 0) {
                throw new IOException("EOF reading WebSocket length");
            }
            boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7F;
            if (len == 126) {
                int hi = in.read();
                int lo = in.read();
                if (hi < 0 || lo < 0) {
                    throw new IOException("EOF reading extended length");
                }
                len = ((hi & 0xFF) << 8) | (lo & 0xFF);
            } else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) {
                    int b = in.read();
                    if (b < 0) {
                        throw new IOException("EOF reading 64-bit length");
                    }
                    len = (len << 8) | (b & 0xFF);
                }
            }
            if (len > 1_000_000) {
                throw new IOException("WebSocket frame too large");
            }
            byte[] mask = null;
            if (masked) {
                mask = in.readNBytes(4);
                if (mask.length != 4) {
                    throw new IOException("EOF reading mask");
                }
            }
            byte[] payload = in.readNBytes((int) len);
            if (payload.length != len) {
                throw new IOException("Truncated WebSocket payload");
            }
            if (mask != null) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] = (byte) (payload[i] ^ mask[i % 4]);
                }
            }
            switch (opcode) {
                case 0x1 -> {
                    return new String(payload, StandardCharsets.UTF_8);
                }
                case 0x8 -> throw new IOException("WebSocket closed by peer");
                case 0x9 -> writeMaskedControl(0xA, payload);
                case 0xA -> { }
                default -> throw new IOException("Unsupported WebSocket opcode: " + opcode);
            }
        }
    }

    private void writeMaskedControl(int opcode, byte[] payload) throws IOException {
        if (payload.length > 125) {
            throw new IOException("Control frame too large");
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream(6 + payload.length);
        buf.write(0x80 | (opcode & 0x0F));
        buf.write(0x80 | payload.length);
        buf.write(0);
        buf.write(0);
        buf.write(0);
        buf.write(0);
        buf.write(payload);
        out.write(buf.toByteArray());
        out.flush();
    }

    private static String readHttpLine(InputStream input) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
        int prev = -1;
        while (true) {
            int b = input.read();
            if (b < 0) {
                if (buf.size() == 0) {
                    return null;
                }
                break;
            }
            if (prev == '\r' && b == '\n') {
                byte[] raw = buf.toByteArray();
                return new String(raw, 0, raw.length - 1, StandardCharsets.US_ASCII);
            }
            buf.write(b);
            prev = b;
        }
        return buf.toString(StandardCharsets.US_ASCII);
    }

    private void closeQuietly() {
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        out = null;
        in = null;
        socket = null;
    }

    private static String extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "status", "payload", "data", "text", "raw")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return String.valueOf(candidate);
            }
        }
        if (row.size() == 1) {
            return String.valueOf(row.values().iterator().next());
        }
        return "";
    }
}
