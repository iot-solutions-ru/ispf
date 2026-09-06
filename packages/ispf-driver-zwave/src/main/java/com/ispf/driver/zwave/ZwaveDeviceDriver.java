package com.ispf.driver.zwave;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.zwave.codec.ZwaveLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Z-Wave controller TCP gateway lab driver — newline JSON over TCP (default port {@code 3000}).
 * <p>
 * Point forms: {@code node:3}, {@code node:3:cmd:37}.
 * Both forms support write via {@link ZwaveLabSession#writeValue}.
 * <p>
 * Honesty: Z-Wave controller TCP gateway lab — not Z-Wave RF / serial API silicon.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only. Lab ≠ RF.
 */
public class ZwaveDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("zwaveValue")
            .field("value", FieldType.DOUBLE)
            .field("kind", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "zwave",
            "Z-Wave Controller Gateway Lab Driver",
            "0.1.0",
            "Z-Wave controller TCP gateway lab — not Z-Wave RF / serial API;"
                    + " newline JSON node / command-class get/set over TCP",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "3000",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 3000;
    private int timeoutMs = 3000;
    private ZwaveLabSession session;
    private final Map<String, ZwavePoint> points = new ConcurrentHashMap<>();

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
            session = new ZwaveLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "Z-Wave controller gateway lab connected to " + host + ":" + port
                            + " (not Z-Wave RF / serial API)");
        } catch (IOException e) {
            session = null;
            throw new DriverException(
                    "Z-Wave controller gateway lab connect failed for " + host + ":" + port, e);
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
            ZwavePoint point = ZwavePoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                double value = session.readValue(point.wireToken());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("Z-Wave controller gateway lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        ZwavePoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        double numeric = extractNumeric(value);
        try {
            session.writeValue(point.wireToken(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("Z-Wave controller gateway lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(ZwavePoint point, double value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "kind", point.kind() == ZwavePoint.Kind.CMD ? "cmd" : "node",
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("Z-Wave write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "raw", "cmd")) {
            Object candidate = row.get(key);
            if (candidate instanceof Number number) {
                return number.doubleValue();
            }
            if (candidate != null) {
                return Double.parseDouble(String.valueOf(candidate).trim());
            }
        }
        throw new IllegalArgumentException("Z-Wave write requires numeric value/raw/cmd");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
