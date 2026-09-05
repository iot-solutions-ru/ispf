package com.ispf.driver.zigbee;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.zigbee.codec.ZigbeeLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zigbee ZCL coordinator TCP gateway lab driver — newline JSON over TCP (default port {@code 17754}).
 * <p>
 * Point forms: {@code nwk:0x1234:ep:1:cluster:0x0402:attr:0}, {@code ieee:00124b0001234567}.
 * ZCL attribute points support write via {@link ZigbeeLabSession#writeValue}; IEEE address is read-only.
 * <p>
 * Honesty: ZCL coordinator TCP gateway lab — not 802.15.4 radio / NCP silicon.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only. Lab ≠ RF.
 */
public class ZigbeeDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("zigbeeValue")
            .field("value", FieldType.DOUBLE)
            .field("kind", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "zigbee",
            "Zigbee ZCL Coordinator Gateway Lab Driver",
            "0.1.0",
            "ZCL coordinator TCP gateway lab — not 802.15.4 radio / NCP;"
                    + " newline JSON attribute get/set and IEEE poll over TCP",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "17754",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 17754;
    private int timeoutMs = 3000;
    private ZigbeeLabSession session;
    private final Map<String, ZigbeePoint> points = new ConcurrentHashMap<>();

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
            session = new ZigbeeLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "Zigbee ZCL coordinator gateway lab connected to " + host + ":" + port
                            + " (not 802.15.4 radio / NCP)");
        } catch (IOException e) {
            session = null;
            throw new DriverException(
                    "Zigbee ZCL gateway lab connect failed for " + host + ":" + port, e);
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
            ZigbeePoint point = ZigbeePoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                double value = session.readValue(point.wireToken());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("Zigbee ZCL gateway lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        ZigbeePoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        if (!point.writable()) {
            throw new DriverException(
                    "Zigbee ZCL gateway lab rejects writes for ieee point: " + point.display());
        }
        double numeric = extractNumeric(value);
        try {
            session.writeValue(point.wireToken(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("Zigbee ZCL gateway lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(ZigbeePoint point, double value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "kind", point.kind() == ZigbeePoint.Kind.ZCL_ATTR ? "attr" : "ieee",
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("Zigbee write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "attr", "raw")) {
            Object candidate = row.get(key);
            if (candidate instanceof Number number) {
                return number.doubleValue();
            }
            if (candidate != null) {
                return Double.parseDouble(String.valueOf(candidate).trim());
            }
        }
        throw new IllegalArgumentException("Zigbee write requires numeric value/attr/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
