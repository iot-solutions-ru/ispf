package com.ispf.driver.thread;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.thread.codec.ThreadLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread Border Router TCP gateway lab driver — newline JSON over TCP (default port {@code 8081}).
 * <p>
 * Point forms: {@code ip:fd00::1}, {@code udp:61631}, {@code child:1}.
 * IP and UDP points support write via {@link ThreadLabSession#writeValue}; child table is read-only.
 * <p>
 * Honesty: Thread BR TCP gateway lab — not 802.15.4 Thread radio / RCP silicon
 * (and not a live CoAP/5683 stack; lab dials TCP 8081).
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only. Lab ≠ RF.
 */
public class ThreadDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("threadValue")
            .field("value", FieldType.DOUBLE)
            .field("kind", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "thread",
            "Thread Border Router Gateway Lab Driver",
            "0.1.0",
            "Thread BR TCP gateway lab — not 802.15.4 Thread radio / RCP;"
                    + " newline JSON ip/udp/child get/set over TCP 8081 (not CoAP/5683 silicon)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "8081",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 8081;
    private int timeoutMs = 3000;
    private ThreadLabSession session;
    private final Map<String, ThreadPoint> points = new ConcurrentHashMap<>();

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
            session = new ThreadLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "Thread BR gateway lab connected to " + host + ":" + port
                            + " (not Thread radio / RCP)");
        } catch (IOException e) {
            session = null;
            throw new DriverException(
                    "Thread BR gateway lab connect failed for " + host + ":" + port, e);
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
            ThreadPoint point = ThreadPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                double value = session.readValue(point.wireToken());
                driverObject.updateVariable(entry.getKey(), toRecord(point, value));
            } catch (IOException e) {
                throw new DriverException("Thread BR gateway lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        ThreadPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        if (!point.writable()) {
            throw new DriverException(
                    "Thread BR gateway lab rejects writes for child point: " + point.display());
        }
        double numeric = extractNumeric(value);
        try {
            session.writeValue(point.wireToken(), numeric);
            driverObject.updateVariable(pointId, toRecord(point, numeric));
        } catch (IOException e) {
            throw new DriverException("Thread BR gateway lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(ThreadPoint point, double value) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "kind", point.kindToken(),
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("Thread write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "raw", "udp")) {
            Object candidate = row.get(key);
            if (candidate instanceof Number number) {
                return number.doubleValue();
            }
            if (candidate != null) {
                return Double.parseDouble(String.valueOf(candidate).trim());
            }
        }
        throw new IllegalArgumentException("Thread write requires numeric value/raw/udp");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
