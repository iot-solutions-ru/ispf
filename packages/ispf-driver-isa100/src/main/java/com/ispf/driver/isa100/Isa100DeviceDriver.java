package com.ispf.driver.isa100;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.isa100.codec.Isa100LabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ISA100 wireless gateway ASCII/JSON lab driver over TCP (port 4840).
 * <p>
 * Point forms: {@code pv}, {@code tag:FI-101}, {@code device:1/pv}, {@code /devices/1/pv}.
 * {@code writePoint} calls {@code session.writeValue(...)}.
 * <p>
 * Honesty: gateway lab — not ISA100.11a RF / Wireless Compliance Institute stack.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class Isa100DeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("isa100Value")
            .field("value", FieldType.DOUBLE)
            .field("path", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "isa100",
            "ISA100 Gateway Lab Driver",
            "0.1.0",
            "ISA100 gateway ASCII/JSON lab: TCP GET/SET on 4840;"
                    + " not ISA100.11a RF / Wireless Compliance Institute stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "4840",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 4840;
    private int timeoutMs = 3000;
    private Isa100LabSession session;
    private final Map<String, Isa100Point> points = new ConcurrentHashMap<>();

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
            session = new Isa100LabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "ISA100 gateway lab connected to " + host + ":" + port
                            + " (not ISA100.11a RF / WCI stack)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("ISA100 lab connect failed for " + host + ":" + port, e);
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
            Isa100Point point = Isa100Point.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                float value = session.readValue(point.path());
                driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                        "value", (double) value,
                        "path", point.path()
                )));
            } catch (IOException e) {
                throw new DriverException("ISA100 lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        Isa100Point point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        float numeric = (float) extractNumeric(value);
        try {
            session.writeValue(point.path(), numeric);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", (double) numeric,
                    "path", point.path()
            )));
        } catch (IOException e) {
            throw new DriverException("ISA100 lab write failed for " + pointId, e);
        }
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("ISA100 write requires a value");
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
        throw new IllegalArgumentException("ISA100 write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
