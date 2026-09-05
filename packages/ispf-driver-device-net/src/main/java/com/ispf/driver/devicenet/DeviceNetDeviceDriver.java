package com.ispf.driver.devicenet;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.devicenet.codec.DeviceNetLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DeviceNet CIP gateway driver — ASCII lab over TCP (default port {@code 44818}).
 * <p>
 * Point forms: {@code node:1}, {@code node:1:attr:1}, {@code class:4:inst:1:attr:3}.
 * Reads/writes via {@link DeviceNetLabSession#readValue} / {@link DeviceNetLabSession#writeValue}.
 * <p>
 * Honesty: TCP CIP/DeviceNet gateway lab — not DeviceNet CAN PHY / ODVA stack. Lab ≠ field.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class DeviceNetDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("deviceNetValue")
            .field("value", FieldType.DOUBLE)
            .field("kind", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "device-net",
            "DeviceNet CIP Gateway Lab Driver",
            "0.1.0",
            "CIP/DeviceNet gateway ASCII lab over TCP (GET/SET node/class path);"
                    + " not DeviceNet CAN PHY / ODVA stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "44818",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 44818;
    private int timeoutMs = 3000;
    private DeviceNetLabSession session;
    private final Map<String, DeviceNetPoint> points = new ConcurrentHashMap<>();

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
            session = new DeviceNetLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "DeviceNet CIP gateway lab connected to " + host + ":" + port
                            + " (not DeviceNet CAN PHY / ODVA stack)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("DeviceNet lab connect failed for " + host + ":" + port, e);
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
            DeviceNetPoint point = DeviceNetPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                double value = session.readValue(point.wireToken());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("DeviceNet lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        DeviceNetPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        double numeric = extractNumeric(value);
        try {
            session.writeValue(point.wireToken(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("DeviceNet lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(DeviceNetPoint point, double value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "kind", point.kind().name().toLowerCase(Locale.ROOT),
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("DeviceNet write requires a value");
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
        throw new IllegalArgumentException("DeviceNet write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
