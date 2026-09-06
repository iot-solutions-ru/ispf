package com.ispf.driver.websocket;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket driver — RFC6455 client with text-frame get/set for telemetry points.
 * <p>
 * Point mapping is a channel/path (for example {@code /sensors/temp}) or a message key
 * (for example {@code temperature}). Reads send a JSON get request and take the {@code value}
 * field from the reply; writes send a JSON set request with the record value.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only (no Jetty/Netty/Tyrus/OkHttp).
 */
public class WebsocketDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("websocketValue")
            .field("value", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "websocket",
            "WebSocket Driver",
            "0.1.0",
            "Connects via RFC6455 and exchanges JSON text frames for channel/path or message-key points",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "8080",
                    "path", "/",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "5000"
            ),
            DriverMaturity.PRODUCTION,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 8080;
    private String path = "/";
    private int timeoutMs = 5000;
    private final Map<String, String> points = new ConcurrentHashMap<>();
    private Rfc6455Client client;
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
            case "path" -> path = Rfc6455Client.normalizePath(value);
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            closeClient();
            client = Rfc6455Client.connect(host, port, path, timeoutMs);
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "WebSocket connected to " + host + ":" + port + path);
        } catch (IOException e) {
            connected = false;
            throw new DriverException("WebSocket connect failed for " + host + ":" + port + path, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        closeClient();
        points.clear();
        if (driverObject != null) {
            driverObject.log(DriverLogLevel.INFO, "WebSocket disconnected");
        }
    }

    @Override
    public boolean isConnected() {
        return connected && client != null && client.isOpen();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String mapping = resolveMapping(pointId, entry.getValue());
            points.put(pointId, mapping);
            try {
                client.sendText(Rfc6455Client.getRequest(mapping));
                String reply = client.readText();
                String value = Rfc6455Client.extractJsonField(reply, "value");
                if (value == null) {
                    value = reply == null ? "" : reply;
                }
                driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                        "value", value,
                        "point", mapping
                )));
            } catch (IOException e) {
                throw new DriverException("WebSocket read failed for point " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String mapping = points.getOrDefault(pointId, pointId);
        String payload = extractValue(value);
        try {
            client.sendText(Rfc6455Client.setRequest(mapping, payload));
            String reply = client.readText();
            String echoed = Rfc6455Client.extractJsonField(reply, "value");
            if (echoed == null) {
                echoed = payload;
            }
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", echoed,
                    "point", mapping
            )));
        } catch (IOException e) {
            throw new DriverException("WebSocket write failed for point " + mapping, e);
        }
    }

    private static String resolveMapping(String pointId, String mapping) {
        if (mapping == null || mapping.isBlank()) {
            return pointId;
        }
        return mapping.trim();
    }

    private void closeClient() {
        if (client != null) {
            client.close();
            client = null;
        }
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
