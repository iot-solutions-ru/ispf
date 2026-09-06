package com.ispf.driver.ocpp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
 * Transport is <strong>newline-delimited OCPP-J CALL/CALLRESULT</strong> over TCP (lab-friendly
 * stand-in for the OCPP 1.6 WebSocket subprotocol). Point mapping:
 * {@code boot} / {@code BootNotification}, {@code heartbeat} / {@code Heartbeat},
 * {@code status} / {@code StatusNotification}, or {@code status:&lt;ConnectorId&gt;}.
 * Write updates connector status and sends StatusNotification.
 * <p>
 * Public OCPP 1.6 JSON schema only (Open Charge Alliance). Clean-room ISPF code, Apache-2.0 —
 * JDK sockets only; no third-party OCPP stack.
 */
public class OcppDeviceDriver implements DeviceDriver {

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
            "OCPP 1.6 JSON Charge Point subset (BootNotification/Heartbeat/StatusNotification) over TCP JSON lines",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "9000",
                    "timeoutMs", "3000",
                    "chargePointVendor", "ISPF",
                    "chargePointModel", "LabCP",
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
    private String chargePointModel = "LabCP";
    private String chargePointSerialNumber = "CP-001";
    private int connectorId = 1;
    private String connectorStatus = "Available";
    private final Map<String, String> points = new ConcurrentHashMap<>();
    private final AtomicLong nextUniqueId = new AtomicLong(1);
    private volatile boolean connected;
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
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
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
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
                    "OCPP 1.6 JSON-lines Charge Point ready for " + host + ":" + port
                            + " (boot=" + bootStatus + ")");
        } catch (DriverException e) {
            closeQuietly();
            throw e;
        } catch (IOException e) {
            closeQuietly();
            throw new DriverException("OCPP connect failed for " + host + ":" + port, e);
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
        String line = OcppJson.call(uniqueId, action, payload);
        try {
            writer.write(line);
            writer.write('\n');
            writer.flush();
            String responseLine = reader.readLine();
            if (responseLine == null) {
                throw new IOException("CSMS closed connection");
            }
            OcppJson.ParsedMessage parsed = OcppJson.parse(responseLine);
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

    private void closeQuietly() {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        writer = null;
        reader = null;
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
