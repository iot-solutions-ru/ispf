package com.ispf.driver.opchda;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.opchda.codec.OpcHdaLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OPC HDA HTTP/JSON historical data gateway lab driver — newline JSON over TCP (default port {@code 48081}).
 * <p>
 * Point forms: {@code item:Tag1}, {@code tag:Temperature}.
 * {@code readPoints} fetches last value / raw sample; {@code writePoint} inserts a lab sample via
 * {@link OpcHdaLabSession#writeValue}.
 * <p>
 * Honesty: HDA gateway lab — not OPC Classic HDA / DCOM.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only. Lab ≠ field.
 */
public class OpcHdaDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("opcHdaValue")
            .field("value", FieldType.DOUBLE)
            .field("kind", FieldType.STRING)
            .field("name", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "opc-hda",
            "OPC HDA HTTP/JSON Gateway Lab Driver",
            "0.1.0",
            "HDA gateway lab — not OPC Classic HDA / DCOM;"
                    + " HTTP/JSON last-value / raw sample poll and lab insert over TCP",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "48081",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 48081;
    private int timeoutMs = 3000;
    private OpcHdaLabSession session;
    private final Map<String, OpcHdaPoint> points = new ConcurrentHashMap<>();

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
            session = new OpcHdaLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "OPC HDA HTTP/JSON gateway lab connected to " + host + ":" + port
                            + " (not OPC Classic HDA / DCOM)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("OPC HDA gateway lab connect failed for " + host + ":" + port, e);
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
            OpcHdaPoint point = OpcHdaPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                double value = session.readValue(point.kindToken(), point.name());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("OPC HDA gateway lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        OpcHdaPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        double numeric = extractNumeric(value);
        try {
            session.writeValue(point.kindToken(), point.name(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("OPC HDA gateway lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(OpcHdaPoint point, double value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "kind", point.kindToken(),
                "name", point.name(),
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("OPC HDA write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "raw", "sample")) {
            Object candidate = row.get(key);
            if (candidate instanceof Number number) {
                return number.doubleValue();
            }
            if (candidate != null) {
                return Double.parseDouble(String.valueOf(candidate).trim());
            }
        }
        throw new IllegalArgumentException("OPC HDA write requires numeric value/raw/sample");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
