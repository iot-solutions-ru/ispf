package com.ispf.driver.opcae;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.opcae.codec.OpcAeLabSession;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OPC A&amp;E HTTP/JSON alarm gateway lab driver — newline JSON over TCP (default port {@code 48080}).
 * <p>
 * Point forms: {@code alarm:1}, {@code source:Tank1}, {@code area:Plant}.
 * {@code readPoints} polls active alarms / source / area state; {@code writePoint} acknowledges
 * ({@code ack}) or enables via {@link OpcAeLabSession#acknowledge} / {@link OpcAeLabSession#writeValue}.
 * <p>
 * Honesty: HTTP/JSON A&amp;E gateway lab — not OPC Classic DCOM / COM A&amp;E.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only. Lab ≠ field.
 */
public class OpcAeDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("opcAeValue")
            .field("value", FieldType.DOUBLE)
            .field("text", FieldType.STRING)
            .field("kind", FieldType.STRING)
            .field("id", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "opc-ae",
            "OPC A&E HTTP/JSON Gateway Lab Driver",
            "0.1.0",
            "HTTP/JSON A&E gateway lab — not OPC Classic DCOM / COM A&E;"
                    + " newline JSON alarm poll / ack / enable over TCP",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "48080",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 48080;
    private int timeoutMs = 3000;
    private OpcAeLabSession session;
    private final Map<String, OpcAePoint> points = new ConcurrentHashMap<>();

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
            session = new OpcAeLabSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "OPC A&E HTTP/JSON gateway lab connected to " + host + ":" + port
                            + " (not OPC Classic DCOM / COM A&E)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("OPC A&E gateway lab connect failed for " + host + ":" + port, e);
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
            OpcAePoint point = OpcAePoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                OpcAeLabSession.AlarmSample sample = session.readValue(point.kindToken(), point.id());
                driverObject.updateVariable(entry.getKey(), toRecord(point, sample.value(), sample.text()));
            } catch (IOException e) {
                throw new DriverException("OPC A&E gateway lab read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        OpcAePoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        WriteIntent intent = resolveWriteIntent(value);
        try {
            if (intent.acknowledge()) {
                if (!point.acknowledgeable()) {
                    throw new DriverException(
                            "OPC A&E gateway lab acknowledge applies to alarm points only: "
                                    + point.display());
                }
                session.acknowledge(point.kindToken(), point.id());
                driverObject.updateVariable(pointId, toRecord(point, 0.0, "acknowledged"));
            } else {
                session.writeValue(point.kindToken(), point.id(), intent.enabled());
                driverObject.updateVariable(pointId, toRecord(point, intent.enabled(), "enabled"));
            }
        } catch (IOException e) {
            throw new DriverException("OPC A&E gateway lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(OpcAePoint point, double value, String text) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "text", text == null ? "" : text,
                "kind", point.kindToken(),
                "id", point.id(),
                "point", point.display()
        ));
    }

    private static WriteIntent resolveWriteIntent(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("OPC A&E write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        Object ackField = row.get("ack");
        if (ackField != null) {
            String token = String.valueOf(ackField).trim().toLowerCase(Locale.ROOT);
            if ("1".equals(token) || "true".equals(token) || "ack".equals(token)
                    || "acknowledge".equals(token)) {
                return WriteIntent.ack();
            }
        }
        for (String key : List.of("value", "command", "action", "raw")) {
            Object candidate = row.get(key);
            if (candidate == null) {
                continue;
            }
            String token = String.valueOf(candidate).trim().toLowerCase(Locale.ROOT);
            if ("ack".equals(token) || "acknowledge".equals(token)) {
                return WriteIntent.ack();
            }
            if ("enable".equals(token) || "enabled".equals(token)) {
                return WriteIntent.enable(1.0);
            }
            if ("disable".equals(token) || "disabled".equals(token)) {
                return WriteIntent.enable(0.0);
            }
            if (candidate instanceof Number number) {
                return WriteIntent.enable(number.doubleValue());
            }
            try {
                return WriteIntent.enable(Double.parseDouble(token));
            } catch (NumberFormatException ignored) {
                // try next key
            }
        }
        Object enabled = row.get("enabled");
        if (enabled instanceof Number number) {
            return WriteIntent.enable(number.doubleValue());
        }
        if (enabled != null) {
            return WriteIntent.enable(Double.parseDouble(String.valueOf(enabled).trim()));
        }
        throw new IllegalArgumentException(
                "OPC A&E write requires ack/acknowledge or numeric enabled/value");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }

    private record WriteIntent(boolean acknowledge, double enabled) {
        static WriteIntent ack() {
            return new WriteIntent(true, 0.0);
        }

        static WriteIntent enable(double enabled) {
            return new WriteIntent(false, enabled);
        }
    }
}
