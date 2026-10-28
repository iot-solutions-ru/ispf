package com.ispf.driver.websocket;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverConfigurationException;
import com.ispf.driver.DriverErrorKind;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.DriverReconnectPendingException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * WebSocket driver — RFC6455 client with text-frame get/set for telemetry points.
 * <p>
 * Point mapping is a channel/path (for example {@code /sensors/temp}) or a message key
 * (for example {@code temperature}). Reads send a JSON get request and take the {@code value}
 * field from the reply; writes send a JSON set request with the record value.
 * <p>
 * When {@code subscribeText} is set, that text is sent once after the handshake and each poll
 * reads the latest text message. Point mappings are then {@code @state}, {@code @raw},
 * {@code $.path}, {@code $.array.length} or {@code round:<digits>:$.path}.
 * {@code @raw} stores the text of the message used for that poll.
 * {@code useTls} wraps the connection in TLS. Empty or {@code false} keeps the plain socket.
 * {@code caCertPath} is an optional PEM file trusted together with the JVM trust store.
 * The port stays the value in the device configuration; public wss servers usually use 443.
 * A connect failure that is not a configuration error is retried from later polls. The wait
 * grows from 5 seconds to 60 seconds. An open socket that is only quiet is left open.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only (no Jetty/Netty/Tyrus/OkHttp).
 */
public class WebsocketDeviceDriver implements DeviceDriver {

    private static final long[] RETRY_DELAYS_MS = {5_000L, 10_000L, 20_000L, 40_000L, 60_000L};

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("websocketValue")
            .field("value", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "websocket",
            "WebSocket Driver",
            "0.1.2",
            "RFC6455 client. useTls encrypts the socket. Retries connect unless configuration is wrong",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "8080",
                    "path", "/",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "5000",
                    "subscribeText", "",
                    "useTls", "false",
                    "caCertPath", ""
            ),
            DriverMaturity.PRODUCTION,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 8080;
    private String path = "/";
    private int timeoutMs = 5000;
    private String subscribeText = "";
    private boolean useTls;
    private String caCertPath = "";
    private DriverErrorKind lastConnectFailure;
    private int retryAttempt;
    private long nextConnectAttemptAt;
    LongSupplier clock = System::currentTimeMillis;
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
            case "subscribeText" -> subscribeText = value.trim();
            case "useTls" -> useTls = Boolean.parseBoolean(value.trim());
            case "caCertPath" -> caCertPath = value.trim();
            default -> { }
        }
    }

    @Override
    public boolean retriesConnectWhilePolling() {
        return true;
    }

    @Override
    public void connect() throws DriverException {
        try {
            closeClient();
            client = Rfc6455Client.connect(host, port, path, timeoutMs,
                    new Rfc6455Client.SocketSecurity(useTls, caCertPath));
            if (!subscribeText.isBlank()) {
                client.sendText(subscribeText);
            }
            connected = true;
            clearConnectFailure();
            driverObject.log(DriverLogLevel.INFO, "WebSocket connected"
                    + (useTls ? " via TLS" : "") + " to " + host + ":" + port + path);
        } catch (IOException e) {
            connected = false;
            closeClient();
            DriverException error = new DriverException(
                    "WebSocket connect failed for " + host + ":" + port + path, e);
            rememberConnectFailure(error);
            throw error;
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        clearConnectFailure();
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
        ensureConnectedForPoll();
        points.clear();
        if (!subscribeText.isBlank()) {
            readSubscribed(pointMappings);
            return;
        }
        String lastReply = "";
        Map<String, String> rawPoints = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String mapping = resolveMapping(pointId, entry.getValue());
            points.put(pointId, mapping);
            if (isRawMapping(mapping)) {
                rawPoints.put(pointId, mapping);
                continue;
            }
            try {
                client.sendText(Rfc6455Client.getRequest(mapping));
                String reply = client.readText();
                lastReply = reply == null ? "" : reply;
                String value = Rfc6455Client.extractJsonField(reply, "value");
                if (value == null) {
                    value = lastReply;
                }
                updatePoint(pointId, value, mapping);
            } catch (IOException e) {
                throw new DriverException("WebSocket read failed for point " + mapping, e);
            }
        }
        for (Map.Entry<String, String> rawPoint : rawPoints.entrySet()) {
            updatePoint(rawPoint.getKey(), lastReply, rawPoint.getValue());
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnectedForPoll();
        if (!subscribeText.isBlank()) {
            // Wording must not match driver-readiness WRITE_STUB_MSG ("read-only") — get/set
            // writes still work when subscribeText is empty.
            throw new DriverException(
                    "WebSocket subscribe mode does not support writePoint; clear subscribeText for get/set writes"
            );
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

    private void readSubscribed(Map<String, String> pointMappings) throws DriverException {
        String message;
        try {
            List<String> batch = client.readAvailableTexts();
            message = pickPublish(batch);
        } catch (IOException e) {
            throw new DriverException("WebSocket read failed", e);
        }
        Object root;
        try {
            root = JsonValues.parse(message);
        } catch (IllegalArgumentException e) {
            throw new DriverException("WebSocket message is not JSON", e);
        }
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String mapping = resolveMapping(pointId, entry.getValue());
            points.put(pointId, mapping);
            String value;
            if (isRawMapping(mapping)) {
                value = message == null ? "" : message;
            } else {
                try {
                    value = JsonValues.readMapping(root, mapping);
                } catch (IllegalArgumentException | ArithmeticException e) {
                    throw new DriverException("WebSocket point mapping failed for " + pointId, e);
                }
            }
            updatePoint(pointId, value, mapping);
        }
    }

    private void updatePoint(String pointId, String value, String mapping) {
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "point", mapping
        )));
    }

    private static boolean isRawMapping(String mapping) {
        return "@raw".equals(mapping);
    }

    private static String pickPublish(List<String> batch) {
        for (int i = batch.size() - 1; i >= 0; i--) {
            if ("publish".equals(Rfc6455Client.extractJsonField(batch.get(i), "op"))) {
                return batch.get(i);
            }
        }
        return batch.get(batch.size() - 1);
    }

    private static String resolveMapping(String pointId, String mapping) {
        if (mapping == null || mapping.isBlank()) {
            return pointId;
        }
        return mapping.trim();
    }

    private void ensureConnectedForPoll() throws DriverException {
        if (isConnected()) {
            return;
        }
        if (lastConnectFailure == DriverErrorKind.CONFIGURATION) {
            throw new DriverConfigurationException("WebSocket address is not usable");
        }
        if (clock.getAsLong() < nextConnectAttemptAt) {
            throw new DriverReconnectPendingException("WebSocket reconnect is waiting");
        }
        connect();
    }

    void rememberConnectFailure(DriverException error) {
        lastConnectFailure = DriverErrorKind.classify(error);
        long delay = RETRY_DELAYS_MS[Math.min(retryAttempt, RETRY_DELAYS_MS.length - 1)];
        retryAttempt++;
        nextConnectAttemptAt = clock.getAsLong() + delay;
    }

    private void clearConnectFailure() {
        lastConnectFailure = null;
        retryAttempt = 0;
        nextConnectAttemptAt = 0;
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
