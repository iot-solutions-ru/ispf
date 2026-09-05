package com.ispf.driver.eebus;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.eebus.codec.EebusLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EEBus SHIP/SPINE-lite over TCP lab driver — ASCII GET/SET (not full EEBus SHIP TLS / SDK).
 * <p>
 * Point forms: {@code power}, {@code setpoint}, {@code entity:ElectricalConnection:power}.
 * Speaks to a SPINE-lite TCP lab on {@code host:port} (default 4712). Honesty: TCP SPINE-lite
 * lab only — not a full EEBus SHIP TLS stack and not an official EEBus SDK.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class EebusDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("eebusValue")
            .field("value", FieldType.DOUBLE)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "eebus",
            "EEBus SHIP/SPINE-lite TCP Lab Driver",
            "0.1.0",
            "EEBus SHIP/SPINE-lite over TCP lab: ASCII GET/SET power and setpoint;"
                    + " TCP SPINE-lite lab — not full EEBus SHIP TLS stack, not official EEBus SDK",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "4712",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 4712;
    private int timeoutMs = 3000;
    private EebusLabSession session;
    private final Map<String, EebusPoint> points = new ConcurrentHashMap<>();

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
            session = new EebusLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "EEBus SPINE-lite TCP lab connected to " + host + ":" + port
                            + " (not full EEBus/SHIP)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("EEBus lab connect failed for " + host + ":" + port, e);
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
            EebusPoint point = EebusPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                float value = session.readValue(point.gatewayToken());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("EEBus lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        EebusPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        float numeric = (float) extractNumeric(value);
        try {
            session.writeValue(point.gatewayToken(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("EEBus lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(EebusPoint point, float value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", (double) value,
                "point", point.gatewayToken()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("EEBus write requires a value");
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
        throw new IllegalArgumentException("EEBus write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
