package com.ispf.driver.wirelesshart;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.wirelesshart.codec.WirelesshartLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WirelessHART gateway TCP lab driver — cmd/PV style on port 5094.
 * <p>
 * Point mapping: {@code pv}, {@code cmd:1}, {@code device:0}, {@code device:0:cmd:1}.
 * {@code writePoint} calls {@code session.writeValue(...)}.
 * <p>
 * Honesty: gateway TCP lab — not 802.15.4 WirelessHART radio / HCF stack.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class WirelesshartDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("wirelesshartValue")
            .field("value", FieldType.DOUBLE)
            .field("command", FieldType.LONG)
            .field("device", FieldType.LONG)
            .field("unit", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "wirelesshart",
            "WirelessHART Gateway Lab Driver",
            "0.1.0",
            "WirelessHART gateway TCP lab: cmd/PV GET/SET on 5094;"
                    + " not 802.15.4 WirelessHART radio / HCF stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5094",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5094;
    private int timeoutMs = 3000;
    private WirelesshartLabSession session;
    private final Map<String, WirelesshartPoint> points = new ConcurrentHashMap<>();

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
            session = new WirelesshartLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "WirelessHART gateway lab connected to " + host + ":" + port
                            + " (not 802.15.4 radio / HCF stack)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("WirelessHART lab connect failed for " + host + ":" + port, e);
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
            WirelesshartPoint point = WirelesshartPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                float pv = session.readPrimaryVariable(point.deviceAddress(), point.command());
                driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                        "value", (double) pv,
                        "command", (long) point.command(),
                        "device", (long) point.deviceAddress(),
                        "unit", "lab"
                )));
            } catch (IOException e) {
                throw new DriverException("WirelessHART lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        WirelesshartPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        float numeric = (float) extractNumeric(value);
        try {
            session.writeValue(point.deviceAddress(), point.command(), numeric);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", (double) numeric,
                    "command", (long) point.command(),
                    "device", (long) point.deviceAddress(),
                    "unit", "lab"
            )));
        } catch (IOException e) {
            throw new DriverException("WirelessHART lab write failed for " + pointId, e);
        }
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("WirelessHART write requires a value");
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
        throw new IllegalArgumentException("WirelessHART write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
