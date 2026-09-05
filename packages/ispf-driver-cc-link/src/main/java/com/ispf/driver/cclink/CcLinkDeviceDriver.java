package com.ispf.driver.cclink;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.cclink.codec.CcLinkLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CC-Link SLMP/ASCII TCP gateway lab driver — register R/W over TCP (default port {@code 5001}).
 * <p>
 * Honesty boundary: TCP gateway / SLMP-shaped register R/W lab only — not CC-Link RS-485,
 * not IE Field ASIC, and not a CLPA protocol stack. Point forms: {@code D100}, {@code R0},
 * {@code W0}, {@code dev:D100}. Lab ≠ field.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class CcLinkDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("ccLinkValue")
            .field("value", FieldType.DOUBLE)
            .field("kind", FieldType.STRING)
            .field("address", FieldType.LONG)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "cc-link",
            "CC-Link SLMP/ASCII Gateway Lab Driver",
            "0.1.0",
            "CC-Link TCP gateway / SLMP-shaped register R/W lab (D/R/W points);"
                    + " not CC-Link RS-485 / IE Field ASIC / CLPA stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5001",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5001;
    private int timeoutMs = 3000;
    private CcLinkLabSession session;
    private final Map<String, CcLinkPoint> points = new ConcurrentHashMap<>();

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
            session = new CcLinkLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "CC-Link SLMP/ASCII gateway lab connected to " + host + ":" + port
                            + " (not CC-Link RS-485 / IE Field ASIC / CLPA stack)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("CC-Link lab connect failed for " + host + ":" + port, e);
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
            CcLinkPoint point = CcLinkPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                double value = session.readValue(point.wireToken());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("CC-Link lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        CcLinkPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        double numeric = extractNumeric(value);
        try {
            session.writeValue(point.wireToken(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("CC-Link lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(CcLinkPoint point, double value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "kind", point.kind(),
                "address", (long) point.address(),
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("CC-Link write requires a value");
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
        throw new IllegalArgumentException("CC-Link write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
