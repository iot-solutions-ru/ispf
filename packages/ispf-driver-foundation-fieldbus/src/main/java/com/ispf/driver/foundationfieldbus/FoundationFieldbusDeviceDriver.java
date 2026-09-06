package com.ispf.driver.foundationfieldbus;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.foundationfieldbus.codec.FoundationFieldbusLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Foundation Fieldbus HSE/TCP gateway lab driver — ASCII RD/WR over TCP (default port {@code 1089}).
 * <p>
 * Honesty boundary: FF HSE/TCP gateway lab only — not native H1 modem, not LAS, and not a
 * Fieldbus Foundation protocol stack. Point forms: {@code ai:1}, {@code ao:2},
 * {@code device:0:pv}, {@code ff:1}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class FoundationFieldbusDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("foundationFieldbusValue")
            .field("value", FieldType.DOUBLE)
            .field("kind", FieldType.STRING)
            .field("index", FieldType.LONG)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "foundation-fieldbus",
            "Foundation Fieldbus HSE/TCP Gateway Lab Driver",
            "0.1.0",
            "FF HSE/TCP gateway lab — ASCII RD/WR for ai/ao/device PV/ff points;"
                    + " not native H1 / LAS / Fieldbus Foundation stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "1089",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 1089;
    private int timeoutMs = 3000;
    private FoundationFieldbusLabSession session;
    private final Map<String, FoundationFieldbusPoint> points = new ConcurrentHashMap<>();

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
            session = new FoundationFieldbusLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "Foundation Fieldbus HSE/TCP gateway lab connected to " + host + ":" + port
                            + " (not native H1 / LAS / Fieldbus Foundation stack)");
        } catch (IOException e) {
            session = null;
            throw new DriverException(
                    "Foundation Fieldbus lab connect failed for " + host + ":" + port, e);
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
            FoundationFieldbusPoint point = FoundationFieldbusPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                double value = session.readValue(point.wireToken());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("Foundation Fieldbus lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        FoundationFieldbusPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        double numeric = extractNumeric(value);
        try {
            session.writeValue(point.wireToken(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("Foundation Fieldbus lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(FoundationFieldbusPoint point, double value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "kind", point.kind(),
                "index", (long) point.index(),
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("Foundation Fieldbus write requires a value");
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
        throw new IllegalArgumentException("Foundation Fieldbus write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
