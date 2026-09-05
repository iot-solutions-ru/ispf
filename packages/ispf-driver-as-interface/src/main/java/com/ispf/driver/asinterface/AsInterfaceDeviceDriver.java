package com.ispf.driver.asinterface;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.asinterface.codec.AsInterfaceLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AS-Interface master/gateway driver — ASCII lab over TCP (default port {@code 9600}).
 * <p>
 * Honesty boundary: this talks to an ISPF AS-Interface-over-TCP gateway lab, not an AS-i
 * physical master and not the yellow-cable AS-Interface PHY. Lab dialect uses
 * {@code GET}/{@code SET} (aliases {@code RD}/{@code WR}) for digital slave points such as
 * {@code slave:3}, {@code slave:3:di0}, {@code slave:3:do1}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class AsInterfaceDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("asInterfaceValue")
            .field("value", FieldType.DOUBLE)
            .field("slave", FieldType.LONG)
            .field("channel", FieldType.STRING)
            .field("bit", FieldType.LONG)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "as-interface",
            "AS-Interface Gateway Lab Driver",
            "0.1.0",
            "AS-Interface master/gateway over TCP ASCII lab (GET/SET slave:N:di/do);"
                    + " not AS-i physical master / yellow cable",
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
    private AsInterfaceLabSession session;
    private final Map<String, AsInterfacePoint> points = new ConcurrentHashMap<>();

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
            session = new AsInterfaceLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "AS-Interface TCP gateway lab connected to " + host + ":" + port
                            + " (not AS-i physical master)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("AS-Interface lab connect failed for " + host + ":" + port, e);
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
            AsInterfacePoint point = AsInterfacePoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                double value = session.readValue(point.wireToken());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("AS-Interface lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        AsInterfacePoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        if (!point.writable()) {
            throw new DriverException("AS-Interface lab rejects writes for DI point: " + point.display());
        }
        double numeric = extractNumeric(value);
        try {
            session.writeValue(point.wireToken(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("AS-Interface lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(AsInterfacePoint point, double value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "slave", (long) point.slave(),
                "channel", point.channelLabel(),
                "bit", (long) point.bit(),
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("AS-Interface write requires a value");
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
        throw new IllegalArgumentException("AS-Interface write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
