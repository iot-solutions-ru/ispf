package com.ispf.driver.profibuspa;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.profibuspa.codec.ProfibusPaLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PROFIBUS PA instrument gateway lab driver — ASCII RD/WR over TCP (default port {@code 9600}).
 * <p>
 * Honesty boundary: PROFIBUS PA-over-TCP gateway lab only — not a native PA PHY, not a DP/PA
 * coupler ASIC, and not RS-485 field wiring. Point forms: {@code slot:1}, {@code slot:1:pv},
 * {@code addr:12}, {@code pa:1}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class ProfibusPaDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("profibusPaValue")
            .field("value", FieldType.DOUBLE)
            .field("kind", FieldType.STRING)
            .field("index", FieldType.LONG)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "profibus-pa",
            "PROFIBUS PA-over-TCP Gateway Lab Driver",
            "0.1.0",
            "PROFIBUS PA-over-TCP gateway lab — ASCII RD/WR for slot/addr/pa instrument points;"
                    + " not native PA PHY / DP-PA coupler ASIC / RS-485",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "9600",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 9600;
    private int timeoutMs = 3000;
    private ProfibusPaLabSession session;
    private final Map<String, ProfibusPaPoint> points = new ConcurrentHashMap<>();

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
            session = new ProfibusPaLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "PROFIBUS PA-over-TCP gateway lab connected to " + host + ":" + port
                            + " (not native PA PHY / DP-PA coupler ASIC)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("PROFIBUS PA lab connect failed for " + host + ":" + port, e);
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
            ProfibusPaPoint point = ProfibusPaPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                double value = session.readValue(point.wireToken());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("PROFIBUS PA lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        ProfibusPaPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        double numeric = extractNumeric(value);
        try {
            session.writeValue(point.wireToken(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("PROFIBUS PA lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(ProfibusPaPoint point, double value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "kind", point.kind(),
                "index", (long) point.index(),
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("PROFIBUS PA write requires a value");
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
        throw new IllegalArgumentException("PROFIBUS PA write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
