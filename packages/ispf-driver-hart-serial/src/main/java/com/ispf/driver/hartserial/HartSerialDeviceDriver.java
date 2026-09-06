package com.ispf.driver.hartserial;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.hartserial.codec.HartSerialLabSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HART serial-gateway lab driver — TCP length-prefixed HART PDU subset (not FSK modem / HART FSK PHY).
 * <p>
 * Point mapping: {@code pv}, {@code cmd:1}, {@code device:0}, {@code device:0:cmd:1}.
 * Speaks to a TCP serial gateway lab on {@code host:port} (default 5094). This is a gateway lab
 * subset, not a full HCF stack and not an FSK modem. Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class HartSerialDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("hartSerialValue")
            .field("value", FieldType.DOUBLE)
            .field("command", FieldType.LONG)
            .field("device", FieldType.LONG)
            .field("unit", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "hart-serial",
            "HART Serial Gateway Lab Driver",
            "0.1.0",
            "HART serial TCP gateway lab: length-prefixed short-frame PV read (cmd 1/3);"
                    + " not FSK modem, not full HCF stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5094",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5094;
    private int timeoutMs = 3000;
    private HartSerialLabSession session;
    private final Map<String, HartSerialPoint> points = new ConcurrentHashMap<>();

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
            session = new HartSerialLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "HART serial-gateway lab connected to " + host + ":" + port);
        } catch (IOException e) {
            session = null;
            throw new DriverException("HART serial-gateway connect failed for " + host + ":" + port, e);
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
            HartSerialPoint point = HartSerialPoint.parse(mapping);
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
                throw new DriverException("HART serial-gateway read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException(
                "HART serial-gateway lab: write not supported (PV / universal command pass-through only)");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
