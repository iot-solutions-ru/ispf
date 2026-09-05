package com.ispf.driver.iolink;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.iolink.codec.IoLinkLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IO-Link master driver — JSON-over-TCP lab bridge (default port {@code 8080}).
 * <p>
 * Honesty boundary: this talks to an ISPF IO-Link master REST/JSON-over-TCP lab bridge,
 * not the IO-Link PHY and not a vendor ISDU stack. Lab dialect is newline JSON
 * {@code {"op":"get|set","port":N,...}} for points {@code port:1}, {@code port:1:pdin},
 * {@code port:1:pdout}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class IoLinkDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("ioLinkValue")
            .field("value", FieldType.DOUBLE)
            .field("port", FieldType.LONG)
            .field("channel", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "io-link",
            "IO-Link Master Lab Driver",
            "0.1.0",
            "IO-Link master JSON-over-TCP lab bridge (get/set port process data);"
                    + " not IO-Link PHY / ISDU vendor stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "8080",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 8080;
    private int timeoutMs = 3000;
    private IoLinkLabSession session;
    private final Map<String, IoLinkPoint> points = new ConcurrentHashMap<>();

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
            session = new IoLinkLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "IO-Link master lab bridge connected to " + host + ":" + port
                            + " (not IO-Link PHY / ISDU)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("IO-Link lab connect failed for " + host + ":" + port, e);
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
            IoLinkPoint point = IoLinkPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                String channel = switch (point.channel()) {
                    case PORT -> "pdin";
                    case PDIN -> "pdin";
                    case PDOUT -> "pdout";
                };
                double value = session.readValue(point.port(), channel);
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("IO-Link lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        IoLinkPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        if (!point.writable()) {
            throw new DriverException("IO-Link lab rejects writes for pdin point: " + point.display());
        }
        double numeric = extractNumeric(value);
        try {
            session.writeValue(point.port(), "pdout", numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("IO-Link lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(IoLinkPoint point, double value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "port", (long) point.port(),
                "channel", point.channelLabel(),
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("IO-Link write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "pdout", "raw")) {
            Object candidate = row.get(key);
            if (candidate instanceof Number number) {
                return number.doubleValue();
            }
            if (candidate != null) {
                return Double.parseDouble(String.valueOf(candidate).trim());
            }
        }
        throw new IllegalArgumentException("IO-Link write requires numeric value/pdout/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
