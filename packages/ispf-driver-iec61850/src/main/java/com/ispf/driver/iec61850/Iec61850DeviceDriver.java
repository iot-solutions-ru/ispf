package com.ispf.driver.iec61850;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.iec61850.codec.Iec61850LabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IEC 61850 MMS client lab driver — minimal request/response over TCP
 * (default port {@code 102}).
 * <p>
 * Honesty boundary: MMS client lab subset only — not a full IEC 61850 / SCL /
 * 61850-8-1 stack. Point forms: {@code LD0/MMXU1.TotW.mag.f},
 * {@code IED1/LLN0.Mod.stVal}. Lab ≠ substation field.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class Iec61850DeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("iec61850Value")
            .field("value", FieldType.DOUBLE)
            .field("quality", FieldType.STRING)
            .field("kind", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "iec61850",
            "IEC 61850 MMS Lab Driver",
            "0.1.0",
            "IEC 61850 MMS client lab subset over TCP (Operate/Write + read);"
                    + " not full IEC 61850 / SCL / 61850-8-1 stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "102",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 102;
    private int timeoutMs = 3000;
    private Iec61850LabSession session;
    private final Map<String, Iec61850Point> points = new ConcurrentHashMap<>();

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
            session = new Iec61850LabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "IEC 61850 MMS-lab connected to " + host + ":" + port
                            + " (not full IEC 61850 / SCL / 61850-8-1 stack)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("IEC 61850 MMS-lab connect failed for " + host + ":" + port, e);
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
            Iec61850Point point = Iec61850Point.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                double value = session.readValue(point.wireToken());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value, "good"));
            } catch (IOException e) {
                throw new DriverException("IEC 61850 MMS-lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        Iec61850Point point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        double numeric = extractNumeric(value);
        try {
            session.writeValue(point.wireToken(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric, "good"));
        } catch (IOException e) {
            throw new DriverException("IEC 61850 MMS-lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(Iec61850Point point, double value, String quality) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "quality", quality,
                "kind", point.kind(),
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("IEC 61850 MMS-lab write requires a value");
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
        throw new IllegalArgumentException("IEC 61850 MMS-lab write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
