package com.ispf.driver.profibus;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.profibus.codec.ProfibusFdlSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PROFIBUS DP driver — FDL over TCP (serial-server), default port {@code 9600}.
 * <p>
 * Wire format is FDL SD1/SD2 (not an RS-485 PHY or FDL ASIC). Connect may send an SD1
 * station probe; slave/byte points are read and written with SD2 PDUs.
 * Point forms: {@code slave:3}, {@code slave:3:byte:0}. Not the PA pack.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class ProfibusDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("profibusValue")
            .field("value", FieldType.DOUBLE)
            .field("slave", FieldType.LONG)
            .field("byte", FieldType.LONG)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "profibus",
            "PROFIBUS DP FDL-over-TCP Driver",
            "1.0.0",
            "PROFIBUS DP FDL over TCP (serial-server): SD1 station probe, SD2 slave/byte R/W;"
                    + " not RS-485 DP PHY / FDL ASIC",
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
    private ProfibusFdlSession session;
    private final Map<String, ProfibusPoint> points = new ConcurrentHashMap<>();

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
            session = new ProfibusFdlSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "PROFIBUS DP FDL over TCP connected to " + host + ":" + port
                            + " (not RS-485 DP PHY / FDL ASIC)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("PROFIBUS FDL connect failed for " + host + ":" + port, e);
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
            ProfibusPoint point = ProfibusPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                double value = session.readValue(point.slave(), point.byteOffset());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("PROFIBUS FDL read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        ProfibusPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        double numeric = extractNumeric(value);
        try {
            session.writeValue(point.slave(), point.byteOffset(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("PROFIBUS FDL write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(ProfibusPoint point, double value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "slave", (long) point.slave(),
                "byte", (long) point.byteOffset(),
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("PROFIBUS write requires a value");
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
        throw new IllegalArgumentException("PROFIBUS write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
