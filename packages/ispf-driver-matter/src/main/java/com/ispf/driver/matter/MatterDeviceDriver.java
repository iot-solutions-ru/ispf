package com.ispf.driver.matter;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.matter.codec.MatterLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Matter/CHIP controller TCP gateway lab driver — newline JSON over TCP (default port {@code 5540}).
 * <p>
 * Point forms: {@code node:1:ep:1:cluster:OnOff:attr:OnOff}, {@code node:1:cmd:On}.
 * Attribute and command points support write via {@link MatterLabSession#writeValue}.
 * <p>
 * Honesty: Matter/CHIP controller gateway lab — not full CSA Matter / CHIP SDK / Thread/BLE
 * commissioning stack. Clean-room ISPF code, Apache-2.0 — JDK sockets only. Lab ≠ field.
 */
public class MatterDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("matterValue")
            .field("value", FieldType.DOUBLE)
            .field("kind", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "matter",
            "Matter Controller Gateway Lab Driver",
            "0.1.0",
            "Matter/CHIP controller gateway lab — newline JSON attr/cmd over TCP 5540;"
                    + " not full CSA Matter / CHIP SDK / Thread/BLE commissioning stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5540",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5540;
    private int timeoutMs = 3000;
    private MatterLabSession session;
    private final Map<String, MatterPoint> points = new ConcurrentHashMap<>();

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
            session = new MatterLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "Matter controller gateway lab connected to " + host + ":" + port
                            + " (not full CSA Matter / CHIP SDK / Thread/BLE commissioning)");
        } catch (IOException e) {
            session = null;
            throw new DriverException(
                    "Matter controller gateway lab connect failed for " + host + ":" + port, e);
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
            MatterPoint point = MatterPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                double value = session.readValue(point.wireToken());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("Matter controller gateway lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        MatterPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        double numeric = extractNumeric(value);
        try {
            session.writeValue(point.wireToken(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("Matter controller gateway lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(MatterPoint point, double value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "kind", point.kindToken(),
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("Matter write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "raw", "onoff")) {
            Object candidate = row.get(key);
            if (candidate instanceof Number number) {
                return number.doubleValue();
            }
            if (candidate != null) {
                return Double.parseDouble(String.valueOf(candidate).trim());
            }
        }
        throw new IllegalArgumentException("Matter write requires numeric value/raw/onoff");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
