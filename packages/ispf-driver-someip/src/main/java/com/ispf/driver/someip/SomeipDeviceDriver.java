package com.ispf.driver.someip;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.someip.codec.SomeipCodec;
import com.ispf.driver.someip.codec.SomeipSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AUTOSAR SOME/IP driver over TCP ({@code someip}).
 * <p>
 * Wire header is 16 bytes big-endian: serviceId, methodId, length (8 + payload), clientId,
 * sessionId, protocolVersion=1, interfaceVersion=1, messageType, returnCode. Reads send
 * REQUEST ({@code 0x00}) and expect RESPONSE ({@code 0x80}). Point mappings are
 * {@code service:method} such as {@code 0x0100:0x0001}.
 */
public class SomeipDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("someipValue")
            .field("value", FieldType.STRING)
            .field("data", FieldType.STRING)
            .field("service", FieldType.STRING)
            .field("method", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "someip",
            "SOME/IP Driver",
            "1.0.0",
            "AUTOSAR SOME/IP over TCP: 16-byte big-endian header, REQUEST/RESPONSE"
                    + " (messageType 0x00/0x80); service:method point mapping on port 30490.",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "30490",
                    "clientId", "0x0001",
                    "writeMode", "fireForget",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 30490;
    private int clientId = 0x0001;
    private String writeMode = "fireForget";
    private int timeoutMs = 3000;
    private SomeipSession session;
    private final Map<String, SomeipPoint> points = new ConcurrentHashMap<>();

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
            case "clientId" -> clientId = parseIntFlexible(value.trim());
            case "writeMode" -> writeMode = value.trim();
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            session = new SomeipSession(host, port, clientId, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "SOME/IP connected via TCP to " + host + ":" + port);
        } catch (IOException e) {
            session = null;
            throw new DriverException("SOME/IP connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        if (session != null) {
            session.close();
            session = null;
        }
        points.clear();
    }

    @Override
    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        ensureConnected();
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String mapping = entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId
                    : entry.getValue().trim();
            SomeipPoint point = SomeipPoint.parse(mapping);
            points.put(pointId, point);
            try {
                byte[] payload = session.request(point.service(), point.method(), new byte[0], true);
                publish(pointId, point, payload);
            } catch (IOException e) {
                throw new DriverException(
                        "SOME/IP read failed for " + point.formatService() + ":" + point.formatMethod(), e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        SomeipPoint point = points.get(pointId);
        if (point == null) {
            point = SomeipPoint.parse(pointId);
            points.put(pointId, point);
        }
        byte[] payload = extractPayload(value);
        boolean expectResponse = !"fireForget".equalsIgnoreCase(writeMode)
                && !"noReturn".equalsIgnoreCase(writeMode);
        try {
            byte[] responsePayload = session.request(
                    point.service(), point.method(), payload, expectResponse);
            publish(pointId, point, expectResponse ? responsePayload : payload);
        } catch (IOException e) {
            throw new DriverException(
                    "SOME/IP write failed for " + point.formatService() + ":" + point.formatMethod(), e);
        }
    }

    private void publish(String pointId, SomeipPoint point, byte[] payload) {
        String hex = SomeipCodec.toHex(payload);
        String text = SomeipCodec.tryUtf8(payload);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", text != null ? text : hex,
                "data", hex,
                "service", point.formatService(),
                "method", point.formatMethod(),
                "raw", hex
        )));
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }

    static byte[] extractPayload(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return new byte[0];
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("data", "value", "payload", "raw", "hex")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return toBytes(String.valueOf(candidate).trim());
            }
        }
        if (row.size() == 1) {
            return toBytes(String.valueOf(row.values().iterator().next()).trim());
        }
        throw new IllegalArgumentException("SOME/IP write requires value/data field");
    }

    static byte[] toBytes(String text) {
        if (text.isEmpty()) {
            return new byte[0];
        }
        if (text.matches("(?i)0x([0-9A-F]{2})+") || text.matches("(?i)([0-9A-F]{2})+")) {
            return SomeipCodec.fromHex(text);
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static int parseIntFlexible(String raw) {
        String text = raw.trim();
        if (text.regionMatches(true, 0, "0x", 0, 2)) {
            return Integer.parseInt(text.substring(2), 16);
        }
        if (text.matches("(?i)[0-9A-F]*[A-F][0-9A-F]*")) {
            return Integer.parseInt(text, 16);
        }
        return Integer.parseInt(text, 10);
    }
}
