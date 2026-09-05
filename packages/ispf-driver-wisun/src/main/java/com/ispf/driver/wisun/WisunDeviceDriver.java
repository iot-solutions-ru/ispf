package com.ispf.driver.wisun;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.wisun.codec.WisunLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wi-SUN border-router CoAP lab driver over TCP (port 5683).
 * <p>
 * Point forms: {@code node:1}, {@code /nodes/1/value}, {@code coap:/nodes/1/value}.
 * {@code writePoint} calls {@code session.writeValue(...)}.
 * <p>
 * Honesty: border-router CoAP lab — not Wi-SUN FAN PHY / FAN stack.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class WisunDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("wisunValue")
            .field("value", FieldType.DOUBLE)
            .field("path", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "wisun",
            "Wi-SUN Border-Router CoAP Lab Driver",
            "0.1.0",
            "Wi-SUN border-router CoAP lab: TCP GET/PUT on 5683;"
                    + " not Wi-SUN FAN PHY / FAN stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5683",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5683;
    private int timeoutMs = 3000;
    private WisunLabSession session;
    private final Map<String, WisunPoint> points = new ConcurrentHashMap<>();

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
            session = new WisunLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "Wi-SUN border-router CoAP lab connected to " + host + ":" + port
                            + " (not Wi-SUN FAN PHY / FAN stack)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("Wi-SUN lab connect failed for " + host + ":" + port, e);
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
            String mapping = entry.getValue() == null || entry.getValue().isBlank()
                    ? entry.getKey() : entry.getValue();
            WisunPoint point = WisunPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                float value = session.readValue(point.path());
                driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                        "value", (double) value,
                        "path", point.path()
                )));
            } catch (IOException e) {
                throw new DriverException("Wi-SUN lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        WisunPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        float numeric = (float) extractNumeric(value);
        try {
            session.writeValue(point.path(), numeric);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", (double) numeric,
                    "path", point.path()
            )));
        } catch (IOException e) {
            throw new DriverException("Wi-SUN lab write failed for " + pointId, e);
        }
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("Wi-SUN write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "raw")) {
            Object candidate = row.get(key);
            if (candidate instanceof Number number) {
                return number.doubleValue();
            }
            if (candidate != null) {
                return Double.parseDouble(String.valueOf(candidate).trim());
            }
        }
        throw new IllegalArgumentException("Wi-SUN write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
